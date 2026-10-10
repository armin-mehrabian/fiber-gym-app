// Geometry of the body figure: plain SVG path data, no rendering code.
//
// Coordinates live in a 400-wide drawing whose centre line is x = 200.
// Only the LEFT half of the body is stored here (x <= 200). The figure
// draws it once, then draws it again mirrored around x = 200 for the right half.

export type View = "front" | "back";

// The 26 training regions, in catalog order. Must match the backend's region list
// (GET /api/regions); a unit test checks this against data/regions.json.
export const REGION_IDS = [
  "chest_upper",
  "chest_mid",
  "chest_lower",
  "delt_front",
  "delt_side",
  "delt_rear",
  "traps_upper",
  "traps_mid",
  "lats_upper",
  "lats_lower",
  "lower_back",
  "biceps_long",
  "biceps_short",
  "triceps_long",
  "triceps_lateral",
  "forearms",
  "abs_upper",
  "abs_lower",
  "obliques",
  "quads_rectus",
  "quads_vasti",
  "adductors",
  "glutes",
  "glute_med",
  "hamstrings",
  "calves",
] as const;

export type RegionId = (typeof REGION_IDS)[number];

// Display names, same as the catalog. Used as each region's accessible name.
export const REGION_NAMES: Record<RegionId, string> = {
  chest_upper: "Upper chest",
  chest_mid: "Mid chest",
  chest_lower: "Lower chest",
  delt_front: "Front delts",
  delt_side: "Side delts",
  delt_rear: "Rear delts",
  traps_upper: "Upper traps",
  traps_mid: "Mid/lower traps",
  lats_upper: "Upper lats",
  lats_lower: "Lower lats",
  lower_back: "Lower back (erectors)",
  biceps_long: "Biceps, long head",
  biceps_short: "Biceps, short head",
  triceps_long: "Triceps, long head",
  triceps_lateral: "Triceps, lateral + medial heads",
  forearms: "Forearms",
  abs_upper: "Upper abs",
  abs_lower: "Lower abs",
  obliques: "Obliques",
  quads_rectus: "Quads, rectus femoris",
  quads_vasti: "Quads, vasti (outer + inner)",
  adductors: "Adductors",
  glutes: "Glutes (max)",
  glute_med: "Glute med",
  hamstrings: "Hamstrings",
  calves: "Calves",
};

export type RegionShape = { id: RegionId; d: string };

