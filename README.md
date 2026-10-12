# FIBER

A gym app that tracks which *part* of each muscle you trained this week, and plans your next workout around what you missed.

<p align="center">
  <img height="520" alt="Login" src="https://github.com/user-attachments/assets/08141a6b-3d47-4574-8154-de48bef26532" />
  <img height="520" alt="Home" src="https://github.com/user-attachments/assets/8a2105cf-985d-44ef-99c6-91651561f8c4" />
  <br><br>
  <img height="520" alt="Weekly badges" src="https://github.com/user-attachments/assets/791554b8-57d3-4a51-93ef-c2ac87b70ed3" />
  <img height="520" alt="Workout" src="https://github.com/user-attachments/assets/d8d73e8a-72d3-4079-9b14-d97d8a945996" />
  <br><br>
  <img height="520" alt="Changing an exercise" src="https://github.com/user-attachments/assets/15604293-68d9-4eae-8ccd-19bd7cc818b6" />
  <img height="520" alt="Settings" src="https://github.com/user-attachments/assets/611d91e1-cc37-449a-948e-966a9aaa32f1" />
</p>

## Why I built it

Most workout apps think in whole muscles. You log a bench press and it says "chest: done". But a flat bench
mostly hits the middle of your chest, and if all you ever do is flat bench, your upper chest falls behind
without you noticing. Same with lats, delts, triceps heads and so on.

So Fiber splits the body into 26 regions (upper/mid/lower chest, front/side/rear delts, upper and lower
lats, and so on). Every exercise in the app is mapped to the regions it mainly works and the ones it helps
with. When you finish a workout, those regions light up gold on a body figure. Anything still dark at the
end of the week is what your next workout should go after, and Fiber builds that workout for you.

A note on the muscle mappings: they describe where an exercise puts the most *emphasis*, not some claim
that it isolates one region. They're sensible defaults from training research, and the sources are listed in
[`data/SOURCES.md`](data/SOURCES.md).

## What you can do with it

- See your week on a front and back body figure. Each region is either *not yet*, *started* or *done*.
- Tap START and get a workout for the next day of your split (push/pull/legs, upper/lower or full body),
  picked to fill the regions that are furthest behind.
- Log an exercise with one tap. It comes pre-filled with sets, reps and a weight based on last time.
  If you did something different, hit *Change* first, or log set by set if you prefer.
- Swap an exercise you don't like for one that hits similar regions with the equipment you have.
- Finish and see which regions you just completed, any muscle group badges you earned, and new personal bests.
- Set your split, equipment, kg or lb, and (if you want to fine tune) a weekly set target for each region.

## How it's put together

It's a pretty standard three-part setup: a Next.js frontend, a Spring Boot API and a Postgres database,
all started together with Docker Compose.

```
  +-------------+        +---------------------+        +---------------------+        +------------+
  |  phone /    |  -->   |  Next.js frontend   |  -->   |  Spring Boot API    |  -->   | PostgreSQL |
  |  browser    |        |  (port 3000)        | /api/* |  (port 8080)        |        |            |
  +-------------+        +---------------------+        +---------------------+        +------------+
                                                                  ^
                                                                  |  loaded at startup
                                                         data/*.json (regions,
                                                         equipment, exercises)
```

A few decisions I made along the way:

- **The frontend proxies `/api/*` to the backend**, so the browser only ever talks to one address. That keeps
  cookies and CSRF simple.
- **The interesting logic is plain Java.** Coverage, recovery, recommendations and progression each live in
  their own small class (`CoverageCalculator`, `RecoveryCalculator`, `Recommender`, `ProgressionRule`) with no
  Spring or database code in them. You pass data in and get an answer back, which makes them easy to test
  and easy to reason about.
- **Controllers stay thin.** They read the request, validate it and call a service. Services handle
  transactions and the database.
- **Flyway owns the schema.** Hibernate only checks that the entities match it; it never changes tables itself.
- **The body figure is hand-built SVG**, with separate front and back views, so every region can be coloured
  and tapped on its own.

Stack: Next.js 16, React 19, TypeScript, Tailwind CSS 4 · Java 21, Spring Boot 4, Spring Security, JPA ·
PostgreSQL 16, Flyway · JUnit, Testcontainers, Vitest, Cypress · GitHub Actions.

## The training logic

This is the part I spent the most time on, so here's how it actually works.

### Counting sets

Only completed working sets from a finished workout count. Warm-ups and sets you didn't tick off don't.

Each set counts as 1 for the exercise's main regions and 0.5 for the regions it helps with. So 3 sets of
incline dumbbell press gives your upper chest 3, and your front delts and triceps 1.5 each.

The week runs Monday to Sunday in your own timezone (picked up automatically when you sign up). A region
is *done* once it reaches its weekly target. By default that's 6 sets for the 12 bigger regions and 4 for
the rest, since smaller ones like front delts and triceps already get a lot of half credit from compound lifts.

When every region in a group (chest, shoulders, back, arms, core, legs) is done, you get that group's badge.

### Recovery

