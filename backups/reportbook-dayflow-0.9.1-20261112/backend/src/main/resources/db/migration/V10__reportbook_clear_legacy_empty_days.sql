-- Existing drafts created by the old UI contain seven automatic
-- ANWESEND/BETRIEB rows, even where the user never entered any activity.
-- Keep all nonempty activities, attachments, comments and finalized weeks.
-- Only the empty default placeholders in editable reports are removed.
DELETE d FROM sibyl_report_days d
JOIN sibyl_report_weeks w ON w.id=d.week_id
LEFT JOIN sibyl_report_day_items i ON i.week_id=d.week_id AND i.day_date=d.day_date
LEFT JOIN sibyl_report_day_files f ON f.week_id=d.week_id AND f.day_date=d.day_date
LEFT JOIN sibyl_report_day_comments c ON c.week_id=d.week_id AND c.day_date=d.day_date
WHERE w.status IN ('DRAFT','RETURNED')
  AND d.attendance='ANWESEND' AND d.location_type='BETRIEB'
  AND d.minutes_spent=0 AND TRIM(d.activity_text)=''
  AND i.id IS NULL AND f.id IS NULL AND c.id IS NULL;
