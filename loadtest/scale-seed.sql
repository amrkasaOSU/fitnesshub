-- Synthetic volume for query-plan analysis. NOT demo data and never run in
-- production: it exists because the five seeded clients are too small for
-- PostgreSQL to choose anything but a sequential scan, so an index can't be
-- shown to matter (or not) against them.
--
--   docker exec -i fitnesshub-loadtest-postgres-1 \
--     psql -U fitnesshub -d fitnesshub -v clients=200 -v days=365 < loadtest/scale-seed.sql
--
-- Adds one coach (scale-coach@fitnesshub.local, demo password) with :clients
-- clients, each carrying :days days of history on a copy of the seeded
-- strength program. Row counts are printed at the end.
\set ON_ERROR_STOP on
BEGIN;

INSERT INTO users (email, password_hash, first_name, last_name, role, email_verified)
SELECT 'scale-coach@fitnesshub.local', password_hash, 'Scale', 'Coach', 'COACH', TRUE
FROM users WHERE email = 'coach@fitnesshub.local';

INSERT INTO coach_profiles (user_id)
SELECT id FROM users WHERE email = 'scale-coach@fitnesshub.local';

INSERT INTO users (email, password_hash, first_name, last_name, role, email_verified)
SELECT 'scale-client' || g || '@fitnesshub.local',
       (SELECT password_hash FROM users WHERE email = 'coach@fitnesshub.local'),
       'Client', 'Number' || g, 'CLIENT', TRUE
FROM generate_series(1, :clients) g;

CREATE TEMP TABLE scale_clients ON COMMIT DROP AS
SELECT id AS client_id, row_number() OVER (ORDER BY email) AS n
FROM users WHERE email LIKE 'scale-client%';

CREATE TEMP TABLE scale_coach ON COMMIT DROP AS
SELECT id AS coach_id FROM users WHERE email = 'scale-coach@fitnesshub.local';

INSERT INTO client_profiles (user_id, coach_id, date_of_birth, height_cm, sex, fitness_goal,
                             starting_weight, target_weight, daily_calorie_target, daily_protein_target)
SELECT c.client_id, k.coach_id, CURRENT_DATE - INTERVAL '30 years', 175, 'MALE', 'STRENGTH',
       200, 190, 2400, 160
FROM scale_clients c, scale_coach k;

INSERT INTO coach_clients (coach_id, client_id)
SELECT k.coach_id, c.client_id FROM scale_clients c, scale_coach k;

INSERT INTO conversations (coach_id, client_id)
SELECT k.coach_id, c.client_id FROM scale_clients c, scale_coach k;

-- A private copy of the seeded 5-day strength cycle (4 training days + rest).
WITH src AS (SELECT id FROM programs WHERE name = '12-Week Strength Program' LIMIT 1),
     newp AS (
         INSERT INTO programs (coach_id, name, duration_weeks, goal)
         SELECT coach_id, 'Scale Strength Program', 52, 'STRENGTH' FROM scale_coach
         RETURNING id),
     newd AS (
         INSERT INTO program_days (program_id, day_number, name)
         SELECT newp.id, d.day_number, d.name
         FROM program_days d, newp WHERE d.program_id = (SELECT id FROM src)
         RETURNING id, day_number)
INSERT INTO program_exercises (program_day_id, exercise_id, order_index, sets, target_reps,
                               target_weight, target_rpe, rest_seconds)
SELECT nd.id, pe.exercise_id, pe.order_index, pe.sets, pe.target_reps,
       pe.target_weight, pe.target_rpe, pe.rest_seconds
FROM program_exercises pe
JOIN program_days od ON od.id = pe.program_day_id
JOIN newd nd ON nd.day_number = od.day_number
WHERE od.program_id = (SELECT id FROM src);

CREATE TEMP TABLE scale_days ON COMMIT DROP AS
SELECT d.id AS program_day_id, row_number() OVER (ORDER BY d.day_number) - 1 AS idx
FROM program_days d JOIN programs p ON p.id = d.program_id
WHERE p.name = 'Scale Strength Program';

INSERT INTO client_programs (client_id, program_id, start_date)
SELECT c.client_id, (SELECT id FROM programs WHERE name = 'Scale Strength Program'),
       CURRENT_DATE - (:days - 1)
FROM scale_clients c;

-- Workouts: every non-rest cycle day, attended ~85% of the time.
INSERT INTO workout_sessions (client_id, program_day_id, started_at, completed_at, duration_seconds, status)
SELECT c.client_id, sd.program_day_id,
       now() - make_interval(days => d) - INTERVAL '1 hour',
       now() - make_interval(days => d) - INTERVAL '10 minutes',
       3000, 'COMPLETED'