// Region outlines per view. Order matters: later shapes are painted on top.
// Some muscles are drawn as several closed sub-shapes in one path (e.g. the ab blocks).
export const SHAPES: Record<View, RegionShape[]> = {
  front: [
    { id: "traps_upper", d: "M180 122 Q166 148 130 160 L168 161 Q180 152 184 134 Z" },
    { id: "delt_side", d: "M128 160 Q100 160 88 185 Q80 210 86 245 Q92 262 100 268 Q100 230 108 200 Q116 176 128 160 Z" },
    { id: "delt_front", d: "M132 160 Q150 162 160 170 Q140 190 128 220 Q118 248 104 268 Q100 230 108 200 Q117 176 132 160 Z" },
    { id: "chest_upper", d: "M198 166 Q175 162 158 166 Q140 176 126 208 L132 212 Q165 196 198 196 Z" },
    { id: "chest_mid", d: "M198 199 Q165 199 132 215 L130 225 Q165 222 198 232 Z" },
    { id: "chest_lower", d: "M198 235 Q165 226 130 228 Q132 248 150 262 Q172 272 198 264 Z" },
    { id: "biceps_long", d: "M100 272 Q84 290 82 315 Q82 340 94 352 L102 350 Q98 320 104 280 Z" },
    { id: "biceps_short", d: "M106 276 Q100 320 104 350 L112 352 Q124 334 122 306 Q120 284 110 270 Z" },
    { id: "forearms", d: "M90 358 Q76 380 72 420 Q72 450 78 470 L96 472 Q108 440 116 400 Q120 372 114 356 Q102 362 90 358 Z" },
    { id: "abs_upper", d: "M174 272 Q186 270 198 272 L198 300 L172 302 Q171 286 174 272 Z M172 306 L198 304 L198 334 L171 336 Q170 320 172 306 Z" },
    { id: "abs_lower", d: "M171 340 L198 338 L198 368 L172 370 Z M172 374 L198 372 L198 428 Q186 424 178 404 Q173 390 172 374 Z" },
    { id: "obliques", d: "M166 300 L140 306 Q136 342 146 376 Q156 398 170 404 Q167 360 168 330 Z" },
    { id: "adductors", d: "M176 452 Q196 456 198 470 L194 540 Q186 520 179 500 Q175 474 176 452 Z" },
    // Quads: outer head (vastus lateralis) + inner teardrop (vastus medialis) share one region.
    { id: "quads_vasti", d: "M134 420 Q122 480 128 550 Q134 590 150 612 L160 600 Q152 540 152 480 Q150 446 142 426 Z M176 522 Q192 540 190 576 Q184 606 166 612 L164 600 Q174 568 176 522 Z" },
    // Quads: the middle strip (rectus femoris), the head that also crosses the hip.
    { id: "quads_rectus", d: "M146 428 Q164 436 172 452 Q178 510 170 568 L162 598 Q156 540 156 480 Q154 448 146 428 Z" },
    { id: "calves", d: "M148 652 Q138 690 146 740 L152 748 Q148 700 156 656 Z M180 650 Q194 680 190 730 L184 760 Q176 720 176 680 Z" },
  ],
  back: [
    { id: "lower_back", d: "M188 304 L198 306 L198 444 L182 444 Q184 400 186 370 Z" },
    { id: "traps_upper", d: "M180 120 Q172 150 128 162 Q150 172 178 176 L198 168 L198 124 Z" },
    { id: "traps_mid", d: "M178 180 L198 172 L198 330 Q186 300 176 262 Q166 226 152 200 Q164 190 178 180 Z" },
    { id: "delt_side", d: "M126 162 Q100 162 88 186 Q80 214 88 246 Q96 262 102 268 Q100 230 106 204 Q114 178 126 162 Z" },
    { id: "delt_rear", d: "M130 162 Q144 170 152 180 Q132 196 122 222 Q112 248 104 268 Q100 230 106 204 Q114 178 130 162 Z" },
    { id: "lats_upper", d: "M126 238 Q150 250 172 264 Q180 288 184 306 L136 316 Q130 280 126 238 Z" },
    { id: "lats_lower", d: "M136 320 L184 310 Q182 346 182 380 Q170 390 156 392 Q144 370 140 350 Z" },
    // Triceps: the outer strip is the lateral head (the medial head sits underneath it).
    { id: "triceps_lateral", d: "M96 268 Q82 290 82 316 Q84 336 94 346 L102 330 Q98 300 104 272 Z" },
    // Triceps: the inner strip is the long head, the one that also crosses the shoulder.
    { id: "triceps_long", d: "M106 272 Q102 300 104 330 L98 346 Q112 350 120 336 Q126 310 122 286 Q116 268 106 264 Z" },
    { id: "forearms", d: "M90 356 Q76 380 72 420 Q72 450 78 470 L96 472 Q108 440 116 400 Q120 372 114 354 Q102 360 90 356 Z" },
    { id: "glute_med", d: "M142 404 Q160 396 186 404 L192 430 Q164 428 140 440 Q136 420 142 404 Z" },
    { id: "glutes", d: "M140 444 Q166 432 194 434 L198 440 L198 500 Q188 520 166 518 Q142 512 134 486 Q132 462 140 444 Z" },
    { id: "hamstrings", d: "M138 522 Q130 570 140 610 L156 618 Q158 570 162 526 Q150 528 138 522 Z M166 526 Q186 524 194 530 Q194 580 184 618 L162 618 Q162 570 166 526 Z" },
    { id: "calves", d: "M146 652 Q134 690 144 734 Q152 744 162 738 Q164 700 166 656 Z M170 656 Q190 660 192 690 Q194 724 180 744 Q170 744 166 736 Q168 700 170 656 Z M146 746 Q156 752 168 746 Q178 752 184 750 Q178 770 172 782 L156 782 Q148 768 146 746 Z" },
  ],
};

