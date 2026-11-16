-- Report-book v0.10: non-destructive trainee metadata and many-to-many trainer assignments.
-- Existing trainee-to-trainer rows remain in place when the primary key changes.
ALTER TABLE sibyl_report_profiles
 ADD COLUMN learning_year TINYINT UNSIGNED NULL,
 ADD CONSTRAINT chk_sibyl_report_learning_year CHECK (learning_year IS NULL OR learning_year BETWEEN 1 AND 4);

ALTER TABLE sibyl_report_assignments
 DROP PRIMARY KEY,
 ADD PRIMARY KEY (trainee_username, trainer_username);

CREATE TABLE sibyl_report_profile_groups (
 username VARCHAR(100) NOT NULL,
 group_name VARCHAR(100) NOT NULL,
 source VARCHAR(12) NOT NULL DEFAULT 'AD',
 PRIMARY KEY (username,group_name),
 INDEX idx_sibyl_report_group_name(group_name),
 CONSTRAINT fk_sibyl_report_group_user FOREIGN KEY (username)
   REFERENCES sibyl_report_profiles(username) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
