// Validates Fiber's seed data: regions.json, equipment.json and exercises.json.
// Run from the repo root or from data/:  node data/validate.mjs   (or: node validate.mjs)
// Plain Node, no dependencies. Exits 1 and lists every error if anything is wrong.

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

// The JSON files live next to this script, so it works from any working directory.
const dataDir = path.dirname(fileURLToPath(import.meta.url));

// ---- Fixed lists: the agreed 26 regions, equipment ids and movement patterns ----

// The 26 agreed regions. regions.json must match this table exactly.
// Columns: id, name, group, view.
const REGIONS = [
  ["chest_upper", "Upper chest", "chest", "front"],
  ["chest_mid", "Mid chest", "chest", "front"],
  ["chest_lower", "Lower chest", "chest", "front"],
  ["delt_front", "Front delts", "shoulders", "front"],
  ["delt_side", "Side delts", "shoulders", "both"],
  ["delt_rear", "Rear delts", "shoulders", "back"],
  ["traps_upper", "Upper traps", "back", "both"],
  ["traps_mid", "Mid/lower traps", "back", "back"],
  ["lats_upper", "Upper lats", "back", "back"],
  ["lats_lower", "Lower lats", "back", "back"],
  ["lower_back", "Lower back (erectors)", "back", "back"],
  ["biceps_long", "Biceps, long head", "arms", "front"],
  ["biceps_short", "Biceps, short head", "arms", "front"],
  ["triceps_long", "Triceps, long head", "arms", "back"],
  ["triceps_lateral", "Triceps, lateral + medial heads", "arms", "back"],
  ["forearms", "Forearms", "arms", "both"],
  ["abs_upper", "Upper abs", "core", "front"],
  ["abs_lower", "Lower abs", "core", "front"],
  ["obliques", "Obliques", "core", "front"],
  ["quads_rectus", "Quads, rectus femoris", "legs", "front"],
  ["quads_vasti", "Quads, vasti (outer + inner)", "legs", "front"],
  ["adductors", "Adductors", "legs", "front"],
  ["glutes", "Glutes (max)", "legs", "back"],
  ["glute_med", "Glute med", "legs", "back"],
  ["hamstrings", "Hamstrings", "legs", "back"],
  ["calves", "Calves", "legs", "both"],
].map(([id, name, group, view]) => ({ id, name, group, view }));
const REGION_IDS = REGIONS.map((r) => r.id);

const EQUIPMENT_IDS = [
  "barbell", "dumbbells", "cables", "machines", "bench",
  "pullup_bar", "dip_station", "kettlebells", "bands",
];
const PATTERNS = [
  "horizontal_push", "vertical_push", "horizontal_pull", "vertical_pull",
  "squat", "hinge", "lunge", "knee_flexion", "knee_extension",
  "hip_abduction", "hip_adduction", "shrug", "wrist", "calf",
  "elbow_flexion", "elbow_extension", "lateral_raise", "rear_delt", "fly",
  "core_flexion", "core_rotation", "carry",
];
// Cues and notes must say "emphasis", never "isolation".
const BANNED_WORDS = /\b(isolates?|isolation|only works)\b/i;

const MIN_PRIMARY_USES = 2; // rule 7
const SNAKE_CASE = /^[a-z][a-z0-9]*(_[a-z0-9]+)*$/;

const errors = [];
const fail = (msg) => errors.push(msg);

// ---- Small type helpers ----

const isString = (v) => typeof v === "string" && v.trim().length > 0;
const isStringArray = (v) => Array.isArray(v) && v.every((x) => typeof x === "string");

function readJsonArray(fileName) {
  const file = path.join(dataDir, fileName);
  try {
    const data = JSON.parse(readFileSync(file, "utf8"));
    if (!Array.isArray(data)) {
      fail(`${fileName}: top level must be an array`);
      return [];
    }
    return data;
  } catch (err) {
    fail(`${fileName}: cannot read or parse (${err.message})`);
    return [];
  }
}

