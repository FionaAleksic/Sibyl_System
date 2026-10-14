CREATE TABLE sibyl_managed_networks (
  cidr VARCHAR(20) NOT NULL PRIMARY KEY, label VARCHAR(80) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE sibyl_managed_assets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  collector_id VARCHAR(40) NOT NULL, external_id VARCHAR(90) NOT NULL,
  hostname VARCHAR(120) NOT NULL, ipv4 VARCHAR(15) NOT NULL,
  os_name VARCHAR(100) NOT NULL, patch_state VARCHAR(16) NOT NULL,
  protection_state VARCHAR(16) NOT NULL,
  last_seen DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_asset_source(collector_id,external_id),
  INDEX idx_asset_seen(last_seen)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE sibyl_security_alerts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  asset_id BIGINT NOT NULL, category VARCHAR(40) NOT NULL,
  severity VARCHAR(10) NOT NULL, message VARCHAR(200) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_asset_alert FOREIGN KEY (asset_id) REFERENCES sibyl_managed_assets(id),
  INDEX idx_alert_status(status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE sibyl_collector_nonces (
  collector_id VARCHAR(40) NOT NULL, nonce CHAR(36) NOT NULL,
  accepted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY(collector_id,nonce)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
