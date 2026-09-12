-- The exercise library is reference data, not demo data: a coach cannot build a
-- program without it, so a production deploy that skips seeding was left with an
-- empty exercise dropdown and no way to create a program at all. Moving it here
-- means every environment gets it, including one with seeding disabled.
--
-- SeedDataRunner no longer creates these; it looks up what this migration
-- inserted. Coach-created custom exercises set is_system_exercise = FALSE and
-- are unaffected.
INSERT INTO exercises (name, description, muscle_group, equipment, movement_pattern, is_system_exercise)
VALUES
  ('Bench Press',             'Standard Bench Press performed with controlled tempo and full range of motion.',             'CHEST',      'BARBELL',    'PUSH_HORIZONTAL', TRUE),
  ('Incline Dumbbell Press',  'Standard Incline Dumbbell Press performed with controlled tempo and full range of motion.',  'CHEST',      'DUMBBELL',   'PUSH_HORIZONTAL', TRUE),
  ('Cable Fly',               'Standard Cable Fly performed with controlled tempo and full range of motion.',               'CHEST',      'CABLE',      'ISOLATION',       TRUE),
  ('Push-up',                 'Standard Push-up performed with controlled tempo and full range of motion.',                 'CHEST',      'BODYWEIGHT', 'PUSH_HORIZONTAL', TRUE),
  ('Barbell Row',             'Standard Barbell Row performed with controlled tempo and full range of motion.',             'BACK',       'BARBELL',    'PULL_HORIZONTAL', TRUE),
  ('Seated Cable Row',        'Standard Seated Cable Row performed with controlled tempo and full range of motion.',        'BACK',       'CABLE',      'PULL_HORIZONTAL', TRUE),
  ('Lat Pulldown',            'Standard Lat Pulldown performed with controlled tempo and full range of motion.',            'BACK',       'CABLE',      'PULL_VERTICAL',   TRUE),
  ('Pull-up',                 'Standard Pull-up performed with controlled tempo and full range of motion.',                 'BACK',       'BODYWEIGHT', 'PULL_VERTICAL',   TRUE),
  ('Overhead Press',          'Standard Overhead Press performed with controlled tempo and full range of motion.',          'SHOULDERS',  'BARBELL',    'PUSH_VERTICAL',   TRUE),
  ('Lateral Raise',           'Standard Lateral Raise performed with controlled tempo and full range of motion.',           'SHOULDERS',  'DUMBBELL',   'ISOLATION',       TRUE),
  ('Face Pull',               'Standard Face Pull performed with controlled tempo and full range of motion.',               'SHOULDERS',  'CABLE',      'ISOLATION',       TRUE),
  ('Bicep Curl',              'Standard Bicep Curl performed with controlled tempo and full range of motion.',              'BICEPS',     'DUMBBELL',   'ISOLATION',       TRUE),
  ('Tricep Pushdown',         'Standard Tricep Pushdown performed with controlled tempo and full range of motion.',         'TRICEPS',    'CABLE',      'ISOLATION',       TRUE),
  ('Squat',                   'Standard Squat performed with controlled tempo and full range of motion.',                   'QUADS',      'BARBELL',    'SQUAT',           TRUE),
  ('Leg Press',               'Standard Leg Press performed with controlled tempo and full range of motion.',               'QUADS',      'MACHINE',    'SQUAT',           TRUE),
  ('Leg Extension',           'Standard Leg Extension performed with controlled tempo and full range of motion.',           'QUADS',      'MACHINE',    'ISOLATION',       TRUE),
  ('Walking Lunge',           'Standard Walking Lunge performed with controlled tempo and full range of motion.',           'QUADS',      'DUMBBELL',   'LUNGE',           TRUE),
  ('Romanian Deadlift',       'Standard Romanian Deadlift performed with controlled tempo and full range of motion.',       'HAMSTRINGS', 'BARBELL',    'HINGE',           TRUE),
  ('Leg Curl',                'Standard Leg Curl performed with controlled tempo and full range of motion.',                'HAMSTRINGS', 'MACHINE',    'ISOLATION',       TRUE),
  ('Deadlift',                'Standard Deadlift performed with controlled tempo and full range of motion.',                'BACK',       'BARBELL',    'HINGE',           TRUE),
  ('Hip Thrust',              'Standard Hip Thrust performed with controlled tempo and full range of motion.',              'GLUTES',     'BARBELL',    'HINGE',           TRUE),
  ('Calf Raise',              'Standard Calf Raise performed with controlled tempo and full range of motion.',              'CALVES',     'MACHINE',    'ISOLATION',       TRUE),
  ('Plank',                   'Standard Plank performed with controlled tempo and full range of motion.',                   'CORE',       'BODYWEIGHT', 'ISOLATION',       TRUE),
  ('Kettlebell Swing',        'Standard Kettlebell Swing performed with controlled tempo and full range of motion.',        'FULL_BODY',  'KETTLEBELL', 'HINGE',           TRUE);
