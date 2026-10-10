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

// A failed request: the backend's { error, fields } reply plus the HTTP status.
// status 0 means the backend couldn't be reached at all.
export type ApiError = {
  status: number;
  error: string;
  fields: Record<string, string>; // per-field messages, e.g. { email: "already used" }
};

// What every browser call returns: the data, or an error to show.
export type ApiResult<T> = { ok: true; data: T } | { ok: false; error: ApiError };
