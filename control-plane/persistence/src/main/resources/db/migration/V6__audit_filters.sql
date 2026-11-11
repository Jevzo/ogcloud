-- The dashboard filters the audit log by target and by action (D-0213); actors and times have indexes since V1.
create index audit_log_target on audit_log (target_id);
create index audit_log_action on audit_log (action);
