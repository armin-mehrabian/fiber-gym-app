-- Keep the order of equipment.json, so the equipment list (e.g. in onboarding) shows the items
-- in the order the file lists them. position = index in the file (0 = first).
alter table equipment add column position int not null default 0;
