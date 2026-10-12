// Types that mirror the backend API contract (JSON, camelCase).

// GET /api/me, and the reply of signup and login.
export type User = {
  id: string;
  email: string;
  onboarded: boolean;
};

// CUSTOM exists in the API, but the v1 screens only offer the three presets.
export type SplitType = "PPL" | "UL" | "FB" | "CUSTOM";
export type Units = "KG" | "LB";

export type SplitDay = {
  id: string;
  position: number; // starts at 1
  name: string;
  regions: string[]; // region ids
};

// GET /api/settings and the reply of PUT /api/settings.
export type Settings = {
  splitType: SplitType | null; // null until onboarding is finished
  units: Units;
  timezone: string; // IANA zone, e.g. "America/Vancouver"
  onboarded: boolean;
  equipment: string[]; // equipment ids
  splitDays: SplitDay[];
};

// Body of PUT /api/settings. For the presets (PPL, UL, FB) the server builds the split days
// itself and ignores splitDays; only a CUSTOM split needs them.
export type SettingsUpdate = {
  splitType: SplitType;
  units: Units;
  timezone: string;
  equipment: string[];
  splitDays?: { name: string; regions: string[] }[];
};

// One row of GET/PUT /api/targets: how many sets per week a region should get.
export type Target = {
  regionId: string;
  weeklySets: number; // 0–30
};

// GET /api/regions
export type Region = {
  id: string;
  name: string;
  group: string; // muscle group, e.g. "chest"
  view: "front" | "back" | "both";
  order: number;
};

// GET /api/equipment
export type Equipment = {
  id: string;
  name: string;
};

// GET /api/exercises (and ?mine=true: only exercises the user's equipment allows).
export type Exercise = {
  id: string;
  name: string;
  equipment: string[]; // equipment ids; empty = bodyweight
  pattern: string;
  primary: string[]; // region ids, main region first
  secondary: string[]; // region ids
  repRange: [number, number]; // [low, high]
  cue: string;
};

// Why a weight/reps suggestion was made (the backend's progression rule):
// NO_HISTORY = first time, INCREASE = every set hit the top of the range,
// REPEAT = same weight, one more rep, MORE_REPS = bodyweight, one more rep.
export type SuggestionReason = "NO_HISTORY" | "INCREASE" | "REPEAT" | "MORE_REPS";

// One completed working set from an earlier workout.
export type PastSet = {
  weightKg: number;
  reps: number;
};

// The completed working sets of the last finished workout with an exercise.
export type LastTime = {
  date: string;
  sets: PastSet[];
};

// One logged set. Weights are always kg in the API.
export type WorkoutSet = {
  id: string;
  setNumber: number; // 1..n within its exercise
  weightKg: number; // 0 = bodyweight
  reps: number;
  isWarmup: boolean;
  completed: boolean;
};

// One exercise inside a workout.
export type SessionExercise = {
  position: number; // starts at 1
  exerciseId: string;
  name: string;
  primary: string[]; // region ids, main region first
  secondary: string[];
  targetSets: number;
  repRange: [number, number];
  suggestedWeightKg: number | null; // null = no suggestion (e.g. first time)
  suggestedReps: number | null;
  suggestionReason: SuggestionReason | null; // null for exercises saved before suggestions existed
  lastTime: LastTime | null; // only filled while the workout is in progress
  sets: WorkoutSet[];
};

export type SessionStatus = "ACTIVE" | "FINISHED";

// A workout: the one in progress (ACTIVE) or a past one (FINISHED).
export type Session = {
  id: string;
  name: string;
  splitDayId: string | null; // null for an empty workout started without a split day
  status: SessionStatus;
  startedAt: string; // ISO timestamp
  finishedAt: string | null;
  exercises: SessionExercise[];
};

// Body of POST .../sets and the fields PATCH /api/sets/{id} may change.
export type SetValues = {
  weightKg: number;
  reps: number;
  isWarmup: boolean;
};
export type SetChanges = Partial<SetValues & { completed: boolean }>;

// One finished workout in GET /api/history.
export type HistorySession = {
  id: string;
  name: string;
  startedAt: string;
  finishedAt: string;
  exerciseCount: number;
  workingSets: number; // completed sets that aren't warm-ups
};

// One Monday–Sunday week of GET /api/history.
export type HistoryWeek = {
  weekStart: string; // "YYYY-MM-DD", a Monday
  sessions: HistorySession[];
};

// One (exercise, day) behind a region's weekly sets in GET /api/coverage/week.
export type Contribution = {
  exerciseName: string;
  day: string; // short weekday in the user's timezone: "Mon" … "Sun"
  sets: number; // effective sets it gave, e.g. 3 or 1.5
};

