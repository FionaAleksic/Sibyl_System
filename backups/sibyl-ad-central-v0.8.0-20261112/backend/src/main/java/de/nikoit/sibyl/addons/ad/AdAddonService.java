package de.nikoit.sibyl.addons.ad;

import de.nikoit.sibyl.addons.AddonConfigurationService;
import java.util.*;
import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The single AD integration boundary for login, group reads, DNS discovery,
 * service credentials and multi-directory routing. No other addon binds LDAP.
 */
@Service
public class AdAddonService implements SibylAdApi {
    private static final Logger LOG=LoggerFactory.getLogger(AdAddonService.class);
    private static final String MATCHING_RULE_CHAIN="1.2.840.113556.1.4.1941";
    private static final String ACCOUNT="[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}";
    private final JdbcTemplate db;
    private final AdDnsDiscovery dns;
    private final AdConnectionService connections;
    private final ServiceCredentialVault vault;
    private final AddonConfigurationService settings;

    public AdAddonService(JdbcTemplate db,AdDnsDiscovery dns,AdConnectionService connections,
            ServiceCredentialVault vault,AddonConfigurationService settings) {
        this.db=db;this.dns=dns;this.connections=connections;this.vault=vault;this.settings=settings;
    }
    private boolean enabled() {
        return Boolean.TRUE.equals(db.query(
            "SELECT enabled FROM sibyl_addons WHERE addon_id='Sibyl.ad'",
            r->r.next()&&r.getBoolean(1)));
    }
    @Override public Status status() {
        boolean active=enabled();
        List<AdConnectionService.Connection> all=connections.all();
        return new Status(active,
            (int)all.stream().filter(AdConnectionService.Connection::enabled).count(),
            (int)all.stream().filter(c->c.enabled()&&c.loginEnabled()).count(),
            (int)all.stream().filter(c->c.enabled()&&c.syncEnabled()&&c.hasSecret()
                 &&c.servicePrincipal()!=null&&!c.servicePrincipal().isBlank()).count());
    }
    private String preferredDomain(){
        String configured=settings.read("Sibyl.ad").values().getOrDefault("defaultDomain","").trim();
        if(!configured.isEmpty())return configured.toLowerCase(Locale.ROOT);
        return dns.domain();
    }
    @Override public String defaultDomain() {
        // Prefer the explicit default domain when configured; otherwise only a
        // unique active realm may be used for unqualified accounts.
        List<AdConnectionService.Connection> active=connections.all().stream()
            .filter(AdConnectionService.Connection::enabled).toList();
        for(AdConnectionService.Connection c:active)if(c.domain().equalsIgnoreCase(preferredDomain()))
            return c.domain();
        return active.size()==1?active.get(0).domain():preferredDomain();
    }
    @Override public List<Controller> controllers() {
        if(!enabled())return List.of();
        List<Controller> result=new ArrayList<>();
        for(AdConnectionService.Connection c:connections.all()) {
            if(!c.enabled())continue;
            if("static".equals(c.discoveryMode())) {
                String host=c.serverHostname();
                if(host==null)continue;
                try {
                    for(java.net.InetAddress address:java.net.InetAddress.getAllByName(host))
                        if(address.getAddress().length==4)
                            result.add(new Controller(host,address.getHostAddress(),c.domain()));
                } catch(java.net.UnknownHostException e) {
                    LOG.warn("Configured AD controller hostname cannot be resolved [{}]",c.domain());
                }
            } else {
                for(AdDnsDiscovery.Controller dc:dns.discoverDomain(c.domain()))
                    result.add(new Controller(dc.hostname(),dc.ipv4(),c.domain()));
            }
        }
        return List.copyOf(result);
    }

