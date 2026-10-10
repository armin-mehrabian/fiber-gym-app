-- A user can have at most one ACTIVE workout at a time. The service checks this first and answers 409;
-- this index makes sure two requests arriving at the same moment can't both create one.
create unique index workout_session_one_active_per_user on workout_session (user_id) where status = 'ACTIVE';
