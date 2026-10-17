-- Built-in Sibyl addon providers: no arbitrary registration or dynamic code execution.
INSERT INTO sibyl_addons(addon_id,name,version,description,module_type,document_mode,enabled,allow_users) VALUES
('Sibyl.doc','Wiki / Dokumentation','0.7.0','Gemeinsame Wissensartikel, durch Administratoren bearbeitbar.','documents','shared',FALSE,TRUE),
('Sibyl.planning','Planung','0.7.0','Dienstplanung mit drei unabhängigen fairen Rotationen.','planning','personal',FALSE,TRUE),
('Sibyl.report-book','Berichtsheft','0.7.0','Ausbildungsnachweise mit Einreichung und Freigabe.','report-book','personal',FALSE,TRUE);
INSERT INTO sibyl_addon_dependencies(addon_id,requires_addon_id) VALUES
('Sibyl.planning','Sibyl.ad'),('Sibyl.report-book','Sibyl.ad');

CREATE TABLE sibyl_planning_participants(
  username VARCHAR(100) NOT NULL PRIMARY KEY,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE sibyl_planning_slots(
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 slot_date DATE NOT NULL,
 slot_kind VARCHAR(16) NOT NULL,
 assigned_username VARCHAR(100) NOT NULL,
 created_by VARCHAR(100) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_sibyl_plan_date_kind(slot_date,slot_kind),
 INDEX idx_sibyl_plan_rotation(slot_kind,assigned_username,slot_date),
 CONSTRAINT fk_sibyl_plan_participant FOREIGN KEY(assigned_username) REFERENCES sibyl_planning_participants(username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE sibyl_report_entries(
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 owner_username VARCHAR(100) NOT NULL,
 entry_date DATE NOT NULL,
 title VARCHAR(160) NOT NULL,
 description TEXT NOT NULL,
 minutes_spent INT NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
 reviewer VARCHAR(100),
 review_feedback VARCHAR(1000),
 reviewed_at TIMESTAMP NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_sibyl_reports_owner_date(owner_username,entry_date),
 INDEX idx_sibyl_reports_status(status,entry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
