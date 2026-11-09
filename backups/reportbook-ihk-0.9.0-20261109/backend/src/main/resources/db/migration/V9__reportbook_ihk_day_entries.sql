-- Report-book usability update: IHK-style multiple entries per day, comments and attachments.
-- Existing records and all previous Flyway migrations remain intact.
ALTER TABLE sibyl_report_days MODIFY COLUMN attendance VARCHAR(24) NOT NULL DEFAULT 'ANWESEND';
CREATE TABLE sibyl_report_day_items (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 week_id BIGINT NOT NULL,
 day_date DATE NOT NULL,
 ordinal SMALLINT NOT NULL,
 entry_kind VARCHAR(12) NOT NULL DEFAULT 'TEXT',
 minutes_spent INT NOT NULL DEFAULT 0,
 entry_body TEXT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_report_day_item(week_id, day_date, ordinal),
 CONSTRAINT fk_report_day_item_week FOREIGN KEY(week_id)
   REFERENCES sibyl_report_weeks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sibyl_report_day_items(week_id,day_date,ordinal,entry_kind,minutes_spent,entry_body)
SELECT week_id,day_date,0,'TEXT',minutes_spent,activity_text
FROM sibyl_report_days WHERE TRIM(activity_text)<>'' OR minutes_spent>0;

CREATE TABLE sibyl_report_day_comments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 week_id BIGINT NOT NULL,
 day_date DATE NOT NULL,
 author_username VARCHAR(100) NOT NULL,
 body VARCHAR(1500) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_report_comments(week_id,day_date),
 CONSTRAINT fk_report_comment_week FOREIGN KEY(week_id)
   REFERENCES sibyl_report_weeks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sibyl_report_day_files (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 week_id BIGINT NOT NULL,
 day_date DATE NOT NULL,
 uploaded_by VARCHAR(100) NOT NULL,
 file_name VARCHAR(255) NOT NULL,
 mime_type VARCHAR(120) NOT NULL,
 byte_size INT NOT NULL,
 file_data LONGBLOB NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_report_files(week_id,day_date),
 CONSTRAINT fk_report_file_week FOREIGN KEY(week_id)
   REFERENCES sibyl_report_weeks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