// Reports any key that isn't in the allowed list (catches typos like "primay").
function checkKeys(label, obj, allowed) {
  for (const key of Object.keys(obj)) {
    if (!allowed.includes(key)) fail(`${label}: unknown field "${key}"`);
  }
}

// Rule 5 (ids unique + snake_case), applied to any list with an id field.
function checkIds(fileName, items) {
  const seen = new Set();
  for (const [i, item] of items.entries()) {
    if (!isString(item.id)) {
      fail(`${fileName}[${i}]: "id" must be a non-empty string`);
      continue;
    }
    if (!SNAKE_CASE.test(item.id)) fail(`${fileName}: id "${item.id}" is not snake_case`);
    if (seen.has(item.id)) fail(`${fileName}: duplicate id "${item.id}"`);
    seen.add(item.id);
  }
  return seen;
}

// Checks that a file has exactly the expected ids (nothing missing, nothing extra).
function checkExactIds(fileName, actualIds, expectedIds) {
  for (const id of expectedIds) {
    if (!actualIds.has(id)) fail(`${fileName}: missing required id "${id}"`);
  }
  for (const id of actualIds) {
    if (!expectedIds.includes(id)) fail(`${fileName}: unexpected id "${id}"`);
  }
}

// ---- regions.json ----

const regions = readJsonArray("regions.json");
const regionIds = checkIds("regions.json", regions);
checkExactIds("regions.json", regionIds, REGION_IDS);

const orders = new Set();
for (const r of regions) {
  const label = `regions.json "${r.id}"`;
  checkKeys(label, r, ["id", "name", "group", "view", "order"]);

  // name, group and view must match the agreed table exactly.
  const expected = REGIONS.find((x) => x.id === r.id);
  if (expected) {
    for (const field of ["name", "group", "view"]) {
      if (r[field] !== expected[field]) {
        fail(`${label}: "${field}" is ${JSON.stringify(r[field])}, expected "${expected[field]}"`);
      }
    }
  }

  // order sets the display order, so two regions can't share a value.
  if (!Number.isInteger(r.order)) {
    fail(`${label}: "order" must be an integer`);
  } else {
    if (orders.has(r.order)) fail(`${label}: "order" ${r.order} is used by another region`);
    orders.add(r.order);
  }
}

// ---- equipment.json ----

const equipment = readJsonArray("equipment.json");
const equipmentIds = checkIds("equipment.json", equipment);
checkExactIds("equipment.json", equipmentIds, EQUIPMENT_IDS);

for (const eq of equipment) {
  const label = `equipment.json "${eq.id}"`;
  checkKeys(label, eq, ["id", "name"]);
  if (!isString(eq.name)) fail(`${label}: "name" must be a non-empty string`);
}

// ---- exercises.json ----

const exercises = readJsonArray("exercises.json");
checkIds("exercises.json", exercises);

const names = new Set();
const primaryCount = Object.fromEntries(REGION_IDS.map((id) => [id, 0]));

