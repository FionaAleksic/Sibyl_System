package org.sibyl.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Real MySQL validation of the generic per-account extension document broker.
 * Changes are rolled back so tests never mutate real addon data.
 */
@SpringBootTest
@Transactional
class AddonRecordsIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AddonRecordsController records;

    @Test
    void recordHistoryConflictsArchiveAndTenantIsolation()throws Exception{
        jdbc.update("""
            INSERT INTO sibyl_addon_installs
              (addon_id,version,archive_sha256,kind,nav_label,nav_entry,active)
            VALUES ('Sibyl.doc','0.1.0-rc.3',REPEAT('0',64),'feature',
              'Wiki','ui/index.html',1)
            ON DUPLICATE KEY UPDATE active=1
            """);
        Principal alice=()->"integration.alice";
        Principal bob=()->"integration.bob";
        var doc=mapper.readTree("{\"title\":\"Erster Entwurf\",\"markdown\":\"# Willkommen\"}");
        AddonRecordsController.Record stored=records.create(alice,"Sibyl.doc","pages",doc);
        assertEquals(1,stored.version());
        assertEquals("Erster Entwurf",stored.value().get("title").asText());
        assertEquals(1,records.list(alice,"Sibyl.doc","pages").size());
        assertTrue(records.list(bob,"Sibyl.doc","pages").isEmpty());
        ResponseStatusException denied=assertThrows(ResponseStatusException.class,
            ()->records.get(bob,"Sibyl.doc","pages",stored.id()));
        assertEquals(HttpStatus.NOT_FOUND,denied.getStatusCode());

        var updated=mapper.readTree("{\"title\":\"Zweite Version\",\"markdown\":\"Neu\"}");
        var revision=records.update(alice,"Sibyl.doc","pages",stored.id(),
            new AddonRecordsController.Update(1,updated));
        assertEquals(2,revision.version());
        assertEquals(2,records.history(alice,"Sibyl.doc","pages",stored.id()).size());
        var conflict=assertThrows(ResponseStatusException.class,
            ()->records.update(alice,"Sibyl.doc","pages",stored.id(),
                new AddonRecordsController.Update(1,updated)));
        assertEquals(HttpStatus.CONFLICT,conflict.getStatusCode());
        var archived=records.archive(alice,"Sibyl.doc","pages",stored.id(),2);
        assertEquals(true,archived.get("archived"));
        assertTrue(records.list(alice,"Sibyl.doc","pages").isEmpty());

        var other=assertThrows(ResponseStatusException.class,
            ()->records.create(alice,"Sibyl.inventory","devices",updated));
        assertEquals(HttpStatus.NOT_FOUND,other.getStatusCode());
    }
}
