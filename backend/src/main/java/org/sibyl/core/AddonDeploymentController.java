package org.sibyl.core;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Routes are generic and have no named addon conditions.
 * Admin install is an explicit action; download alone never activates a ZIP.
 */
@RestController
public final class AddonDeploymentController {
    private final AddonDeploymentService deploy;
    public AddonDeploymentController(AddonDeploymentService deploy) {this.deploy=deploy;}

    public record InstallRequest(String channel,String releaseTag) {}
    @GetMapping("/api/v1/admin/extensions/{id}/plan")
    public Map<String,Object> plan(@PathVariable String id,
        @RequestParam String releaseTag,
        @RequestParam(defaultValue="prerelease") String channel) {
        return deploy.preview(id,releaseTag,channel);
    }
    @PostMapping("/api/v1/admin/extensions/{id}/install")
    public Map<String,String> install(@PathVariable String id,Principal actor,
                                     @RequestBody InstallRequest request) {
        if(request==null || request.channel()==null||request.releaseTag()==null)
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST);
        return deploy.install(actor,id,request.releaseTag(),request.channel());
    }
    @PostMapping("/api/v1/admin/extensions/{id}/deactivate")
    public Map<String,String> deactivate(@PathVariable String id,Principal actor) {
        return deploy.deactivate(actor,id);
    }
    @GetMapping("/api/v1/extensions")
    public Map<String,Object> active() {
        return Map.of("modules",deploy.active(),"runtime","addon-deployment-contract-v1");
    }
    @GetMapping("/extensions/{id}/ui/{filename:.+}")
    public ResponseEntity<byte[]> asset(@PathVariable String id,@PathVariable String filename){
        AddonDeploymentService.Asset asset=deploy.asset(id,filename);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE,asset.type())
            .header("X-Content-Type-Options","nosniff")
            // Sandboxed even in a first-party URL. No cookie, API or Core DOM access.
            .header("Content-Security-Policy","sandbox allow-scripts allow-top-navigation-by-user-activation; "+
                "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; "+
                "img-src 'self' data:; connect-src 'none'; form-action 'none'; base-uri 'none'")
            .header(HttpHeaders.CACHE_CONTROL,"no-store")
            .body(asset.bytes());
    }
}
