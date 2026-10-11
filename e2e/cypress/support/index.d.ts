// Type declarations for the custom commands in commands.ts, so specs get autocomplete and type checks.

type SplitType = "PPL" | "UL" | "FB";
type Units = "KG" | "LB";

type OnboardingChoices = {
  splitType: SplitType;
  units: Units;
  equipment: string[]; // equipment ids, e.g. "barbell"
};

// The parts of the workout API replies that the tests read (weights in kg).
type WorkoutSet = {
  id: string;
  setNumber: number;
  weightKg: number;
  reps: number;
  isWarmup: boolean;
  completed: boolean;
};

type SessionExercise = {
  position: number;
  exerciseId: string;
  name: string;
  targetSets: number;
  sets: WorkoutSet[];
};

type WorkoutSession = {
  id: string;
  name: string;
  status: "ACTIVE" | "FINISHED";
  startedAt: string;
  finishedAt: string | null;
  exercises: SessionExercise[];
};

// A set to create in a test. completed: true also ticks it off (default: not ticked).
type SetSetup = {
  weightKg: number;
  reps: number;
  isWarmup: boolean;
  completed?: boolean;
};

declare namespace Cypress {
  interface Chainable {
    /** A new email address for every call, so each test gets its own user. */
    uniqueEmail(): Chainable<string>;

    /** Creates an account through the API and keeps its session cookie in the browser. */
    apiSignup(email: string, password: string): Chainable<void>;

    /** Finishes onboarding through the API for the logged-in user. */
    apiOnboard(choices: OnboardingChoices): Chainable<void>;

    /** Finds elements by their data-testid attribute. */
    byTestId(id: string): Chainable<JQuery<HTMLElement>>;

    /** Like byTestId, but only the elements on screen (skips pages Next.js keeps hidden). */
    shown(id: string): Chainable<JQuery<HTMLElement>>;

    /** Starts an empty workout through the API and yields it. */
    apiStartWorkout(): Chainable<WorkoutSession>;

    /** Adds an exercise (by id, e.g. "barbell_curl") to the end of the workout; yields the workout. */
    apiAddExercise(sessionId: string, exerciseId: string): Chainable<WorkoutSession>;

    /** Adds a set to the exercise at `position` (starts at 1); yields the set. */
    apiAddSet(sessionId: string, position: number, values: SetSetup): Chainable<WorkoutSet>;

    /** Finishes the workout through the API; yields it. */
    apiFinish(sessionId: string): Chainable<WorkoutSession>;
  }
}
