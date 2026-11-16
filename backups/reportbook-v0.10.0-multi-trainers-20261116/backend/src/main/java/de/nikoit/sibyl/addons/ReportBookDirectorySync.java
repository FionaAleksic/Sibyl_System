package de.nikoit.sibyl.addons;

import de.nikoit.sibyl.auth.AdConnectionService;
import de.nikoit.sibyl.auth.AdDnsDiscovery;
import de.nikoit.sibyl.auth.ServiceCredentialVault;
import java.util.*;
import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only LDAPS roster discovery through configured, encrypted AD service accounts. */
@Service
public class ReportBookDirectorySync {
    public record Result(int trainees,int trainers,int directories,String message) {}
    private record Flags(boolean trainee,boolean trainer) {
        Flags withTrainee(){return new Flags(true,trainer);}
        Flags withTrainer(){return new Flags(trainee,true);}
    }
    private final JdbcTemplate db;
    private final AddonConfigurationService settings;
    private final AdConnectionService connections;
    private final AdDnsDiscovery dns;
    private final ServiceCredentialVault vault;
    private final AuditService audit;

    public ReportBookDirectorySync(JdbcTemplate db,AddonConfigurationService settings,
            AdConnectionService connections,AdDnsDiscovery dns,ServiceCredentialVault vault,AuditService audit) {
        this.db=db;this.settings=settings;this.connections=connections;
        this.dns=dns;this.vault=vault;this.audit=audit;
    }

