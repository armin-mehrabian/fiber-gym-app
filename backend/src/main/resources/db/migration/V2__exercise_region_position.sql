-- Keep the order of an exercise's regions from exercises.json.
-- position = index within the exercise's primary list, or within its secondary list (0 = first).
-- first primary = the exercise's main region (used by the recommender)
alter table exercise_region add column position int not null default 0;
