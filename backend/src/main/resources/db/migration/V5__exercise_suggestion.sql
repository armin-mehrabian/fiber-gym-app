-- The rep target and the reason that go with suggested_weight_kg. They are worked out from the
-- user's last finished workout with that exercise when the exercise is added to a workout (or swapped in),
-- and stored so the plan doesn't change while the workout is in progress.
-- Both stay null for exercises added before suggestions existed.
alter table session_exercise
    add column suggested_reps    int check (suggested_reps >= 0),
    add column suggestion_reason text check (suggestion_reason in ('NO_HISTORY', 'INCREASE', 'REPEAT', 'MORE_REPS'));
