// Signing up, signing in and logging out.

import { PASSWORD } from "../support/testUser";

// Any finished setup will do here: these tests are about the account, not the choices.
const ONBOARDING: OnboardingChoices = { splitType: "PPL", units: "KG", equipment: ["barbell", "dumbbells"] };

// Fills in the login screen and submits it. The screen starts in "Sign in" mode.
function submitAuthForm(email: string, password: string, mode: "signin" | "signup") {
  cy.visit("/login");
  if (mode === "signup") {
    cy.byTestId("auth-toggle").click();
    // The password field switches to "new-password" in create-account mode.
    cy.byTestId("auth-password").should("have.attr", "autocomplete", "new-password");
  }
  cy.byTestId("auth-email").type(email);
  cy.byTestId("auth-password").type(password, { log: false });
  cy.byTestId("auth-submit").click();
}

describe("Auth", () => {
  it("sends a logged-out visitor from home to the login screen", () => {
    cy.visit("/");
    cy.location("pathname").should("eq", "/login");
    cy.byTestId("auth-email").should("be.visible");
    cy.byTestId("nav-home").should("not.exist");
  });

  it("creating an account leads to onboarding", () => {
    cy.uniqueEmail().then((email) => {
      submitAuthForm(email, PASSWORD, "signup");
    });
    cy.location("pathname").should("eq", "/onboarding");
    cy.byTestId("onboarding-step").should("have.text", "1");
  });

  it("signing up with an email that is already used shows an error", () => {
    cy.uniqueEmail().then((email) => {
      cy.apiSignup(email, PASSWORD);
      cy.clearCookies(); // log out again, so the login screen is reachable
      submitAuthForm(email, PASSWORD, "signup");
    });
    cy.byTestId("auth-error").should("have.text", "Email already registered");
    cy.byTestId("field-error-email").should("have.text", "already used");
    cy.location("pathname").should("eq", "/login");
  });

  it("signing in with a wrong password shows an error", () => {
    cy.uniqueEmail().then((email) => {
      cy.apiSignup(email, PASSWORD);
      cy.clearCookies();
      submitAuthForm(email, "not-the-password", "signin");
    });
    cy.byTestId("auth-error").should("have.text", "Wrong email or password");
    cy.location("pathname").should("eq", "/login");
  });

  it("signing up with a too-short password shows a message under the password field", () => {
    cy.uniqueEmail().then((email) => {
      submitAuthForm(email, "short", "signup");
    });
    cy.byTestId("field-error-password").should("be.visible");
    cy.byTestId("field-error-email").should("not.exist");
    cy.location("pathname").should("eq", "/login");
  });

  it("logging out from settings goes to login and keeps home locked", () => {
    cy.uniqueEmail().then((email) => {
      cy.apiSignup(email, PASSWORD);
      cy.apiOnboard(ONBOARDING);
    });
    cy.visit("/settings");
    cy.byTestId("settings-logout").click();
    cy.location("pathname").should("eq", "/login");

    cy.visit("/");
    cy.location("pathname").should("eq", "/login");
  });

  it("signing back in as an onboarded user lands on home", () => {
    cy.uniqueEmail().then((email) => {
      cy.apiSignup(email, PASSWORD);
      cy.apiOnboard(ONBOARDING);
      cy.clearCookies();
      submitAuthForm(email, PASSWORD, "signin");
    });
    cy.location("pathname").should("eq", "/");
    cy.byTestId("nav-home").should("be.visible");
  });
});
