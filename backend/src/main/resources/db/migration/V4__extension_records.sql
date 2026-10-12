-- General owner-scoped persistent storage shared as a safe Core capability.
CREATE TABLE sibyl_extension_records (
 addon_id VARCHAR(120) NOT NULL,
 collection VARCHAR(49) NOT NULL,
 owner VARCHAR(80) NOT NULL,
 record_id CHAR(36) NOT NULL,
 revision INT NOT NULL DEFAULT 1,
 payload_json JSON NOT NULL,
 archived BOOLEAN NOT NULL DEFAULT FALSE,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(addon_id,collection,owner,record_id),
 INDEX idx_sibyl_ext_records_updated(addon_id,collection,owner,archived,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sibyl_extension_record_revisions (
 addon_id VARCHAR(120) NOT NULL,
 collection VARCHAR(49) NOT NULL,
 owner VARCHAR(80) NOT NULL,
 record_id CHAR(36) NOT NULL,
 revision INT NOT NULL,
 payload_json JSON NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(addon_id,collection,owner,record_id,revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
