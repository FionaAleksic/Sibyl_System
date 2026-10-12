package org.sibyl.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Generic authenticated, owner-scoped, versioned document capability.
 * Addons cannot submit SQL or access each other's data.
 * Records are visible only after the addon has been activated.
 */
@RestController
@RequestMapping("/api/v1/ext/{addon}/records")
public final class AddonRecordsController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private static final int MAX_BODY=120_000;

    public AddonRecordsController(JdbcTemplate jdbc,ObjectMapper mapper) {
        this.jdbc=jdbc;this.mapper=mapper;
    }
    private void scope(String addon,String collection) {
        if(!AddonDeploymentPlan.validId(addon) || collection==null ||
           !collection.matches("[a-z][a-z0-9-]{0,48}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid namespace");
        Integer enabled=jdbc.queryForObject("""
            SELECT COUNT(*) FROM sibyl_addon_installs
            WHERE addon_id=? AND active=1
            """,Integer.class,addon);
        if(enabled==null || enabled!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Addon not active");
    }
    private void value(JsonNode value){
        if(value==null || !value.isObject() || value.toString().length()>MAX_BODY)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid record JSON");
    }
    private static void key(String id){
        if(id==null || !id.matches("[0-9a-f-]{36}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid record ID");
    }
    public record Record(String id,int version,JsonNode value,String updatedAt) {}
    private Record map(java.sql.ResultSet rs,int idx)throws java.sql.SQLException {
        JsonNode data;
        try{data=mapper.readTree(rs.getString("payload_json"));}
        catch(Exception ex){throw new java.sql.SQLException("Stored addon JSON corrupted",ex);}
        return new Record(rs.getString("record_id"),rs.getInt("revision"),
            data,rs.getString("updated_at"));
    }

    @GetMapping("/{collection}")
    public List<Record> list(Principal principal,@PathVariable String addon,
                             @PathVariable String collection) {
        scope(addon,collection);
        return jdbc.query("""
            SELECT record_id,revision,payload_json,updated_at FROM sibyl_extension_records
            WHERE addon_id=? AND collection=? AND owner=? AND archived=0
            ORDER BY updated_at DESC LIMIT 200
            """,this::map,addon,collection,principal.getName());
    }

    @GetMapping("/{collection}/{id}")
    public Record get(Principal principal,@PathVariable String addon,
                      @PathVariable String collection,@PathVariable String id) {
        scope(addon,collection);key(id);
        var rows=jdbc.query("""
            SELECT record_id,revision,payload_json,updated_at FROM sibyl_extension_records
            WHERE addon_id=? AND collection=? AND owner=? AND record_id=? AND archived=0
            """,this::map,addon,collection,principal.getName(),id);
        if(rows.size()!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return rows.get(0);
    }

    @PostMapping("/{collection}")
    @Transactional
    public Record create(Principal principal,@PathVariable String addon,
                         @PathVariable String collection,@RequestBody JsonNode body) {
        scope(addon,collection);value(body);
        String id=UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO sibyl_extension_records
              (addon_id,collection,owner,record_id,revision,payload_json,archived)
            VALUES (?,?,?,?,1,?,0)
            """,addon,collection,principal.getName(),id,body.toString());
        jdbc.update("""
            INSERT INTO sibyl_extension_record_revisions
              (addon_id,collection,owner,record_id,revision,payload_json)
            VALUES (?,?,?,?,1,?)
            """,addon,collection,principal.getName(),id,body.toString());
        return get(principal,addon,collection,id);
    }

    public record Update(int expectedVersion,JsonNode value) {}
    @PutMapping("/{collection}/{id}")
    @Transactional
    public Record update(Principal principal,@PathVariable String addon,
                         @PathVariable String collection,@PathVariable String id,
                         @RequestBody Update input) {
        scope(addon,collection);key(id);
        if(input==null || input.expectedVersion()<1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Expected revision required");
        value(input.value());
        int changed=jdbc.update("""
            UPDATE sibyl_extension_records SET payload_json=?,revision=revision+1,
                  updated_at=CURRENT_TIMESTAMP(6)
            WHERE addon_id=? AND collection=? AND owner=?
              AND record_id=? AND revision=? AND archived=0
            """,input.value().toString(),addon,collection,principal.getName(),
              id,input.expectedVersion());
        if(changed!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Revision conflict or record unavailable");
        jdbc.update("""
            INSERT INTO sibyl_extension_record_revisions
              (addon_id,collection,owner,record_id,revision,payload_json)
            VALUES (?,?,?,?,?,?)
            """,addon,collection,principal.getName(),id,input.expectedVersion()+1,
             input.value().toString());
        return get(principal,addon,collection,id);
    }

    @GetMapping("/{collection}/{id}/history")
    public List<Record> history(Principal principal,@PathVariable String addon,
                                @PathVariable String collection,@PathVariable String id) {
        scope(addon,collection);key(id);
        Integer count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM sibyl_extension_records
            WHERE addon_id=? AND collection=? AND owner=? AND record_id=?
            """,Integer.class,addon,collection,principal.getName(),id);
        if(count==null||count!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return jdbc.query("""
            SELECT record_id,revision,payload_json,created_at AS updated_at
            FROM sibyl_extension_record_revisions
            WHERE addon_id=? AND collection=? AND owner=? AND record_id=?
            ORDER BY revision DESC LIMIT 100
            """,this::map,addon,collection,principal.getName(),id);
    }

    @DeleteMapping("/{collection}/{id}")
    @Transactional
    public Map<String,Object> archive(Principal principal,@PathVariable String addon,
                                       @PathVariable String collection,@PathVariable String id,
                                       @RequestParam int expectedVersion) {
        scope(addon,collection);key(id);
        if(expectedVersion<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        int changed=jdbc.update("""
            UPDATE sibyl_extension_records SET archived=1,revision=revision+1,
                updated_at=CURRENT_TIMESTAMP(6)
            WHERE addon_id=? AND collection=? AND owner=? AND record_id=?
                AND revision=? AND archived=0
            """,addon,collection,principal.getName(),id,expectedVersion);
        if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT);
        return Map.of("archived",true,"id",id);
    }
}
