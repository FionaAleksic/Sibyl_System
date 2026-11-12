package de.nikoit.sibyl.addons;

import de.nikoit.sibyl.addons.ad.SibylAdApi;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Safe, read-only DNS/service and local-interface inventory. No subnet port scans. */
@Service
public class InventoryDiscoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(InventoryDiscoveryService.class);
    public record Asset(long id,String hostname,String ipv4,String assetType,
                        String source,String details,String firstSeen,String lastSeen) {}
    public record Status(String domain,int controllers,int observations,String lastSuccess,
                         String lastError,boolean enabled,String scanMode) {}
    private final JdbcTemplate db;
    private final SibylAdApi ad;
    private final AddonConfigurationService configuration;
    private volatile long nextScheduledRun=0L;
    private final AtomicReference<String> lastSuccess = new AtomicReference<>("");
    private final AtomicReference<String> lastError = new AtomicReference<>("");

    public InventoryDiscoveryService(JdbcTemplate db,SibylAdApi ad,AddonConfigurationService configuration) {
        this.db=db;this.ad=ad;this.configuration=configuration;
    }

    public boolean enabled() {
        Boolean active=db.query("SELECT enabled FROM sibyl_addons WHERE addon_id='Sibyl.inventory'",
            rs->rs.next() && rs.getBoolean(1));
        return Boolean.TRUE.equals(active);
    }

    public List<Asset> list() {
        return db.query("SELECT id,hostname,ipv4,asset_type,source,details,first_seen,last_seen "
           + "FROM sibyl_discovered_assets ORDER BY asset_type,hostname",
           (r,i)->new Asset(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),
             r.getString(5),r.getString(6),r.getTimestamp(7).toInstant().toString(),
             r.getTimestamp(8).toInstant().toString()));
    }

    public Status status() {
        int controllerCount=db.queryForObject("SELECT COUNT(*) FROM sibyl_discovered_assets "
              +"WHERE asset_type='DOMAIN_CONTROLLER'",Integer.class);
        int count=db.queryForObject("SELECT COUNT(*) FROM sibyl_discovered_assets",Integer.class);
        return new Status(ad.defaultDomain(),controllerCount,count,lastSuccess.get(),lastError.get(),
              enabled(),"DNS-SRV, lokale Netzwerkschnittstellen, bereits erfasste Geräte; kein Subnetzscan");
    }

    /** Periodic refresh with no effect on existing manually edited infrastructure rows. */
    @Scheduled(fixedDelay=60000,initialDelay=30000)
    public void scheduledRefresh() {
        long now=System.currentTimeMillis();
        if(now < nextScheduledRun || !enabled())return;
        nextScheduledRun=now + configuration.integerValue("Sibyl.inventory","refreshMinutes")*60000L;
        try { refresh(); }
        catch (Exception e) { LOG.warn("Sibyl inventory refresh failed [{}]",e.getClass().getSimpleName()); }
    }

    @Transactional
    public synchronized Status refresh() {
        if (!enabled()) {
            lastError.set("Inventory addon disabled");
            return status();
        }
        List<SibylAdApi.Controller> dcs;
        try { dcs=ad.controllers(); }
        catch (Exception ex) {
            lastError.set("DNS-SRV discovery failed");
            throw ex;
        }
        for (SibylAdApi.Controller dc:dcs) {
            save("ad-dc:"+dc.hostname(),dc.hostname(),dc.ipv4(),
                "DOMAIN_CONTROLLER","ad-dns-srv","Via LDAP SRV, LDAPS 636");
        }
        if(configuration.boolValue("Sibyl.inventory","includeLocal"))try {
            String name=InetAddress.getLocalHost().getCanonicalHostName();
            var interfaces=NetworkInterface.getNetworkInterfaces();
            if (interfaces!=null) while (interfaces.hasMoreElements()) {
                var nic=interfaces.nextElement();
                if (!nic.isUp() || nic.isLoopback()) continue;
                var addresses=nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    var ip=addresses.nextElement();
                    if (ip instanceof Inet4Address && ip.isSiteLocalAddress()) {
                        save("local:"+nic.getName()+":"+ip.getHostAddress(),name,ip.getHostAddress(),
                             "SIBYL_SERVER","local-interface","Automatisch aus der Serverschnittstelle");
                    }
                }
            }
        } catch (Exception ex) {
            LOG.warn("Local interface discovery skipped [{}]",ex.getClass().getSimpleName());
        }
        // Reference objects remain explicitly marked as pre-existing, NOT newly network-discovered.
        if(configuration.boolValue("Sibyl.inventory","includeKnown")) {
        var existing=db.query("SELECT name,ipv4,role FROM devices WHERE ipv4 IS NOT NULL AND ipv4<>''",
            (r,i)->List.of(r.getString(1),r.getString(2),r.getString(3)));
        for (var row:existing) {
            String ipv4=row.get(1);
            if (ipv4.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
                save("known:"+row.get(0),row.get(0),ipv4,
                    "KNOWN_DEVICE","existing-inventory","Bestehender Eintrag: "+row.get(2));
            }
        }
        }
        lastError.set("");
        lastSuccess.set(Instant.now().toString());
        return status();
    }

    private void save(String key,String name,String ip,String type,String source,String details) {
        db.update("INSERT INTO sibyl_discovered_assets(asset_key,hostname,ipv4,asset_type,source,details) "
             +"VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE hostname=VALUES(hostname), "
             +"ipv4=VALUES(ipv4),asset_type=VALUES(asset_type),source=VALUES(source), "
             +"details=VALUES(details),last_seen=CURRENT_TIMESTAMP",
             key,name,ip,type,source,details);
    }
}
