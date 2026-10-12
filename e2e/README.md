# Fiber end-to-end tests

Browser tests (Cypress) that click through the real app: sign up, onboarding, settings, logging a
workout (one tap per exercise, or set by set), the workout history, the weekly dashboard (region states,
badges, what's still dark), the recommended next workout (with swaps), the summary after a workout
(regions lit up, badges earned) and progress between workouts (suggestions, new bests, exercise page).
Each test creates its own user, so the tests never depend on each other or on existing data.

## 1. Start the app

From the repo root:

```bash
docker compose up --build
```

Wait until http://localhost:3000/login loads.

## 2. Run the tests

In this `e2e/` folder:

```bash
npm ci
npm run cy:run    # all specs, headless, results in the terminal
npm run cy:open   # the Cypress window, to watch and re-run single specs
```

Screenshots of failed tests are saved in `cypress/screenshots/` (not committed).

## Layout

- `cypress.config.ts`: app address, phone-sized screen (390×844), timeouts.
- `cypress/e2e/`: one spec per user flow (`auth`, `onboarding`, `settings`, `workout`, `one-tap`, `history`,
  `dashboard`, `plan`, `summary`, `progress`).
- `cypress/support/commands.ts`: helpers that set up state through the API
  (`apiSignup`, `apiOnboard`, `apiSetTargets`, `apiStartWorkout`, `apiAddExercise`, `apiAddSet`,
  `apiCompleteExercise`, `apiFinish`)
  and find elements by their `data-testid` (`byTestId`, or `shown` for only the ones on screen).
