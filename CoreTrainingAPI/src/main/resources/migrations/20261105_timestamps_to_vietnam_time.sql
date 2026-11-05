-- Converts stored timestamps from UTC wall clock to Vietnam wall clock, to match the backend now pinned to
-- Asia/Ho_Chi_Minh. Run once, with the backend stopped, only on environments whose backend ran in UTC (Docker image
-- without TZ). Do not run where the backend already ran in Vietnam time: those rows are already correct.
-- notifications.created_at stays UTC on purpose, and timestamptz columns already carry their zone.
-- A marker row makes a second run a no-op; everything runs in one transaction, so a failure changes nothing.
BEGIN;
CREATE TABLE IF NOT EXISTS app_data_migrations (
    name varchar(120) PRIMARY KEY,
    applied_at timestamp with time zone NOT NULL DEFAULT now()
);
DO $$
DECLARE
    col record;
    changed bigint;
BEGIN
    IF EXISTS (SELECT 1 FROM app_data_migrations WHERE name = '20261105_timestamps_to_vietnam_time') THEN
        RAISE NOTICE 'Already applied, nothing changed';
        RETURN;
    END IF;
    INSERT INTO app_data_migrations (name) VALUES ('20261105_timestamps_to_vietnam_time');
    FOR col IN
        SELECT c.table_name, c.column_name
        FROM information_schema.columns c
        JOIN information_schema.tables t USING (table_schema, table_name)
        WHERE c.table_schema = 'public' AND t.table_type = 'BASE TABLE'
          AND c.data_type = 'timestamp without time zone'
          AND NOT (c.table_name = 'notifications' AND c.column_name = 'created_at')
        ORDER BY 1, 2
    LOOP
        EXECUTE format('UPDATE public.%I SET %I = %I + interval ''7 hours'' WHERE %I IS NOT NULL',
            col.table_name, col.column_name, col.column_name, col.column_name);
        GET DIAGNOSTICS changed = ROW_COUNT;
        RAISE NOTICE '%.%: % rows', col.table_name, col.column_name, changed;
    END LOOP;
END $$;
COMMIT;
