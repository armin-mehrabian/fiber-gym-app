// Type declarations for the custom commands in commands.ts, so specs get autocomplete and type checks.

type SplitType = "PPL" | "UL" | "FB";
type Units = "KG" | "LB";

type OnboardingChoices = {
  splitType: SplitType;
  units: Units;
  equipment: string[]; // equipment ids, e.g. "barbell"
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
  }
}
