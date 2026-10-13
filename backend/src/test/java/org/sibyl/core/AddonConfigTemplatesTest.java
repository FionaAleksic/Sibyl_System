package org.sibyl.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** No network or database needed: verify generic config schema compatibility. */
class AddonConfigTemplatesTest {
    final ObjectMapper mapper = new ObjectMapper();
    final AddonConfigTemplates templates = new AddonConfigTemplates(mapper, null);

    @Test void defaultValuesAndRequiredAdFields() throws Exception {
        var schema = mapper.readTree("""
            {"type":"object","properties":{
               "serverUrl":{"type":"string","pattern":"^ldaps://.+$"},
               "baseDn":{"type":"string","minLength":3},
               "pageSize":{"type":"integer","minimum":10,"maximum":1000,"default":250},
               "enableWrites":{"type":"boolean","const":false,"default":false}},
             "required":["serverUrl","baseDn"]}
            """);
        ObjectNode defaults = mapper.createObjectNode().put("pageSize",250).put("enableWrites",false);
        var template = new AddonConfigTemplates.Template(schema,defaults,List.of("serverUrl","baseDn"),"v0.1.0-rc.4",true);
        assertEquals(List.of("serverUrl","baseDn"),templates.missingRequired(template,defaults));
        var configured = defaults.deepCopy().put("serverUrl","ldaps://dc.example.org:636").put("baseDn","DC=example,DC=org");
        assertTrue(templates.validate(template,configured));
        assertFalse(templates.validate(template,configured.deepCopy().put("pageSize",1001)));
        assertFalse(templates.validate(template,configured.deepCopy().put("enableWrites",true)));
        assertFalse(templates.validate(template,configured.deepCopy().put("serverUrl","ldap://dc.example.org")));
        assertFalse(templates.validate(template,configured.deepCopy().put("password","do-not-store")));
    }
    @Test void protectsSecretKeysButAllowsEnvironmentReference() {
        assertTrue(AddonConfigTemplates.secret("clientSecret"));
        assertTrue(AddonConfigTemplates.secret("bindPassword"));
        assertTrue(AddonConfigTemplates.secret("apiToken"));
        assertFalse(AddonConfigTemplates.secret("credentialEnvironmentVariable"));
        assertFalse(AddonConfigTemplates.secret("connectTimeoutMs"));
    }
    @Test void checksFixedPlanningSlotsAndEnums() throws Exception {
        var schema=mapper.readTree("""
          {"type":"object","properties":{
            "slotLabels":{"type":"array","prefixItems":[{"const":"Primär"},{"const":"Sekundär"},{"const":"Tertiär"}],"minItems":3,"maxItems":3},
            "minimumEntryMinutes":{"type":"integer","enum":[15,30,60]}
          }}
          """);
        var temp=new AddonConfigTemplates.Template(schema,mapper.createObjectNode(),List.of(),"",true);
        var okay=mapper.readTree("{\"slotLabels\":[\"Primär\",\"Sekundär\",\"Tertiär\"],\"minimumEntryMinutes\":15}");
        assertTrue(templates.validate(temp,okay));
        assertFalse(templates.validate(temp,mapper.readTree("{\"slotLabels\":[\"other\",\"Sekundär\",\"Tertiär\"]}")));
        assertFalse(templates.validate(temp,mapper.readTree("{\"minimumEntryMinutes\":20}")));
    }
}
