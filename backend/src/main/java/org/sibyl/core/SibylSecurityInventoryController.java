package org.sibyl.core;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Core-managed private network scope, inventory and actionable endpoint hygiene alerts.
 * SecurityConfig enforces fine-grained authorities; no remote execution endpoints.
 */
@RestController
@RequestMapping("/api/v1/security")
public final class SibylSecurityInventoryController {
    private final JdbcTemplate jdbc;
    public SibylSecurityInventoryController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @GetMapping("/summary")
    public Map<String,Object> summary() {
        return Map.of(
            "assets",jdbc.queryForObject("SELECT COUNT(*) FROM sibyl_managed_assets",Integer.class),
            "openAlerts",jdbc.queryForObject(
                "SELECT COUNT(*) FROM sibyl_security_alerts WHERE status='OPEN'",Integer.class),
            "networks",jdbc.queryForObject(
                "SELECT COUNT(*) FROM sibyl_managed_networks WHERE enabled=1",Integer.class));
    }

    @GetMapping("/assets")
    public List<Map<String,Object>> assets(
        @RequestParam(defaultValue="50") int limit,
        @RequestParam(required=false) Long before) {
        checkPage(limit,before);
        return jdbc.queryForList(
            "SELECT id,collector_id,external_id,hostname,ipv4,os_name,"+
            "patch_state,protection_state,last_seen FROM sibyl_managed_assets "+
            "WHERE id < ? ORDER BY id DESC LIMIT ?",
            before==null?Long.MAX_VALUE:before,limit);
    }

    @GetMapping("/alerts")
    public List<Map<String,Object>> alerts(
        @RequestParam(defaultValue="50") int limit,
        @RequestParam(required=false) Long before) {
        checkPage(limit,before);
        return jdbc.queryForList(
            "SELECT a.id,a.asset_id,a.category,a.severity,a.message,a.status,"+
            "a.created_at,d.hostname,d.ipv4 FROM sibyl_security_alerts a "+
            "JOIN sibyl_managed_assets d ON d.id=a.asset_id "+
            "WHERE a.id < ? ORDER BY a.id DESC LIMIT ?",
            before==null?Long.MAX_VALUE:before,limit);
    }

    private static void checkPage(int limit,Long before) {
        if(limit<1 || limit>100 || (before!=null && before<=0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid page");
    }

    @GetMapping("/networks")
    public List<Map<String,Object>> networks() {
        return jdbc.queryForList(
            "SELECT cidr,label,enabled FROM sibyl_managed_networks ORDER BY cidr LIMIT 100");
    }

    public record Network(String cidr,String label) {}
    @PostMapping("/networks")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String,Object> createNetwork(Principal principal,@RequestBody Network request) {
        if(request==null || request.label()==null || request.label().isBlank()
            || request.label().length()>80)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid network label");
        String scope;
        try { scope=IPv4Scope.network(request.cidr()); }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid or public network scope");
        }
        try {
            jdbc.update("INSERT INTO sibyl_managed_networks(cidr,label) VALUES (?,?)",
                scope,request.label().trim());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Network already exists");
        }
        jdbc.update("INSERT INTO sibyl_audit_events(actor,action) VALUES (?,'managed-network-created')",
            principal.getName());
        return Map.of("cidr",scope,"label",request.label().trim(),"enabled",true);
    }

    @PostMapping("/alerts/{id}/ack")
    @Transactional
    public Map<String,Object> acknowledge(Principal principal,@PathVariable long id) {
        if(id<1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        int affected=jdbc.update(
            "UPDATE sibyl_security_alerts SET status='ACKNOWLEDGED' WHERE id=? AND status='OPEN'",id);
        if(affected!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Open alert not found");
        jdbc.update("INSERT INTO sibyl_audit_events(actor,action) VALUES (?,'security-alert-acknowledged')",
            principal.getName());
        return Map.of("id",id,"status","ACKNOWLEDGED");
    }
}
