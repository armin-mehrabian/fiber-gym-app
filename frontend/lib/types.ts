// Types that mirror the backend API contract (JSON, camelCase).

// GET /api/health
export type Health = {
  status: "ok" | "degraded";
  database: "up" | "down";
};

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
  suggestedWeightKg: number | null;
  sets: WorkoutSet[];
};

export type SessionStatus = "ACTIVE" | "FINISHED";

// A workout: the one in progress (ACTIVE) or a past one (FINISHED).
export type Session = {
  id: string;
  name: string;
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

// A failed request: the backend's { error, fields } reply plus the HTTP status.
// status 0 means the backend couldn't be reached at all.
export type ApiError = {
  status: number;
  error: string;
  fields: Record<string, string>; // per-field messages, e.g. { email: "already used" }
};

// What every browser call returns: the data, or an error to show.
export type ApiResult<T> = { ok: true; data: T } | { ok: false; error: ApiError };
