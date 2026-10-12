package org.sibyl.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * General-purpose addon deploy executor: the ADDON owns the deployment plan;
 * Core interprets only explicit, audited capability-limited operations.
 * No shell, systemd, raw SQL or arbitrary addon bytecode is executed here.
 */
@Service
public class AddonDeploymentService {
    private final ObjectMapper mapper;
    private final AddonCatalog catalog;
    private final GitHubReleaseService releases;
    private final JdbcTemplate jdbc;
    private final Path root = Path.of(System.getenv().getOrDefault(
        "SIBYL_ADDON_DATA", "/var/lib/sibyl/addons")).toAbsolutePath().normalize();

    public AddonDeploymentService(ObjectMapper mapper, AddonCatalog catalog,
                                  GitHubReleaseService releases, JdbcTemplate jdbc) {
        this.mapper=mapper;this.catalog=catalog;this.releases=releases;this.jdbc=jdbc;
    }

    private record Verified(AddonDeploymentPlan plan, String digest, Path path) {}

    private static String digest(Path path) throws Exception {
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(path)) {
            byte[] buffer=new byte[8192];
            int count;
            while((count=in.read(buffer))!=-1)sha.update(buffer,0,count);
        }
        return HexFormat.of().formatHex(sha.digest());
    }

    /**
     * Re-query GitHub release metadata, as in the original secure release
     * downloader. A stale local ZIP cannot be installed by changing a filename.
     */
    private Verified prepare(String id, String tag, String channel) throws Exception {
        if (!AddonDeploymentPlan.validId(id)
            || tag==null || !tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+-rc\\.[0-9]+")
            || !("prerelease".equals(channel)||"stable".equals(channel)))
            throw new IllegalArgumentException("Invalid addon, channel or release tag");
        AddonCatalog.Addon addon=catalog.get(id);
        var release=releases.latest(addon,channel);
        if(release==null || !tag.equals(release.tag()) || release.asset()==null
            || !release.digest().matches("sha256:[a-fA-F0-9]{64}"))
            throw new IllegalStateException("Latest trusted GitHub release not available");
        Path dir=root.resolve(id);
        Path path=dir.resolve(tag+".zip");
        if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)
            || Files.isSymbolicLink(dir) || Files.isSymbolicLink(root)
            || Files.size(path)>64L*1024*1024)
            throw new IllegalStateException("Download the verified GitHub release first");
        String sha=digest(path);
        if (!release.digest().substring(7).equalsIgnoreCase(sha))
            throw new IllegalStateException("Local ZIP checksum differs from GitHub release");
        try(ZipFile zip=new ZipFile(path.toFile())) {
            AddonDeploymentPlan plan=AddonDeploymentPlan.read(zip,mapper,
                id,tag.substring(1));
            if (!(("integration".equals(addon.type()) && "dependency".equals(plan.kind()))
                || ("feature".equals(addon.type()) && "feature".equals(plan.kind()))))
                throw new IllegalArgumentException("Addon category mismatch");
            return new Verified(plan,sha,path);
        }
    }

    public Map<String,Object> preview(String id, String tag, String channel) {
        try {
            Verified verified=prepare(id,tag,channel);
            AddonDeploymentPlan plan=verified.plan();
            return Map.of("id",id,"version",plan.version(),"kind",plan.kind(),
                "dependencies",plan.dependencies(),"operations",plan.operations(),
                "sha256",verified.digest(),
                "warning","Only supported operations execute. No addon script runs as root.");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,ex.getMessage());
        } catch(Exception ex){
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Unable to verify addon plan");
        }
    }

    @Transactional
    public synchronized Map<String,String> install(Principal actor,String id,String tag,String channel) {
        Verified release;
        try { release=prepare(id,tag,channel); }
        catch(IllegalArgumentException ex){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,ex.getMessage());
        } catch(IllegalStateException ex){
            throw new ResponseStatusException(HttpStatus.CONFLICT,ex.getMessage());
        } catch(Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Unable to verify addon plan");
        }
        AddonDeploymentPlan plan=release.plan();
        var existing=jdbc.query("SELECT version FROM sibyl_addon_installs WHERE addon_id=?",
            (rs,i)->rs.getString(1),id);
        if (!existing.isEmpty() && !existing.get(0).equals(plan.version()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Only one installed version per addon ID; deactivate/uninstall the old version first");
        for(AddonDeploymentPlan.Dependency required:plan.dependencies()) {
            Integer exists=jdbc.queryForObject("""
                SELECT COUNT(*) FROM sibyl_addon_installs
                WHERE addon_id=? AND version=? AND active=1
                """,Integer.class,required.id(),required.version());
            if(exists==null || exists!=1)
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Missing active dependency: "+required.id()+" "+required.version());
        }
        String navLabel="";
        String navEntry="";
        for(AddonDeploymentPlan.Operation op:plan.operations()) {
            if ("publish-ui".equals(op.type())) {
                navLabel=op.label();navEntry=op.path();
            } else {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unsupported operation: "+op.type());
            }
        }
        if(existing.isEmpty())
            jdbc.update("""
                INSERT INTO sibyl_addon_installs
                    (addon_id,version,archive_sha256,kind,nav_label,nav_entry,active)
                VALUES (?,?,?,?,?,?,1)
                """,id,plan.version(),release.digest(),plan.kind(),navLabel,navEntry);
        else
            jdbc.update("""
                UPDATE sibyl_addon_installs SET active=1,
                    archive_sha256=?,nav_label=?,nav_entry=?,updated_at=CURRENT_TIMESTAMP(6)
                WHERE addon_id=?
                """,release.digest(),navLabel,navEntry,id);
        jdbc.update("DELETE FROM sibyl_addon_install_dependencies WHERE addon_id=?",id);
        for(AddonDeploymentPlan.Dependency required:plan.dependencies()) {
            jdbc.update("""
                INSERT INTO sibyl_addon_install_dependencies
                    (addon_id,requires_id,requires_version) VALUES (?,?,?)
                """,id,required.id(),required.version());
        }
        jdbc.update("INSERT INTO sibyl_audit_events (actor,action) VALUES (?,?)",
            actor.getName(),"addon-deployment-installed:"+id);
        return Map.of("id",id,"version",plan.version(),"state","registered",
            "note","Addon-owned declarative UI deployment complete; external service hooks not supported in SDK v1");
    }

    @Transactional
    public Map<String,String> deactivate(Principal actor,String id) {
        if(!AddonDeploymentPlan.validId(id))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Integer consumers=jdbc.queryForObject("""
            SELECT COUNT(*) FROM sibyl_addon_install_dependencies AS d
            JOIN sibyl_addon_installs AS a ON a.addon_id=d.addon_id AND a.active=1
            WHERE d.requires_id=?
            """,Integer.class,id);
        if(consumers!=null && consumers>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot deactivate an addon required by another active addon");
        int rows=jdbc.update("UPDATE sibyl_addon_installs SET active=0 WHERE addon_id=? AND active=1",id);
        if(rows!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        jdbc.update("INSERT INTO sibyl_audit_events (actor,action) VALUES (?,?)",
            actor.getName(),"addon-deployment-deactivated:"+id);
        return Map.of("id",id,"state","inactive","data","preserved");
    }

    private Path installedArchive(String id,String version) {
        return root.resolve(id).resolve("v"+version+".zip");
    }

    private boolean stillVerified(String id,String version,String sha) {
        Path dir=root.resolve(id);
        Path file=installedArchive(id,version);
        try {
            return AddonDeploymentPlan.validId(id)
                && !Files.isSymbolicLink(root) && !Files.isSymbolicLink(dir)
                && Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)
                && Files.size(file)<=64L*1024*1024
                && digest(file).equals(sha);
        } catch(Exception ex){return false;}
    }
    public List<Map<String,String>> active() {
        var rows=jdbc.queryForList("""
            SELECT addon_id,version,archive_sha256,kind,nav_label,nav_entry
            FROM sibyl_addon_installs WHERE active=1 ORDER BY nav_label
            """);
        List<Map<String,String>> active=new ArrayList<>();
        for(Map<String,Object> row:rows) {
            String id=(String)row.get("addon_id");
            String version=(String)row.get("version");
            String sha=(String)row.get("archive_sha256");
            if(!stillVerified(id,version,sha))continue;
            String kind=(String)row.get("kind");
            String entry=(String)row.get("nav_entry");
            String label=(String)row.get("nav_label");
            active.add(Map.of("id",id,"version",version,"type",kind,
                "name",label.isBlank()?id:label,"page",
                "feature".equals(kind) ? "/extensions/"+id+"/"+entry : "",
                "state","registered"));
        }
        return List.copyOf(active);
    }

    public record Asset(byte[] bytes,String type) {}
    public Asset asset(String id,String filename) {
        if(!AddonDeploymentPlan.validId(id) || filename==null
           || !filename.matches("[A-Za-z0-9_.-]{1,100}") || filename.contains(".."))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid asset path");
        List<Map<String,Object>> rows=jdbc.queryForList("""
            SELECT version,archive_sha256,kind FROM sibyl_addon_installs
            WHERE addon_id=? AND active=1
            """,id);
        if(rows.isEmpty()||!"feature".equals(rows.get(0).get("kind")))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String version=(String)rows.get(0).get("version");
        String sha=(String)rows.get(0).get("archive_sha256");
        if(!stillVerified(id,version,sha))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Addon package changed");
        String path="ui/"+filename;
        if(!AddonDeploymentPlan.validResource(path))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String type;
        if(filename.endsWith(".html"))type="text/html; charset=utf-8";
        else if(filename.endsWith(".js"))type="text/javascript; charset=utf-8";
        else if(filename.endsWith(".css"))type="text/css; charset=utf-8";
        else if(filename.endsWith(".png"))type="image/png";
        else if(filename.endsWith(".jpg"))type="image/jpeg";
        else if(filename.endsWith(".webp"))type="image/webp";
        else throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        try(ZipFile zip=new ZipFile(installedArchive(id,version).toFile())) {
            ZipEntry entry=zip.getEntry(path);
            if(entry==null||entry.isDirectory()||entry.getSize()<0||entry.getSize()>2_000_000)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            try(InputStream in=zip.getInputStream(entry)) {
                byte[] bytes=in.readNBytes(2_000_001);
                if(bytes.length>2_000_000)
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
                return new Asset(bytes,type);
            }
        } catch(ResponseStatusException ex){throw ex;}
        catch(Exception ex){
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Addon asset failed");
        }
    }
}
