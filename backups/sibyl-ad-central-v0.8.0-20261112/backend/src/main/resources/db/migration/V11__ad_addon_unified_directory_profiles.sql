-- AD-addon boundary: configurable per-directory login and service-account
-- synchronization, with backward-compatible defaults for existing profiles.
ALTER TABLE sibyl_ad_connections
  ADD COLUMN login_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN sync_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN netbios_name VARCHAR(15) NULL;

-- Migrate the original report-book AD group settings into Sibyl.ad.
-- Retain the old keys for rollback; the report-book module no longer
-- reads or displays them.
INSERT IGNORE INTO sibyl_addon_settings(addon_id,setting_key,setting_value)
SELECT 'Sibyl.ad','reportBookTraineeGroups',setting_value
FROM sibyl_addon_settings
WHERE addon_id='Sibyl.report-book' AND setting_key='traineeGroupDns'
;

INSERT IGNORE INTO sibyl_addon_settings(addon_id,setting_key,setting_value)
SELECT 'Sibyl.ad','reportBookTrainerGroups',setting_value
FROM sibyl_addon_settings
WHERE addon_id='Sibyl.report-book' AND setting_key='trainerGroupDns'
;