    @Transactional
    public Result synchronize(String actor) {
        Map<String,String> values=settings.read("Sibyl.report-book").values();
        List<String> traineeGroups=groups(values.get("traineeGroupDns"));
        List<String> trainerGroups=groups(values.get("trainerGroupDns"));
        if(traineeGroups.isEmpty()||trainerGroups.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Configure trainee and trainer AD groups in addon settings first");
        List<AdConnectionService.Connection> configured=connections.all().stream()
            .filter(c->c.enabled()&&c.hasSecret()&&c.servicePrincipal()!=null
                &&!c.servicePrincipal().isBlank()).toList();
        if(configured.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No active directory profile with a read-only service account");

        Map<String,Flags> discovered=new LinkedHashMap<>();
        Map<String,Set<String>> discoveredGroups=new LinkedHashMap<>();
        int scanned=0;
        for(AdConnectionService.Connection connection:configured) {
            try {
                scanDirectory(connection,traineeGroups,trainerGroups,discovered,discoveredGroups);
                scanned++;
            } catch(Exception e) {
                // Never expose LDAP credentials, domain user records, or internal stack traces.
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Directory synchronization failed; no roster changes were applied");
            }
        }
        if(discovered.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "No users found in configured groups; roster unchanged");
        // Apply the complete snapshot only after all directory lookups succeeded.
        applySnapshot(discovered,discoveredGroups,actor);
        int trainees=(int)discovered.values().stream().filter(Flags::trainee).count();
        int trainers=(int)discovered.values().stream().filter(Flags::trainer).count();
        return new Result(trainees,trainers,scanned,
            "AD accounts synchronized; existing manual assignments preserved");
    }

    private List<String> groups(String value) {
        if(value==null||value.isBlank())return List.of();
        return Arrays.stream(value.split("\\R")).map(String::trim)
            .filter(s->!s.isBlank()).distinct().toList();
    }
    private void scanDirectory(AdConnectionService.Connection config,List<String> traineeGroups,
            List<String> trainerGroups,Map<String,Flags> discovered,
            Map<String,Set<String>> discoveredGroups) throws Exception {
        List<String> servers=connections.hosts(config);
        if(servers.isEmpty()) throw new NamingException("No LDAPS server available");
        String ciphertext=db.queryForObject(
            "SELECT service_secret_cipher FROM sibyl_ad_connections WHERE id=?",
            String.class,config.id());
        if(ciphertext==null)throw new NamingException("Service account not configured");
        Hashtable<String,Object> env=new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL,"ldaps://"+servers.get(0)+":636");
        env.put(Context.SECURITY_AUTHENTICATION,"simple");
        env.put(Context.SECURITY_PRINCIPAL,config.servicePrincipal());
        env.put(Context.SECURITY_CREDENTIALS,vault.decrypt(ciphertext));
        env.put(Context.REFERRAL,"ignore");
        env.put("com.sun.jndi.ldap.connect.timeout","4500");
        env.put("com.sun.jndi.ldap.read.timeout","6000");
        try(LdapConnection session=new LdapConnection(new InitialLdapContext(env,null))) {
            InitialLdapContext ctx=session.context;
            for(String entry:traineeGroups) {
                String group=resolveGroup(ctx,entry,config.domain());
                if(group==null)continue;
                String groupName=groupLabel(entry);
                for(String name:members(ctx,group,config.userBaseDn())) {
                    String user=canonical(name,config.domain());
                    discovered.merge(user,new Flags(true,false),
                        (a,b)->new Flags(a.trainee()||b.trainee(),a.trainer()||b.trainer()));
                    discoveredGroups.computeIfAbsent(user,k->new LinkedHashSet<>()).add(groupName);
                }
            }
            for(String entry:trainerGroups) {
                String group=resolveGroup(ctx,entry,config.domain());
                if(group==null)continue;
                for(String name:members(ctx,group,config.userBaseDn())) {
                    String user=canonical(name,config.domain());
                    discovered.merge(user,new Flags(false,true),
                        (a,b)->new Flags(a.trainee()||b.trainee(),a.trainer()||b.trainer()));
                }
            }
        } finally {
            env.remove(Context.SECURITY_CREDENTIALS);
        }
    }
    private static String groupLabel(String entry){
        String name=entry.trim();
        if(name.regionMatches(true,0,"CN=",0,3)){
            int comma=name.indexOf(',');
            name=comma<0?name.substring(3):name.substring(3,comma);
        }
        if(name.isBlank()||name.length()>100
           ||!name.matches("[\\p{L}0-9 ._-]{1,100}"))
            throw new IllegalArgumentException("Invalid AD group label");
        return name;
    }
    private String canonical(String username,String realm) {
        String normalized=username.toLowerCase(Locale.ROOT);
        return normalized+(realm.equalsIgnoreCase(dns.domain())?"":"@"+realm.toLowerCase(Locale.ROOT));
    }
    private static String escape(String value) {
        return value.replace("\\","\\5c").replace("*","\\2a")
            .replace("(","\\28").replace(")","\\29").replace("\u0000","\\00");
    }
    private String resolveGroup(InitialLdapContext ctx,String value,String domain) throws NamingException {
        String base="dc="+domain.replace(".",",dc=");
        String group=value.trim();
        if(group.matches("(?i)cn=.{1,250},.*")) {
            if(!group.toLowerCase(Locale.ROOT).endsWith(base.toLowerCase(Locale.ROOT)))
                return null;
            return group;
        }
        if(!group.matches("[\\p{L}0-9 ._-]{1,100}"))return null;
        SearchControls sc=new SearchControls();sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        sc.setTimeLimit(5000);sc.setCountLimit(2);
        sc.setReturningAttributes(new String[]{"distinguishedName"});
        try(NamingResults results=new NamingResults(ctx.search(base,
            "(&(objectCategory=group)(cn="+escape(group)+"))",sc))) {
            if(!results.enumeration.hasMore())return null;
            SearchResult first=results.enumeration.next();
            if(results.enumeration.hasMore())throw new NamingException("Ambiguous group");
            return first.getNameInNamespace();
        }
    }
    private List<String> members(InitialLdapContext ctx,String groupDn,String base) throws Exception {
        SearchControls sc=new SearchControls();sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        sc.setTimeLimit(10000);sc.setReturningAttributes(new String[]{"sAMAccountName"});
        String filter="(&(objectCategory=person)(objectClass=user)"
            +"(memberOf:1.2.840.113556.1.4.1941:="+escape(groupDn)+"))";
        List<String> found=new ArrayList<>();
        byte[] cookie=null;
        do {
            ctx.setRequestControls(new javax.naming.ldap.Control[]{new PagedResultsControl(250,Control.CRITICAL)});
            try(NamingResults results=new NamingResults(ctx.search(base,filter,sc))) {
                while(results.enumeration.hasMore()) {
                    SearchResult item=results.enumeration.next();
                    Attribute attr=item.getAttributes().get("sAMAccountName");
                    if(attr==null)continue;
                    String name=String.valueOf(attr.get()).trim().toLowerCase(Locale.ROOT);
                    if(name.matches("[a-z0-9][a-z0-9._-]{0,63}"))found.add(name);
                    if(found.size()>20000)throw new NamingException("Too many directory accounts");
                }
            }
            cookie=null;
            javax.naming.ldap.Control[] controls=ctx.getResponseControls();
            if(controls!=null)for(javax.naming.ldap.Control control:controls)
                if(control instanceof PagedResultsResponseControl page)cookie=page.getCookie();
        } while(cookie!=null&&cookie.length>0&&found.size()<20000);
        return found;
    }
    private static final class NamingResults implements AutoCloseable {
        final NamingEnumeration<SearchResult> enumeration;
        NamingResults(NamingEnumeration<SearchResult> results){enumeration=results;}
        @Override public void close() throws NamingException{enumeration.close();}
    }
    private static final class LdapConnection implements AutoCloseable {
        final InitialLdapContext context;
        LdapConnection(InitialLdapContext context){this.context=context;}
        @Override public void close() throws NamingException{context.close();}
    }
    @Transactional
    protected void applySnapshot(Map<String,Flags> discovered,
            Map<String,Set<String>> discoveredGroups,String actor) {
        db.update("UPDATE sibyl_report_profiles SET trainee_enabled=FALSE,trainer_enabled=FALSE,"
            +"active=FALSE WHERE source='AD'");
        discovered.forEach((name,flags)->db.update(
            "INSERT INTO sibyl_report_profiles(username,trainee_enabled,trainer_enabled,active,source,last_synced_at) "
            +"VALUES(?,?,?,TRUE,'AD',CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE "
            +"trainee_enabled=IF(source='AD',VALUES(trainee_enabled),trainee_enabled),"
            +"trainer_enabled=IF(source='AD',VALUES(trainer_enabled),trainer_enabled),"
            +"active=IF(source='AD',TRUE,active),last_synced_at=CURRENT_TIMESTAMP",
            name,flags.trainee(),flags.trainer()));
        db.update("DELETE FROM sibyl_report_profile_groups WHERE source='AD'");
        discoveredGroups.forEach((username,groups)->groups.forEach(group->
            db.update("INSERT INTO sibyl_report_profile_groups(username,group_name,source) "
                +"VALUES(?,?,'AD') ON DUPLICATE KEY UPDATE source=source",username,group)));
        audit.record(actor,"REPORT_BOOK_DIRECTORY_SYNC",Integer.toString(discovered.size()));
    }
}
