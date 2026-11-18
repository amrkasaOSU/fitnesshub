-- Indexes shaped like the queries that actually run, chosen from EXPLAIN
-- ANALYZE against a 200-client / one-year dataset (see RESULTS.md, section 3).
--
-- The original schema indexed each foreign key on its own. That finds a
-- client's rows, but then PostgreSQL still has to sort them by date or filter
-- them by status in memory. Almost every hot query here is "this client's
-- rows, newest first" or "this user's unread rows", so each index below leads
-- with the owner and continues with the column the query orders or filters on.
--
-- Single-column indexes that are now a strict prefix of a composite are
-- dropped: they'd cost a write on every insert and never be chosen.
--
-- IF [NOT] EXISTS throughout so the migration is safe to re-run against a
-- database where an index was created by hand while investigating.
-- Plain CREATE INDEX rather than CONCURRENTLY, because Flyway runs a
-- migration inside a transaction; on tables this size the lock is momentary.

-- Notifications: the unread badge is polled every 30 seconds by every open
-- tab, which makes it the most frequently executed query in the system. A
-- partial index holds only unread rows, so the count never visits the (much
-- larger, ever-growing) pile of read ones.
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
    ON notifications (user_id) WHERE read_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications (user_id, created_at DESC);
DROP INDEX IF EXISTS idx_notifications_user_id;

-- Workout sessions: history pages, "last 7 days" windows, and "most recent
-- completed session" all read one client's sessions in start-time order.
CREATE INDEX IF NOT EXISTS idx_workout_sessions_client_started
    ON workout_sessions (client_id, started_at DESC);
DROP INDEX IF EXISTS idx_workout_sessions_client_id;

-- Weigh-ins: latest entry and recent-window reads, per client.
CREATE INDEX IF NOT EXISTS idx_weight_entries_client_recorded
    ON weight_entries (client_id, recorded_at DESC);
DROP INDEX IF EXISTS idx_weight_entries_client_id;

-- Personal records: "newest N for these clients".
CREATE INDEX IF NOT EXISTS idx_personal_records_client_achieved
    ON personal_records (client_id, achieved_at DESC);
DROP INDEX IF EXISTS idx_personal_records_client_id;

-- Messages: newest message per conversation, and unread counts.
CREATE INDEX IF NOT EXISTS idx_messages_conversation_created
    ON messages (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_unread
    ON messages (conversation_id, sender_id) WHERE read_at IS NULL;
DROP INDEX IF EXISTS idx_messages_conversation_id;

-- Check-ins waiting on the coach: only unreviewed rows are ever counted.
CREATE INDEX IF NOT EXISTS idx_check_ins_client_unreviewed
    ON check_ins (client_id) WHERE coach_reviewed_at IS NULL;

-- A client's active program assignment is looked up on every dashboard,
-- workout and adherence calculation.
CREATE INDEX IF NOT EXISTS idx_client_programs_client_status
    ON client_programs (client_id, status, start_date DESC);
DROP INDEX IF EXISTS idx_client_programs_client_id;
