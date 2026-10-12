-- Generic registry; module-specific deployment instructions remain in each ZIP.
CREATE TABLE sibyl_addon_installs (
  addon_id VARCHAR(120) NOT NULL PRIMARY KEY,
  version VARCHAR(64) NOT NULL,
  archive_sha256 CHAR(64) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  nav_label VARCHAR(48) NOT NULL,
  nav_entry VARCHAR(180) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  installed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT ck_sibyl_addon_kind CHECK (kind IN ('feature','dependency'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sibyl_addon_install_dependencies (
  addon_id VARCHAR(120) NOT NULL,
  requires_id VARCHAR(120) NOT NULL,
  requires_version VARCHAR(64) NOT NULL,
  PRIMARY KEY (addon_id,requires_id),
  INDEX idx_sibyl_addon_dep_required (requires_id),
  CONSTRAINT fk_sibyl_addon_dep_parent FOREIGN KEY (addon_id)
    REFERENCES sibyl_addon_installs(addon_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
