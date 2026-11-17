| endpoint | path | SQL statements / request | DB time / request (ms) |
|---|---|---:|---:|
| client_dashboard | /api/dashboard | 60.0 | 0.59 |
| workout_today | /api/workouts/today | 33.0 | 0.29 |
| workout_week | /api/workouts/week | 35.0 | 0.19 |
| workout_history | /api/workouts | 82.0 | 0.72 |
| weight_dashboard | /api/weight/dashboard | 3.0 | 0.05 |
| exercises | /api/exercises?pageSize=100 | 1.0 | 0.03 |
| coach_dashboard | /api/coach/dashboard | 415.0 | 2.64 |
| coach_clients | /api/clients | 394.0 | 2.37 |
| coach_client_workouts | /api/clients/{id}/workouts | 83.0 | 0.79 |
| coach_conversations | /api/coach/conversations | 16.0 | 0.06 |
