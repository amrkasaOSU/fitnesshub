| endpoint | path | SQL statements / request | DB time / request (ms) |
|---|---|---:|---:|
| client_dashboard | /api/dashboard | 31.0 | 0.45 |
| workout_today | /api/workouts/today | 9.0 | 0.25 |
| workout_week | /api/workouts/week | 5.0 | 0.09 |
| workout_history | /api/workouts | 6.0 | 0.59 |
| weight_dashboard | /api/weight/dashboard | 3.0 | 0.10 |
| exercises | /api/exercises?pageSize=100 | 0.0 | 0.00 |
| coach_dashboard | /api/coach/dashboard | 18.0 | 0.93 |
| coach_clients | /api/clients | 15.0 | 0.85 |
| coach_client_workouts | /api/clients/{id}/workouts | 7.0 | 0.46 |
| coach_conversations | /api/coach/conversations | 4.0 | 0.05 |