// Neutral tissue that is not a training region: neck, serratus, hip, hands, knees, shins, feet.
export const BASE: Record<View, string[]> = {
  front: [
    "M178 112 L200 116 L200 168 L170 160 Q180 146 178 112 Z",
    "M136 248 Q132 272 140 302 L166 296 L166 268 Q152 262 136 248 Z",
    "M146 380 Q160 412 198 440 L198 452 Q168 446 136 418 Z",
    "M78 474 Q66 500 72 530 Q84 542 96 530 Q102 500 96 474 Z",
    "M150 612 Q170 632 188 612 L186 644 Q168 654 152 644 Z",
    "M158 652 Q150 700 160 770 L172 790 Q168 720 176 656 Z",
    "M156 792 L184 792 Q190 815 188 834 L140 836 Q144 812 156 792 Z",
  ],
  back: [
    "M178 112 L200 116 L200 168 L170 160 Q180 146 178 112 Z",
    "M150 204 Q162 216 172 260 Q150 250 128 236 Q130 216 150 204 Z",
    "M140 352 Q146 380 158 396 L182 400 Q180 420 174 430 Q150 420 140 400 Z",
    "M78 474 Q66 500 72 530 Q84 542 96 530 Q102 500 96 474 Z",
    "M142 620 L190 620 Q186 640 180 650 L148 650 Q142 640 142 620 Z",
    "M156 784 L174 784 L176 802 L154 802 Z",
    "M150 804 L182 804 L186 836 L146 836 Z",
  ],
};

// How the fiber lines run inside a region:
// - fan: every line starts at one point (where the muscle's tendon attaches) and spreads out.
// - angle: parallel lines at this angle in degrees (0 = horizontal, 90 = vertical).
export type FiberSpec = { fan: [number, number] } | { angle: number };

export const FIBER_SPEC: Record<View, Partial<Record<RegionId, FiberSpec>>> = {
  front: {
    traps_upper: { fan: [124, 166] },
    delt_side: { fan: [98, 282] },
    delt_front: { fan: [98, 282] },
    chest_upper: { fan: [118, 218] },
    chest_mid: { fan: [118, 218] },
    chest_lower: { fan: [118, 218] },
    biceps_long: { fan: [100, 410] },
    biceps_short: { fan: [106, 410] },
    forearms: { angle: 107 },
    abs_upper: { angle: 90 },
    abs_lower: { angle: 90 },
    obliques: { angle: 50 },
    adductors: { fan: [200, 432] },
    // Both quad regions fan toward the kneecap tendon.
    quads_vasti: { fan: [168, 700] },
    quads_rectus: { fan: [168, 700] },
    calves: { fan: [166, 840] },
  },
  back: {
    lower_back: { angle: 92 },
    traps_upper: { fan: [124, 166] },
    traps_mid: { fan: [134, 192] },
    delt_side: { fan: [98, 282] },
    delt_rear: { fan: [98, 282] },
    lats_upper: { fan: [116, 236] },
    lats_lower: { fan: [116, 236] },
    // Both triceps regions fan toward the elbow tendon.
    triceps_lateral: { fan: [100, 380] },
    triceps_long: { fan: [100, 380] },
    forearms: { angle: 107 },
    glute_med: { fan: [130, 452] },
    glutes: { fan: [124, 500] },
    hamstrings: { fan: [166, 720] },
    calves: { fan: [166, 846] },
  },
};