// Where a region stands this week, in words instead of numbers:
// NOT_YET = nothing yet, STARTED = some sets but below its target, DONE = target reached
// (a target of 0 counts as DONE, because nothing more is wanted).
export type RegionState = "NOT_YET" | "STARTED" | "DONE";

// One region's week: effective sets (primary = 1 per set, secondary = 0.5) against its target.
export type RegionCoverage = {
  regionId: string;
  effective: number; // at most 1 decimal, e.g. 4.5
  target: number; // the user's weekly target
  coverage: number; // 0..1 (0 when the target is 0)
  state: RegionState;
  recovering: boolean; // trained hard (3+ effective sets in one workout) in the last 48 hours
  contributions: Contribution[]; // sorted by day, then exercise name; empty when untrained
};

// One day of the week and how many finished workouts started on it.
export type CoverageDay = {
  date: string; // "YYYY-MM-DD"
  sessions: number;
};

// One muscle group's badge: done once every region of the group is DONE this week.
export type Badge = {
  group: string; // e.g. "chest"
  name: string; // e.g. "Chest"
  done: boolean;
  regionsDone: number;
  regionsTotal: number;
};

// GET /api/coverage/week: one Monday–Sunday week in the user's timezone.
export type WeekCoverage = {
  weekStart: string; // "YYYY-MM-DD", a Monday
  weekEnd: string; // "YYYY-MM-DD", the Sunday
  weekPercent: number; // whole number 0–100 (still sent, no longer shown)
  days: CoverageDay[]; // 7 entries, Monday first
  regions: RegionCoverage[]; // all regions, in region order
  badges: Badge[]; // one per group: chest, shoulders, back, arms, core, legs
};

// One exercise of GET /api/recommendation/next.
export type RecommendedExercise = {
  exerciseId: string;
  name: string;
  primary: string[];
  secondary: string[];
  sets: number;
  repRange: [number, number];
  suggestedWeightKg: number | null;
  suggestedReps: number | null;
  suggestionReason: SuggestionReason | null;
};

// GET /api/recommendation/next: the next split day and the exercises that fill this week's gaps.
export type Recommendation = {
  splitDayId: string;
  name: string; // e.g. "Pull"
  exercises: RecommendedExercise[]; // empty when the week is already covered
  planned: Record<string, number>; // region id → 1 (main region) or 0.5 (helping region)
};

// One option of GET /api/recommendation/alternatives, best match first.
export type Alternative = {
  exerciseId: string;
  name: string;
  primary: string[];
  secondary: string[];
  similarity: number; // 0..1, not shown on screen
};

// The user's best set ever for an exercise (highest estimated 1RM).
export type BestSet = {
  weightKg: number;
  reps: number;
  date: string;
};

// GET /api/exercises/{id}: the catalog entry plus the user's best and last time (null for visitors).
export type ExerciseDetail = Exercise & {
  note: string | null;
  best: BestSet | null;
  last: LastTime | null;
};

// One finished workout in GET /api/exercises/{id}/history (newest first).
export type ExerciseHistoryEntry = {
  sessionId: string;
  date: string;
  sets: (PastSet & { isWarmup: boolean })[];
};

// One personal best in a workout summary.
export type PersonalBest = {
  exerciseId: string;
  name: string;
  weightKg: number;
  reps: number;
};

// GET /api/sessions/{id}/summary: what a finished workout did.
export type SessionSummary = {
  sessionId: string;
  name: string;
  durationMinutes: number;
  workingSets: number;
  regionsHit: Record<string, number>; // region id → 1 or 0.5
  weekPercentBefore: number; // still sent, no longer shown
  weekPercentAfter: number;
  personalBests: PersonalBest[]; // empty when there were none
  regionsLit: string[]; // regions this workout took from not done to DONE, in region order
  badgesEarned: string[]; // groups whose badge this workout completed, e.g. ["chest"]
};

// Body of POST .../exercises/{position}/complete: log `sets` finished working sets in one go.
export type CompleteValues = {
  weightKg: number;
  reps: number;
  sets: number; // 1–10
};

// A failed request: the backend's { error, fields } reply plus the HTTP status.
// status 0 means the backend couldn't be reached at all.
export type ApiError = {
  status: number;
  error: string;
  fields: Record<string, string>; // per-field messages, e.g. { email: "already used" }
};

// What every browser call returns: the data, or an error to show.
export type ApiResult<T> = { ok: true; data: T } | { ok: false; error: ApiError };
