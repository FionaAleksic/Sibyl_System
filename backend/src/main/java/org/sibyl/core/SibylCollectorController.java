package org.sibyl.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Explicitly configured, machine-only authenticated inventory ingress.
 * HMAC canonical: timestamp \n nonce \n collector-id \n SHA256(exact request body).
 * Does not read/collect screens, keystrokes, browsing or communications.
 */
@RestController
@RequestMapping("/api/v1/collector")
public class SibylCollectorController {
    private final CollectorProof proof;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SibylCollectorController(CollectorProof proof,JdbcTemplate jdbc,ObjectMapper mapper) {
        this.proof=proof; this.jdbc=jdbc; this.mapper=mapper;
    }

    @PostMapping("/snapshot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public Map<String,Object> snapshot(
        @RequestHeader(value="X-Sibyl-Collector",required=false) String id,
        @RequestHeader(value="X-Sibyl-Time",required=false) String time,
        @RequestHeader(value="X-Sibyl-Nonce",required=false) String nonce,
        @RequestHeader(value="X-Sibyl-Signature",required=false) String signature,
        @RequestBody byte[] body) {
        if (!proof.verify(id,time,nonce,signature,body))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid collector signature");
        JsonNode json;
        try {
            json=mapper.readTree(body);
        } catch(Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid snapshot JSON");
        }
        if (json==null || !json.isObject() || json.size()!=6)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Exact snapshot fields required");
        for (String field : List.of("assetId","hostname","ipv4","osName","patchState","protectionState")) {
            if (!json.has(field) || !json.get(field).isTextual())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid snapshot shape");
        }
        String external=json.path("assetId").asText();
        String hostname=json.path("hostname").asText();
        String ipv4=json.path("ipv4").asText();
        String osName=json.path("osName").asText();
        String patch=json.path("patchState").asText();
        String protection=json.path("protectionState").asText();
        if (!external.matches("[a-zA-Z0-9_.:-]{1,90}")
            || !hostname.matches("[a-zA-Z0-9_.-]{1,120}")
            || osName.isBlank() || osName.length()>100
            || !List.of("current","outdated","unknown").contains(patch)
            || !List.of("enabled","disabled","unknown").contains(protection))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid device metadata");
        List<String> networks=jdbc.queryForList(
            "SELECT cidr FROM sibyl_managed_networks WHERE enabled=1",String.class);
        try {
            if(!IPv4Scope.inside(networks,ipv4))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"IP outside registered networks");
        } catch(IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid IPv4 address");
        }
        try {
            jdbc.update("INSERT INTO sibyl_collector_nonces(collector_id,nonce) VALUES (?,?)",id,nonce);
        } catch(DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Collector nonce already seen");
        }
        jdbc.update("""
            INSERT INTO sibyl_managed_assets
              (collector_id,external_id,hostname,ipv4,os_name,patch_state,protection_state)
            VALUES (?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE hostname=VALUES(hostname),ipv4=VALUES(ipv4),
              os_name=VALUES(os_name),patch_state=VALUES(patch_state),
              protection_state=VALUES(protection_state),last_seen=CURRENT_TIMESTAMP(6)
            """,id,external,hostname,ipv4,osName,patch,protection);
        Long assetId=jdbc.queryForObject(
            "SELECT id FROM sibyl_managed_assets WHERE collector_id=? AND external_id=?",
            Long.class,id,external);
        if(assetId==null) throw new IllegalStateException("Asset insert failed");
        updateAlert(assetId,"patch-status","MEDIUM","Device reports outdated patches",
            patch.equals("outdated"));
        updateAlert(assetId,"endpoint-protection","HIGH","Endpoint protection reported disabled",
            protection.equals("disabled"));
        jdbc.update("""
            DELETE FROM sibyl_collector_nonces
            WHERE accepted_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 10 MINUTE)
            """);
        return Map.of("accepted",true,"assetId",assetId);
    }

    private void updateAlert(long asset,String category,String severity,String message,boolean finding) {
        if (finding) {
            Integer active=jdbc.queryForObject("""
                SELECT COUNT(*) FROM sibyl_security_alerts
                WHERE asset_id=? AND category=? AND status IN ('OPEN','ACKNOWLEDGED')
                """,Integer.class,asset,category);
            if(active!=null && active==0)
                jdbc.update("""
                    INSERT INTO sibyl_security_alerts(asset_id,category,severity,message)
                    VALUES (?,?,?,?)
                    """,asset,category,severity,message);
        } else {
            jdbc.update("""
                UPDATE sibyl_security_alerts SET status='RESOLVED'
                WHERE asset_id=? AND category=? AND status IN ('OPEN','ACKNOWLEDGED')
                """,asset,category);
        }
    }
}