FROM scale_clients c
CROSS JOIN generate_series(1, :days - 1) d
JOIN scale_days sd ON sd.idx = ((:days - 1 - d) % 5)
WHERE EXISTS (SELECT 1 FROM program_exercises pe WHERE pe.program_day_id = sd.program_day_id)
  AND random() < 0.85;

INSERT INTO workout_exercises (workout_session_id, exercise_id, order_index)
SELECT ws.id, pe.exercise_id, pe.order_index
FROM workout_sessions ws
JOIN scale_clients c ON c.client_id = ws.client_id
JOIN program_exercises pe ON pe.program_day_id = ws.program_day_id;

INSERT INTO workout_sets (workout_exercise_id, set_number, reps, weight, rpe, completed_at)
SELECT we.id, s, 8, 100 + we.order_index * 15 + (random() * 20)::int, 7.5,
       ws.started_at + make_interval(mins => we.order_index * 10 + s * 3)
FROM workout_exercises we
JOIN workout_sessions ws ON ws.id = we.workout_session_id
JOIN scale_clients c ON c.client_id = ws.client_id
CROSS JOIN generate_series(1, 4) s;

INSERT INTO personal_records (client_id, exercise_id, record_type, value, achieved_at)
SELECT ws.client_id, we.exercise_id, 'MAX_WEIGHT', max(s.weight), max(s.completed_at)
FROM workout_sets s
JOIN workout_exercises we ON we.id = s.workout_exercise_id
JOIN workout_sessions ws ON ws.id = we.workout_session_id
JOIN scale_clients c ON c.client_id = ws.client_id
GROUP BY ws.client_id, we.exercise_id;

INSERT INTO weight_entries (client_id, weight, recorded_at)
SELECT c.client_id, round((200 - d * 0.02 + random() * 2)::numeric, 1), now() - make_interval(days => d)
FROM scale_clients c CROSS JOIN generate_series(0, :days - 1) d;

INSERT INTO step_entries (client_id, steps, date)
SELECT c.client_id, 6000 + (random() * 6000)::int, CURRENT_DATE - d
FROM scale_clients c CROSS JOIN generate_series(0, :days - 1) d;

INSERT INTO nutrition_entries (client_id, date, calories, protein_grams)
SELECT c.client_id, CURRENT_DATE - d, 2200 + (random() * 400)::int, 150 + (random() * 30)::int
FROM scale_clients c CROSS JOIN generate_series(0, :days - 1) d
WHERE random() < 0.8;

INSERT INTO check_ins (client_id, week_start_date, energy_score, sleep_score, stress_score, hunger_score,
                       workout_adherence, nutrition_adherence, submitted_at, coach_reviewed_at)
SELECT c.client_id, date_trunc('week', CURRENT_DATE)::date - w * 7, 4, 4, 2, 3, 4, 4,
       now() - make_interval(days => w * 7),
       CASE WHEN w > 0 THEN now() - make_interval(days => w * 7 - 1) END
FROM scale_clients c CROSS JOIN generate_series(0, (:days / 7) - 1) w;

INSERT INTO messages (conversation_id, sender_id, content, read_at, created_at)
SELECT cv.id, CASE WHEN m % 2 = 0 THEN cv.coach_id ELSE cv.client_id END,
       'Message number ' || m, CASE WHEN m > 2 THEN now() END, now() - make_interval(hours => m * 12)
FROM conversations cv
JOIN scale_clients c ON c.client_id = cv.client_id
CROSS JOIN generate_series(1, 60) m;

-- Notifications are the table that grows fastest: every PR, message and
-- reminder writes one, and the unread badge polls it every 30 seconds.
INSERT INTO notifications (user_id, type, title, body, read_at, created_at)
SELECT c.client_id, 'PR_ACHIEVED', 'New PR!', 'Synthetic notification ' || i,
       CASE WHEN i > 5 THEN now() END, now() - make_interval(hours => i * 6)
FROM scale_clients c CROSS JOIN generate_series(1, 300) i;

INSERT INTO notifications (user_id, type, title, body, read_at, created_at)
SELECT k.coach_id, 'PR_ACHIEVED', 'Client PR', 'Synthetic notification ' || i,
       CASE WHEN i > 40 THEN now() END, now() - make_interval(mins => i * 10)
FROM scale_coach k CROSS JOIN generate_series(1, 20000) i;

COMMIT;
ANALYZE;

SELECT relname AS table_name, n_live_tup AS approx_rows
FROM pg_stat_user_tables
WHERE relname IN ('users', 'workout_sessions', 'workout_exercises', 'workout_sets', 'weight_entries',
                  'step_entries', 'nutrition_entries', 'notifications', 'messages', 'check_ins',
                  'personal_records')
ORDER BY n_live_tup DESC;
