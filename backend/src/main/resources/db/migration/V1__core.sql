-- MySQL 8.0+ schema (utf8mb4). No PostgreSQL-specific syntax.
CREATE TABLE sibyl_users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'ADMIN',
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sibyl_organization (
    id SMALLINT NOT NULL PRIMARY KEY,
    organization VARCHAR(80) NOT NULL DEFAULT 'Sibyl System',
    language VARCHAR(8) NOT NULL DEFAULT 'de',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/Berlin',
    accent VARCHAR(16) NOT NULL DEFAULT 'classic',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_sibyl_org_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sibyl_organization (id, organization) VALUES (1, 'Sibyl System');

CREATE TABLE sibyl_addon_settings (
    addon_id VARCHAR(120) NOT NULL PRIMARY KEY,
    settings_json JSON NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sibyl_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor VARCHAR(80) NOT NULL,
    action VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_sibyl_audit_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
