package de.nikoit.sibyl.addons;

import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AddonRegistry {
    private final JdbcTemplate db;
    private final AuditService audit;
    private final List<SibylAddonProvider> providers;
    public record Addon(String addonId,String name,String version,String description,String moduleType,
                        String documentMode,boolean enabled,boolean allowUsers) {}
    public record SetEnabled(boolean enabled) {}
    public AddonRegistry(JdbcTemplate db,AuditService audit,List<SibylAddonProvider> providers) {
        this.db=db;this.audit=audit;this.providers=providers;
    }
    private List<Addon> query(String sql,Object... args) {
        return db.query(sql,(rs,i)->new Addon(rs.getString("addon_id"),rs.getString("name"),
            rs.getString("version"),rs.getString("description"),rs.getString("module_type"),
            rs.getString("document_mode"),rs.getBoolean("enabled"),rs.getBoolean("allow_users")),args)
           .stream().filter(a->BuiltinAddonCatalog.isBundled(a.addonId())).toList();
    }
    private static final String FIELDS="SELECT addon_id,name,version,description,module_type,document_mode,enabled,allow_users FROM sibyl_addons ";

    public List<Addon> all() {return query(FIELDS+"ORDER BY name");}
    public List<Addon> visible(Authentication who) {
        if (isAdmin(who)) return query(FIELDS+"WHERE enabled=TRUE ORDER BY name");
        return query(FIELDS+"WHERE enabled=TRUE AND allow_users=TRUE ORDER BY name");
    }
    public boolean isAdmin(Authentication who) {
        return who.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));
    }
    public Addon accessible(String id,Authentication who) {
        Addon a=query(FIELDS+"WHERE addon_id=?",id).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Addon not found"));
        if (!a.enabled() || (!isAdmin(who) && !a.allowUsers())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Addon unavailable");
        }
        return a;
    }

    @Transactional
    public Addon enable(String id,boolean enabled,Principal actor) {
        query(FIELDS+"WHERE addon_id=? FOR UPDATE",id).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Addon not found"));
        if (enabled) {
            Addon selected=query(FIELDS+"WHERE addon_id=?",id).get(0);
            if(providers.stream().noneMatch(p->p.type().equals(selected.moduleType())))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Addon code not installed");
            List<String> missing=db.query("SELECT d.requires_addon_id FROM sibyl_addon_dependencies d JOIN sibyl_addons a ON d.requires_addon_id=a.addon_id WHERE d.addon_id=? AND a.enabled=FALSE",
                (rs,i)->rs.getString(1),id);
            if (!missing.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Required addon disabled: "+String.join(",",missing));
        } else {
            List<String> active=db.query("SELECT d.addon_id FROM sibyl_addon_dependencies d JOIN sibyl_addons a ON d.addon_id=a.addon_id WHERE d.requires_addon_id=? AND a.enabled=TRUE",
                (rs,i)->rs.getString(1),id);
            if (!active.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Dependent addon still active: "+String.join(",",active));
        }
        db.update("UPDATE sibyl_addons SET enabled=? WHERE addon_id=?",enabled,id);
        audit.record(actor.getName(),enabled?"ADDON_ENABLE":"ADDON_DISABLE",id);
        return query(FIELDS+"WHERE addon_id=?",id).get(0);
    }

}
