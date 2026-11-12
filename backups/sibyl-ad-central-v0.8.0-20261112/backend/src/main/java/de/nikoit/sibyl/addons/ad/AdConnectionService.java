package de.nikoit.sibyl.addons.ad;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.naming.Context;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.util.Hashtable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import de.nikoit.sibyl.addons.AuditService;

/** Multiple directory profiles; all responses deliberately omit encrypted credentials. */
@Service
public class AdConnectionService {
    private static final Pattern DNS=Pattern.compile("(?i)[a-z0-9-]+(?:\\.[a-z0-9-]+)+");
    public record Connection(long id,String label,String domain,String userBaseDn,String discoveryMode,
        String serverHostname,String servicePrincipal,boolean hasSecret,
        String adminGroupDns,String userGroupDns,boolean enabled,
        boolean loginEnabled,boolean syncEnabled,String netbiosName) {}
    public record Input(String label,String domain,String userBaseDn,String discoveryMode,
        String serverHostname,String servicePrincipal,String servicePassword,
        String adminGroupDns,String userGroupDns,boolean enabled,
        Boolean loginEnabled,Boolean syncEnabled,String netbiosName) {}
    public record TestResult(boolean tlsVerified,boolean serviceBindVerified,String message,String endpoint) {}

    private final JdbcTemplate db;
    private final ServiceCredentialVault vault;
    private final AuditService audit;
    private final AdDnsDiscovery dns;
    public AdConnectionService(JdbcTemplate db,ServiceCredentialVault vault,AuditService audit,
                               AdDnsDiscovery dns) {
        this.db=db;this.vault=vault;this.audit=audit;this.dns=dns;
    }
    public List<Connection> all() {
        return db.query("SELECT id,label,domain,user_base_dn,discovery_mode,server_hostname,"
          +"service_principal,service_secret_cipher,admin_group_dns,user_group_dns,enabled,"
          +"login_enabled,sync_enabled,netbios_name FROM sibyl_ad_connections ORDER BY domain",
          (r,i)->new Connection(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),
            r.getString(5),r.getString(6),r.getString(7),r.getString(8)!=null,
            r.getString(9),r.getString(10),r.getBoolean(11),
            r.getBoolean(12),r.getBoolean(13),r.getString(14)));
    }
    public Connection get(long id) {
        return all().stream().filter(c->c.id()==id).findFirst()
           .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Directory missing"));
    }
    private void validate(Input x){
        if(x==null || x.label()==null || x.label().isBlank() || x.label().length()>100
            || x.domain()==null || !DNS.matcher(x.domain()).matches()
            || x.userBaseDn()==null || x.userBaseDn().isBlank() || x.userBaseDn().length()>500
            || !x.userBaseDn().toLowerCase(Locale.ROOT).endsWith(domainDn(x.domain()))
            || !List.of("dns","static").contains(x.discoveryMode())
            || ("static".equals(x.discoveryMode()) && (x.serverHostname()==null
                || !DNS.matcher(x.serverHostname()).matches()
                || !x.serverHostname().toLowerCase(Locale.ROOT).endsWith("."+x.domain().toLowerCase(Locale.ROOT))))
            || (x.servicePrincipal()!=null && x.servicePrincipal().length()>253)
            || (x.servicePassword()!=null && x.servicePassword().length()>512)
            || x.adminGroupDns()==null || x.adminGroupDns().length()>4000
            || x.userGroupDns()==null || x.userGroupDns().length()>4000
            || (x.netbiosName()!=null && !x.netbiosName().isBlank()
                && !x.netbiosName().matches("(?i)[a-z][a-z0-9-]{0,14}")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid directory configuration");
        // The configured groups must belong to this directory and have valid DNs.
        for(String raw:List.of(x.adminGroupDns(),x.userGroupDns())){
            for(String line:raw.split("\\R")){
                if(line.isBlank())continue;
                String entry=line.trim().toLowerCase(Locale.ROOT);
                if(!entry.startsWith("cn=")||!entry.endsWith(domainDn(x.domain())))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Group must be a DN in the selected domain");
            }
        }
    }
    private static String domainDn(String domain){
        return "dc="+domain.toLowerCase(Locale.ROOT).replace(".",",dc=");
    }
    @Transactional
    public Connection create(Input x,Principal actor){
        validate(x);
        String secret=x.servicePassword()==null||x.servicePassword().isBlank()?null:vault.encrypt(x.servicePassword());
        db.update("INSERT INTO sibyl_ad_connections(label,domain,user_base_dn,discovery_mode,"
            +"server_hostname,service_principal,service_secret_cipher,admin_group_dns,user_group_dns,enabled,"
            +"login_enabled,sync_enabled,netbios_name) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            x.label().trim(),x.domain().toLowerCase(Locale.ROOT),x.userBaseDn().trim(),
            x.discoveryMode(),blank(x.serverHostname()),blank(x.servicePrincipal()),secret,
            x.adminGroupDns().trim(),x.userGroupDns().trim(),x.enabled(),
            x.loginEnabled()==null||x.loginEnabled(),x.syncEnabled()==null||x.syncEnabled(),
            blank(x.netbiosName()));
        Long id=db.queryForObject("SELECT id FROM sibyl_ad_connections WHERE domain=?",Long.class,
            x.domain().toLowerCase(Locale.ROOT));
        audit.record(actor.getName(),"AD_CONNECTION_CREATE","directory-"+id);
        return get(id);
    }
    @Transactional
    public Connection update(long id,Input x,Principal actor){
        get(id);validate(x);
        String secret=x.servicePassword()==null||x.servicePassword().isBlank()?null:vault.encrypt(x.servicePassword());
        db.update("UPDATE sibyl_ad_connections SET label=?,domain=?,user_base_dn=?,discovery_mode=?,"
            +"server_hostname=?,service_principal=?,service_secret_cipher=COALESCE(?,service_secret_cipher),"
            +"admin_group_dns=?,user_group_dns=?,enabled=?,"
            +"login_enabled=?,sync_enabled=?,netbios_name=? WHERE id=?",
            x.label().trim(),x.domain().toLowerCase(Locale.ROOT),x.userBaseDn().trim(),
            x.discoveryMode(),blank(x.serverHostname()),blank(x.servicePrincipal()),secret,
            x.adminGroupDns().trim(),x.userGroupDns().trim(),x.enabled(),
            x.loginEnabled()==null||x.loginEnabled(),x.syncEnabled()==null||x.syncEnabled(),
            blank(x.netbiosName()),id);
        audit.record(actor.getName(),"AD_CONNECTION_UPDATE","directory-"+id);
        return get(id);
    }
    @Transactional
    public void delete(long id,Principal actor) {
        get(id);
        db.update("DELETE FROM sibyl_ad_connections WHERE id=?",id);
        audit.record(actor.getName(),"AD_CONNECTION_DELETE","directory-"+id);
    }
    public List<String> hosts(Connection cfg) {
        if ("static".equals(cfg.discoveryMode()))return List.of(cfg.serverHostname());
        return dns.discoverDomain(cfg.domain()).stream().map(AdDnsDiscovery.Controller::hostname).toList();
    }
    public TestResult test(long id,Principal actor) {
        Connection cfg=get(id);
        List<String> names=hosts(cfg);
        if(names.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"No directory servers found");
        String hostname=names.get(0);
        try {
            try(SSLSocket socket=(SSLSocket)SSLSocketFactory.getDefault().createSocket(hostname,636)){
                socket.setSoTimeout(4000);
                var params=socket.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                socket.setSSLParameters(params);
                socket.startHandshake();
            }
            if(cfg.servicePrincipal()==null||cfg.servicePrincipal().isBlank()||!cfg.hasSecret())
                return new TestResult(true,false,"LDAPS-Zertifikat gültig; kein Servicekonto hinterlegt",hostname);
            String cipher=db.queryForObject("SELECT service_secret_cipher FROM sibyl_ad_connections WHERE id=?",
                String.class,id);
            Hashtable<String,Object> env=new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL,"ldaps://"+hostname+":636");
            env.put(Context.SECURITY_AUTHENTICATION,"simple");
            env.put(Context.SECURITY_PRINCIPAL,cfg.servicePrincipal());
            env.put(Context.SECURITY_CREDENTIALS,vault.decrypt(cipher));
            env.put("com.sun.jndi.ldap.connect.timeout","4000");
            env.put("com.sun.jndi.ldap.read.timeout","4000");
            try {
                InitialLdapContext context=new InitialLdapContext(env,null);
                context.close();
            } finally {env.remove(Context.SECURITY_CREDENTIALS);}
            audit.record(actor.getName(),"AD_CONNECTION_TEST","directory-"+id);
            return new TestResult(true,true,"LDAPS und Servicekonto erfolgreich geprüft",hostname);
        } catch(Exception e){
            // No password, DN or potentially identifying LDAP diagnostics are returned or logged.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"LDAPS/Servicekonto-Verbindung fehlgeschlagen");
        }
    }
    private static String blank(String value){return value==null||value.isBlank()?null:value.trim();}
}