If one workout in the last 48 hours gave a region 3 or more sets, Fiber treats it as recovering and won't
try to fill it again yet. It's per workout on purpose: two light sessions don't add up to "needs rest", one
hard one does.

### Picking the next workout

1. Work out which day of your split comes next, based on your last workout.
2. For each region on that day that isn't recovering, figure out the gap: `target - sets so far`.
3. Look at every exercise whose main region is on that day (and not recovering) and that fits your equipment,
   and score it by how much of the remaining gaps it would fill (main regions count fully, helper regions count half).
4. Take the best one, assume you'll do 3 sets, shrink the gaps, and go again.

It stops at 5 exercises, or earlier if nothing is left to fill. There's also a small penalty for picking the
same movement pattern or the same main region twice, so you don't end up with three different chest
presses in a row.

### Suggesting weights

Fiber looks at the last time you did that exercise:

- If you hit the top of the rep range on every set, the weight goes up (2.5 kg, or 5 kg for leg exercises)
  and the reps reset to the bottom of the range.
- Otherwise it keeps the same weight and asks for one more rep than your weakest set.
- If you've never done it, there's no weight suggestion yet, just a rep target at the top of the range.

## Security

Nothing fancy, just doing the basics properly:

- Logins use a server-side session cookie that's HttpOnly and SameSite=Lax (and Secure when running over HTTPS).
- Every write request needs a CSRF token. The server sets it in an `XSRF-TOKEN` cookie and the frontend
  sends it back in an `X-XSRF-TOKEN` header.
- Passwords are hashed with BCrypt.
- Every query for personal data is filtered by the logged-in user. Asking for someone else's workout just
  gives a 404, so you can't even tell it exists.
- Changes to a workout lock its row first, so tapping a button twice quickly can't create duplicate sets.
  The database also only allows one active workout per user.
- Credentials come from environment variables, never from the repo.

## Running it

You only need Docker.

```bash
git clone https://github.com/armin-mehrabian/fiber-gym-app.git
cd fiber-gym-app
docker compose up --build
```

Then open http://localhost:3000. It's designed for a phone, so the device mode in your browser's dev tools
gives the best view. If you want to change the database password, copy `.env.example` to `.env` first.

## Tests

There are around 590 automated tests across the project:

- **Backend:** 416 tests with JUnit, Mockito and AssertJ. The integration tests run against a real Postgres
  through Testcontainers, so Docker needs to be running.
- **Frontend:** 131 unit tests with Vitest.
- **End to end:** 45 Cypress tests that click through the real app on a phone-sized screen. Each test signs
  up its own user so they never step on each other.
- **Seed data:** a small script checks that every exercise points at regions and equipment that actually exist.

```bash
cd backend && ./mvnw verify
cd frontend && npm ci && npm run lint && npm test
node data/validate.mjs
cd e2e && npm ci && npm run cy:run      # with the app running, see e2e/README.md
```

GitHub Actions runs all of this on every push, including starting the whole app with Docker Compose and
running Cypress against it.

## API

Everything is under `/api` and speaks JSON. Errors always look like `{ "error": "...", "fields": { ... } }`.

```
Auth           GET  /auth/csrf      POST /auth/signup     POST /auth/login     POST /auth/logout    GET /me
Catalog        GET  /regions        GET  /equipment       GET  /exercises      GET  /exercises/{id}
Settings       GET  /settings       PUT  /settings        GET  /targets        PUT  /targets
Workouts       POST /sessions       GET  /sessions/active GET  /sessions/{id}  DELETE /sessions/{id}
               POST /sessions/{id}/finish                 GET  /sessions/{id}/summary
Exercises      POST /sessions/{id}/exercises              PUT | DELETE /sessions/{id}/exercises/{position}
               POST /sessions/{id}/exercises/{position}/sets
               POST /sessions/{id}/exercises/{position}/complete      (one-tap logging)
Sets           PATCH /sets/{setId}  DELETE /sets/{setId}
Progress       GET  /coverage/week  GET  /history         GET  /exercises/{id}/history
Plans          GET  /recommendation/next                  GET  /recommendation/alternatives
Health         GET  /health
```

## Database

```
user_account
 |-- user_settings        split, units, timezone
 |-- user_equipment
 |-- region_target        weekly sets per region
 '-- workout_session
      '-- session_exercise     position, suggested weight/reps
           '-- workout_set     weight (kg), reps, warm-up, done

exercise --< exercise_region >-- region          (primary or secondary)
exercise --< exercise_equipment >-- equipment
split_day --< split_day_region >-- region
```

Weights are always stored in kg and converted to lb in the UI. Timestamps are stored in UTC.

## Folders

```
frontend/   the Next.js app: screens, the body figure, the API client and the logic behind the screens
backend/    the Spring Boot API, Flyway migrations and all backend tests
data/       the region list, equipment, exercise library, sources and the validation script
e2e/        Cypress tests
.github/    the CI workflow
```

## Future Developments

- Short 3D demo loops for each exercise
- Logging offline and syncing later
- Custom exercises
- More (and customisable) regions