    private record Username(String account,AdConnectionService.Connection directory) {}
    private Username resolve(String name) {
        if(name==null||name.isBlank())throw new BadCredentialsException("Invalid credentials");
        String input=name.trim();
        String account;
        List<AdConnectionService.Connection> eligible=connections.all().stream()
            .filter(c->c.enabled()&&c.loginEnabled()).toList();
        AdConnectionService.Connection found=null;
        if(input.contains("\\")) {
            String[] pair=input.split("\\\\",-1);
            if(pair.length!=2||!pair[0].matches("[a-zA-Z][a-zA-Z0-9-]{0,14}"))
                throw new BadCredentialsException("Invalid domain account");
            account=pair[1];
            for(AdConnectionService.Connection c:eligible) {
                String netbios=(c.netbiosName()==null||c.netbiosName().isBlank())
                    ?c.domain().split("\\.")[0]:c.netbiosName();
                if(!netbios.equalsIgnoreCase(pair[0]))continue;
                if(found!=null)throw new BadCredentialsException("Ambiguous domain account");
                found=c;
            }
        } else if(input.contains("@")) {
            int pos=input.lastIndexOf("@");
            account=input.substring(0,pos);
            String realm=input.substring(pos+1);
            if(!realm.matches("(?i)[a-z0-9-]+(?:\\.[a-z0-9-]+)+"))
                throw new BadCredentialsException("Invalid realm");
            for(AdConnectionService.Connection c:eligible) {
                if(c.domain().equalsIgnoreCase(realm)){found=c;break;}
            }
        } else {
            account=input;
            String preferred=preferredDomain();
            for(AdConnectionService.Connection c:eligible)
                if(c.domain().equalsIgnoreCase(preferred)){found=c;break;}
            if(found==null&&eligible.size()==1)found=eligible.get(0);
        }
        if(!account.matches(ACCOUNT)||found==null)
            throw new BadCredentialsException("No active AD login profile for account");
        return new Username(account.toLowerCase(Locale.ROOT),found);
    }
    @Override public Login authenticate(String name,String password) {
        if(!enabled())throw new BadCredentialsException("AD addon is disabled");
        if(password==null||password.isBlank())throw new BadCredentialsException("Invalid credentials");
        Username resolved=resolve(name);
        AdConnectionService.Connection profile=resolved.directory();
        List<String> hosts;
        try{hosts=connections.hosts(profile);}
        catch(IllegalStateException e){
            LOG.warn("No valid domain controller for [{}]",profile.domain());
            throw new AuthenticationServiceException("AD DNS discovery unavailable",e);
        }
        NamingException last=null;
        for(String host:hosts){
            Hashtable<String,Object> env=environment(host,resolved.account()+"@"+profile.domain(),password);
            InitialLdapContext context=null;
            try{
                context=new InitialLdapContext(env,null);
                String role=AdGroupRoles.roleForUser(context,resolved.account(),profile.userBaseDn(),
                    profile.adminGroupDns(),profile.userGroupDns());
                if(role==null)throw new BadCredentialsException("AD user is not assigned to a permitted group");
                String identity=profile.domain().equalsIgnoreCase(defaultDomain())
                    ?resolved.account():resolved.account()+"@"+profile.domain();
                return new Login(identity,role);
            }catch(javax.naming.AuthenticationException e){
                throw new BadCredentialsException("Invalid credentials");
            }catch(NamingException e){
                last=e;
                LOG.warn("AD controller unavailable [{}]",profile.domain());
            }finally{
                if(context!=null)try{context.close();}catch(NamingException ignored){}
                env.remove(Context.SECURITY_CREDENTIALS);
            }
        }
        throw new AuthenticationServiceException("All configured AD controllers unavailable",last);
    }
    private static Hashtable<String,Object> environment(String host,String principal,String password){
        Hashtable<String,Object> env=new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL,"ldaps://"+host+":636");
        env.put(Context.SECURITY_AUTHENTICATION,"simple");
        env.put(Context.SECURITY_PRINCIPAL,principal);
        env.put(Context.SECURITY_CREDENTIALS,password);
        env.put(Context.REFERRAL,"ignore");
        env.put("com.sun.jndi.ldap.connect.timeout","4000");
        env.put("com.sun.jndi.ldap.read.timeout","6000");
        return env;
    }

    @Override public GroupRoster groupRoster(Map<String,String> roleToAdAddonSetting) {
        if(!enabled())throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Active Directory addon is disabled");
        if(roleToAdAddonSetting==null||roleToAdAddonSetting.isEmpty()
          ||roleToAdAddonSetting.size()>16)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid directory group mappings");
        Map<String,String> configuration=settings.read("Sibyl.ad").values();
        Map<String,List<String>> roleGroups=new LinkedHashMap<>();
        for(var entry:roleToAdAddonSetting.entrySet()) {
            if(entry.getKey()==null||!entry.getKey().matches("[a-zA-Z][a-zA-Z0-9_-]{0,39}")
             ||entry.getValue()==null||!configuration.containsKey(entry.getValue()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown directory group mapping");
            String value=configuration.get(entry.getValue());
            List<String> groups=value==null?List.of():Arrays.stream(value.split("\\R"))
                .map(String::trim).filter(v->!v.isEmpty()).distinct().toList();
            if(groups.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Configure all directory group mappings in the AD addon");
            roleGroups.put(entry.getKey(),groups);
        }
        List<AdConnectionService.Connection> eligible=connections.all().stream()
            .filter(c->c.enabled()&&c.syncEnabled()&&c.hasSecret()
                &&c.servicePrincipal()!=null&&!c.servicePrincipal().isBlank()).toList();
        if(eligible.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,
            "A configured read-only AD service account is required for group synchronization");

        Map<String,Set<String>> groups=new LinkedHashMap<>();
        roleGroups.keySet().forEach(role->groups.put(role,new LinkedHashSet<>()));
        for(AdConnectionService.Connection c:eligible) {
            try{
                scanDirectory(c,roleGroups,groups);
            }catch(Exception e){
                // Credentials and directory names must not escape this boundary.
                LOG.warn("AD read-only directory scan failed [{}]",c.domain());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Configured AD service account cannot read the requested groups");
            }
        }
        Map<String,Set<String>> frozen=new LinkedHashMap<>();
        groups.forEach((k,v)->frozen.put(k,Set.copyOf(v)));
        return new GroupRoster(Map.copyOf(frozen),eligible.size());
    }
    private void scanDirectory(AdConnectionService.Connection cfg,
            Map<String,List<String>> roles,Map<String,Set<String>> output) throws Exception {
        List<String> hosts=connections.hosts(cfg);
        if(hosts.isEmpty())throw new NamingException("No directory host");
        String cipher=db.queryForObject("SELECT service_secret_cipher FROM sibyl_ad_connections WHERE id=?",
            String.class,cfg.id());
        if(cipher==null)throw new NamingException("Service credential unavailable");
        Hashtable<String,Object> env=environment(hosts.get(0),cfg.servicePrincipal(),vault.decrypt(cipher));
        InitialLdapContext context=null;
        try{
            context=new InitialLdapContext(env,null);
            for(var role:roles.entrySet()){
                for(String group:role.getValue()){
                    String groupDn=resolveGroup(context,group,cfg.domain());
                    if(groupDn==null)continue;
                    for(String username:members(context,groupDn,cfg.userBaseDn())){
                        output.get(role.getKey()).add(canonical(username,cfg.domain()));
                    }
                }
            }
        }finally{
            if(context!=null)try{context.close();}catch(NamingException ignored){}
            env.remove(Context.SECURITY_CREDENTIALS);
        }
    }
    private String canonical(String username,String domain){
        String name=username.toLowerCase(Locale.ROOT);
        return domain.equalsIgnoreCase(defaultDomain())?name:name+"@"+domain.toLowerCase(Locale.ROOT);
    }
    private static String escape(String input){
        return input.replace("\\","\\5c").replace("*","\\2a")
             .replace("(","\\28").replace(")","\\29").replace("\u0000","\\00");
    }
    private String resolveGroup(InitialLdapContext context,String value,String domain) throws NamingException {
        String base="dc="+domain.toLowerCase(Locale.ROOT).replace(".",",dc=");
        String entry=value.trim();
        if(entry.matches("(?i)cn=.{1,400},.*")) {
            if(!entry.toLowerCase(Locale.ROOT).endsWith(base))return null;
            return entry;
        }
        if(!entry.matches("[\\p{L}0-9 ._-]{1,100}"))return null;
        SearchControls controls=new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setTimeLimit(6000);
        controls.setCountLimit(2);
        controls.setReturningAttributes(new String[]{"distinguishedName"});
        try(NamingResults result=new NamingResults(context.search(base,
            "(&(objectCategory=group)(cn="+escape(entry)+"))",controls))){
            if(!result.rows.hasMore())return null;
            String first=result.rows.next().getNameInNamespace();
            if(result.rows.hasMore())throw new NamingException("Ambiguous directory group");
            return first;
        }
    }
    private List<String> members(InitialLdapContext ctx,String dn,String base) throws Exception {
        String filter="(&(objectCategory=person)(objectClass=user)"
            +"(memberOf:"+MATCHING_RULE_CHAIN+":="+escape(dn)+"))";
        SearchControls c=new SearchControls();
        c.setSearchScope(SearchControls.SUBTREE_SCOPE);
        c.setTimeLimit(10000);
        c.setReturningAttributes(new String[]{"sAMAccountName"});
        List<String> result=new ArrayList<>();
        byte[] cookie=null;
        do {
            ctx.setRequestControls(new javax.naming.ldap.Control[]{
                new PagedResultsControl(250,javax.naming.ldap.Control.CRITICAL)});
            try(NamingResults page=new NamingResults(ctx.search(base,filter,c))){
                while(page.rows.hasMore()){
                    SearchResult entry=page.rows.next();
                    Attribute name=entry.getAttributes().get("sAMAccountName");
                    if(name==null)continue;
                    String value=String.valueOf(name.get()).trim().toLowerCase(Locale.ROOT);
                    if(value.matches("[a-z0-9][a-z0-9._-]{0,63}"))result.add(value);
                    if(result.size()>20000)throw new NamingException("Directory response limit exceeded");
                }
            }
            cookie=null;
            var responses=ctx.getResponseControls();
            if(responses!=null)for(var response:responses)
                if(response instanceof PagedResultsResponseControl page)
                    cookie=page.getCookie();
        }while(cookie!=null&&cookie.length>0);
        return result;
    }
    private static final class NamingResults implements AutoCloseable {
        final NamingEnumeration<SearchResult> rows;
        NamingResults(NamingEnumeration<SearchResult> rows){this.rows=rows;}
        @Override public void close() throws NamingException{rows.close();}
    }
}
