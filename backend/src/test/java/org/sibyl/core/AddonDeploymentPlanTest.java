package org.sibyl.core;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class AddonDeploymentPlanTest {
    final ObjectMapper json = new ObjectMapper();
    Path bundle(String id,String version,String descriptor,String ui) throws Exception {
        Path temp=Files.createTempFile("sibyl-deploy-test",".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
            put(zip,"module.json","{\"id\":\""+id+"\",\"version\":\""+version+"\"}");
            put(zip,"deployment.json",descriptor);
            if(ui!=null)put(zip,"ui/index.html",ui);
        }
        return temp;
    }
    void put(ZipOutputStream zip,String name,String body) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(body.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    @Test void acceptsAddonOwnedPageAndStrictIdentity() throws Exception {
        String descriptor="""
          {"schema":1,"id":"Sibyl.demo","version":"0.1.0-rc.2",
           "kind":"feature","dependencies":[{"id":"Sibyl.ad","version":"0.1.0-rc.2"}],
           "operations":[{"type":"publish-ui","path":"ui/index.html","label":"Demo"}]}
          """;
        Path temp=bundle("Sibyl.demo","0.1.0-rc.2",descriptor,"<!doctype html><title>Demo</title>");
        try(ZipFile zip=new ZipFile(temp.toFile())){
            var plan=AddonDeploymentPlan.read(zip,json,"Sibyl.demo","0.1.0-rc.2");
            assertEquals("Demo",plan.operations().get(0).label());
            assertEquals("Sibyl.ad",plan.dependencies().get(0).id());
            assertThrows(IllegalArgumentException.class,
                ()->AddonDeploymentPlan.read(zip,json,"Sibyl.other","0.1.0-rc.2"));
        } finally {Files.deleteIfExists(temp);}
    }
    @Test void blocksArbitraryCommandsAndTraversal() throws Exception {
        assertFalse(AddonDeploymentPlan.validResource("ui/../secrets"));
        assertFalse(AddonDeploymentPlan.validResource("/etc/passwd"));
        assertFalse(AddonDeploymentPlan.validResource("ui/a//b"));
        String descriptor="""
          {"schema":1,"id":"Sibyl.demo","version":"0.1.0-rc.2",
           "kind":"feature","dependencies":[],
           "operations":[{"type":"run-shell","path":"ui/index.html","label":"Demo"}]}
          """;
        Path temp=bundle("Sibyl.demo","0.1.0-rc.2",descriptor,"page");
        try(ZipFile zip=new ZipFile(temp.toFile())){
            assertThrows(IllegalArgumentException.class,
                ()->AddonDeploymentPlan.read(zip,json,"Sibyl.demo","0.1.0-rc.2"));
        } finally {Files.deleteIfExists(temp);}
    }
}