for (const [i, ex] of exercises.entries()) {
  const label = `exercises.json "${ex.id ?? `#${i}`}"`;
  checkKeys(label, ex, [
    "id", "name", "equipment", "pattern", "primary", "secondary",
    "repRange", "cue", "note", "retired",
  ]);

  // Rule 5: names are unique (case-insensitive, so "Pull-up" and "pull-up" clash).
  if (!isString(ex.name)) {
    fail(`${label}: "name" must be a non-empty string`);
  } else {
    const key = ex.name.toLowerCase();
    if (names.has(key)) fail(`${label}: duplicate name "${ex.name}"`);
    names.add(key);
  }

  // Rule 4: every equipment id exists. [] means bodyweight.
  if (!isStringArray(ex.equipment)) {
    fail(`${label}: "equipment" must be an array of ids ([] for bodyweight)`);
  } else {
    for (const id of ex.equipment) {
      if (!equipmentIds.has(id)) fail(`${label}: unknown equipment "${id}"`);
    }
    if (new Set(ex.equipment).size !== ex.equipment.length) fail(`${label}: equipment listed twice`);
  }

  if (!PATTERNS.includes(ex.pattern)) fail(`${label}: unknown pattern "${ex.pattern}"`);

  // Rules 1-3: region ids exist, 1-3 primaries, no overlap with secondary.
  const primary = isStringArray(ex.primary) ? ex.primary : null;
  const secondary = isStringArray(ex.secondary) ? ex.secondary : null;
  if (!primary) fail(`${label}: "primary" must be an array of region ids`);
  if (!secondary) fail(`${label}: "secondary" must be an array of region ids ([] if none)`);

  if (primary) {
    if (primary.length < 1 || primary.length > 3) {
      fail(`${label}: needs 1 to 3 primary regions (has ${primary.length})`);
    }
    if (new Set(primary).size !== primary.length) fail(`${label}: primary region listed twice`);
  }
  if (secondary && new Set(secondary).size !== secondary.length) {
    fail(`${label}: secondary region listed twice`);
  }
  for (const id of [...(primary ?? []), ...(secondary ?? [])]) {
    if (!regionIds.has(id)) fail(`${label}: unknown region "${id}"`);
  }
  if (primary && secondary) {
    // Loop over unique ids so a region listed twice is reported only once here.
    for (const id of new Set(primary)) {
      if (secondary.includes(id)) fail(`${label}: "${id}" is both primary and secondary`);
    }
  }

  // Rule 6: repRange is [low, high], integers, 1..30, low < high.
  const rr = ex.repRange;
  const validRange =
    Array.isArray(rr) && rr.length === 2 && rr.every(Number.isInteger) &&
    rr[0] >= 1 && rr[1] <= 30 && rr[0] < rr[1];
  if (!validRange) fail(`${label}: "repRange" must be two integers [low, high], 1-30, low < high`);

  if (!isString(ex.cue)) fail(`${label}: "cue" must be a non-empty string`);
  if (ex.note !== undefined && !isString(ex.note)) fail(`${label}: "note" must be a non-empty string`);
  if (ex.retired !== undefined && typeof ex.retired !== "boolean") {
    fail(`${label}: "retired" must be true or false`);
  }
  for (const field of ["cue", "note"]) {
    if (isString(ex[field]) && BANNED_WORDS.test(ex[field])) {
      fail(`${label}: "${field}" says "isolate"/"only works"; use emphasis wording`);
    }
  }

  // Count primaries for rule 7 (retired exercises don't count as swap options).
  if (primary && ex.retired !== true) {
    for (const id of new Set(primary)) if (id in primaryCount) primaryCount[id] += 1;
  }
}

// Keep the file sorted by id so diffs stay small and easy to review.
// Only the first out-of-order pair is reported, to keep the error list short.
for (let i = 1; i < exercises.length; i++) {
  const prev = exercises[i - 1].id;
  const curr = exercises[i].id;
  if (typeof prev === "string" && typeof curr === "string" && prev > curr) {
    fail(`exercises.json: not sorted by id ("${prev}" comes before "${curr}")`);
    break;
  }
}

// Rule 7: every region is the primary of at least 2 exercises.
for (const [id, count] of Object.entries(primaryCount)) {
  if (count < MIN_PRIMARY_USES) {
    fail(`region "${id}" is primary in ${count} exercise(s); needs at least ${MIN_PRIMARY_USES}`);
  }
}

// ---- Report ----

if (errors.length > 0) {
  console.error(`Data validation FAILED (${dataDir}) with ${errors.length} error(s):`);
  errors.forEach((msg, i) => console.error(`  ${i + 1}. ${msg}`));
  process.exit(1);
}

console.log("Data validation OK");
console.log(`  regions:   ${regions.length}`);
console.log(`  equipment: ${equipment.length}`);
console.log(`  exercises: ${exercises.length}`);
console.log("  exercises per region (as primary):");
for (const id of REGION_IDS) {
  console.log(`    ${id.padEnd(16)} ${primaryCount[id]}`);
}
