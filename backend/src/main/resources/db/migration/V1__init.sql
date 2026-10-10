-- Fiber initial schema (PostgreSQL 16).
-- Reference data (region, equipment, exercise, ...) is seeded from data/*.json at startup.
-- User data uses uuid ids; gen_random_uuid() is built into PostgreSQL 13+.
-- Weights are always stored in kg. Timestamps are timestamptz (UTC).

-- ===================== Reference data =====================

-- A trainable muscle region, e.g. lats_upper. Ids match data/regions.json and the frontend.
create table region (
    id           text primary key,
    name         text not null,
    muscle_group text not null check (muscle_group in ('chest', 'shoulders', 'back', 'arms', 'core', 'legs')),
    -- Which side of the body figure shows it.
    view         text not null check (view in ('front', 'back', 'both')),
    sort_order   int  not null
);

-- A piece of gym equipment, e.g. cable or dumbbell.
create table equipment (
    id   text primary key,
    name text not null
);

-- An exercise from the library. Retired exercises stay (old sessions point to them) but are flagged.
create table exercise (
    id       text primary key,
    name     text    not null,
    pattern  text    not null,
    rep_low  int     not null,
    rep_high int     not null,
    cue      text,
    note     text,
    retired  boolean not null default false,
    check (rep_low <= rep_high)
);

-- Which equipment an exercise needs.
create table exercise_equipment (
    exercise_id  text not null references exercise (id),
    equipment_id text not null references equipment (id),
    primary key (exercise_id, equipment_id)
);

-- Which regions an exercise emphasises. PRIMARY counts as 1 set, SECONDARY as 0.5.
create table exercise_region (
    exercise_id text not null references exercise (id),
    region_id   text not null references region (id),
    role        text not null check (role in ('PRIMARY', 'SECONDARY')),
    primary key (exercise_id, region_id)
);

-- ===================== User data =====================
-- Deleting a user deletes all of their data (on delete cascade).

-- A login. Email is unique ignoring case (see the index below), without the citext extension.
create table user_account (
    id            uuid primary key default gen_random_uuid(),
    email         text        not null,
    password_hash text        not null,
    created_at    timestamptz not null default now()
);

create unique index user_account_email_lower_key on user_account (lower(email));

-- One row per user. split_type stays null until the user picks a split in onboarding
-- (PPL = push/pull/legs, UL = upper/lower, FB = full body, CUSTOM = user-defined days).
create table user_settings (
    user_id    uuid primary key references user_account (id) on delete cascade,
    split_type text check (split_type in ('PPL', 'UL', 'FB', 'CUSTOM')),
    units      text    not null default 'KG' check (units in ('KG', 'LB')),
    timezone   text    not null,
    onboarded  boolean not null default false
);

-- The equipment a user has access to.
create table user_equipment (
    user_id      uuid not null references user_account (id) on delete cascade,
    equipment_id text not null references equipment (id),
    primary key (user_id, equipment_id)
);

-- Weekly set target per region. Rows are created at signup with defaults; the user can edit them.
create table region_target (
    user_id     uuid not null references user_account (id) on delete cascade,
    region_id   text not null references region (id),
    weekly_sets int  not null check (weekly_sets >= 0),
    primary key (user_id, region_id)
);

-- One day of the user's split, e.g. PUSH / PULL / LEGS, in rotation order (position).
create table split_day (
    id       uuid primary key default gen_random_uuid(),
    user_id  uuid not null references user_account (id) on delete cascade,
    position int  not null,
    name     text not null,
    unique (user_id, position)
);

-- The regions a split day trains.
create table split_day_region (
    split_day_id uuid not null references split_day (id) on delete cascade,
    region_id    text not null references region (id),
    primary key (split_day_id, region_id)
);

-- One workout. "Finish" sets FINISHED; "Discard" deletes the row, so there is no abandoned state.
-- If a split day is deleted later, the workout history stays (split_day_id becomes null).
create table workout_session (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid        not null references user_account (id) on delete cascade,
    split_day_id uuid        references split_day (id) on delete set null,
    name         text        not null,
    status       text        not null default 'ACTIVE' check (status in ('ACTIVE', 'FINISHED')),
    started_at   timestamptz not null default now(),
    finished_at  timestamptz
);

create index workout_session_user_started_idx on workout_session (user_id, started_at);

-- An exercise inside a workout, in order (position), with the plan for it.
create table session_exercise (
    id                  uuid primary key default gen_random_uuid(),
    session_id          uuid not null references workout_session (id) on delete cascade,
    position            int  not null,
    exercise_id         text not null references exercise (id),
    target_sets         int  not null check (target_sets > 0),
    rep_low             int  not null,
    rep_high            int  not null,
    suggested_weight_kg numeric(6, 2),
    check (rep_low <= rep_high),
    -- Also serves as the index for "all exercises of a session" (session_id comes first).
    unique (session_id, position)
);

-- One logged set. Bodyweight exercises use weight_kg = 0. Warm-ups never count towards coverage.
create table workout_set (
    id                  uuid primary key default gen_random_uuid(),
    session_exercise_id uuid          not null references session_exercise (id) on delete cascade,
    set_number          int           not null,
    weight_kg           numeric(6, 2) not null check (weight_kg >= 0),
    reps                int           not null check (reps >= 0),
    is_warmup           boolean       not null default false,
    completed           boolean       not null default false,
    logged_at           timestamptz   not null default now()
);

create index workout_set_session_exercise_idx on workout_set (session_exercise_id);
