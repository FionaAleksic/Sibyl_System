package de.nikoit.sibyl.addons.ad;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Discover AD domain controllers through DNS SRV. Only FQDNs within the configured
 * or host-discovered domain are accepted, and authentication always uses LDAPS:636.
 */
@Service
public class AdDnsDiscovery {
    public record Controller(String hostname, String ipv4, int priority, int weight) {}
    private final String domain;

    public AdDnsDiscovery(@Value("${sibyl.ad.domain:}") String configuredDomain) {
        String name = configuredDomain == null ? "" : configuredDomain.trim();
        if (name.isEmpty()) {
            try {
                String fqdn = InetAddress.getLocalHost().getCanonicalHostName();
                int dot = fqdn.indexOf('.');
                if (dot > 0) name = fqdn.substring(dot + 1);
            } catch (Exception ignored) { }
        }
        if (!name.matches("(?i)[a-z0-9-]+(?:\\.[a-z0-9-]+)+")) {
            name=""; // A fresh install may configure the default realm in Sibyl.ad.
        }
        domain = name.toLowerCase(Locale.ROOT);
    }

    public String domain() { return domain; }

    public List<Controller> discover() { return discoverDomain(domain); }
    public List<Controller> discoverDomain(String selectedDomain) {
        if(selectedDomain==null || !selectedDomain.matches("(?i)[a-z0-9-]+(?:\\.[a-z0-9-]+)+"))
            throw new IllegalArgumentException("Invalid AD domain");
        String domain=selectedDomain.toLowerCase(Locale.ROOT);
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2500");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        Map<String,Controller> unique = new LinkedHashMap<>();
        DirContext dns = null;
        try {
            dns = new InitialDirContext(env);
            Attributes answer = dns.getAttributes("_ldap._tcp.dc._msdcs." + domain, new String[]{"SRV"});
            Attribute records = answer.get("SRV");
            if (records != null) {
                NamingEnumeration<?> values = records.getAll();
                try {
                    while (values.hasMore()) {
                        String[] fields = String.valueOf(values.next()).trim().split("\\s+");
                        if (fields.length != 4) continue;
                        int priority = Integer.parseInt(fields[0]);
                        int weight = Integer.parseInt(fields[1]);
                        if (Integer.parseInt(fields[2]) != 389) continue;
                        String host = fields[3].replaceFirst("\\.$", "").toLowerCase(Locale.ROOT);
                        if (!host.endsWith("." + domain) || host.length() > 253) continue;
                        for (InetAddress address : InetAddress.getAllByName(host)) {
                            if (address.getAddress().length == 4) {
                                unique.putIfAbsent(host, new Controller(host,address.getHostAddress(),priority,weight));
                            }
                        }
                    }
                } finally {values.close();}
            }
        } catch (Exception e) {
            throw new IllegalStateException("AD DNS SRV lookup unavailable",e);
        } finally {
            if (dns != null) try { dns.close(); } catch (Exception ignored) { }
        }
        if (unique.isEmpty()) throw new IllegalStateException("No valid AD domain controllers returned by DNS");
        List<Controller> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparingInt(Controller::priority).thenComparing(Controller::hostname));
        return List.copyOf(result);
    }
}
