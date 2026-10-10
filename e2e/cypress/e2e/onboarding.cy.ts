// The three setup steps after signing up: split → equipment → units.

import { PASSWORD } from "../support/testUser";

describe("Onboarding", () => {
  it("a new user picks a split, equipment and units, lands on home and sees the choices in settings", () => {
    // Sign up through the screens, as a real new user would.
    cy.uniqueEmail().then((email) => {
      cy.visit("/login");
      cy.byTestId("auth-toggle").click();
      cy.byTestId("auth-password").should("have.attr", "autocomplete", "new-password");
      cy.byTestId("auth-email").type(email);
      cy.byTestId("auth-password").type(PASSWORD, { log: false });
      cy.byTestId("auth-submit").click();
    });
    cy.location("pathname").should("eq", "/onboarding");
    cy.byTestId("nav-home").should("not.exist"); // no bottom nav during setup

    // Step 1: split.
    cy.byTestId("onboarding-step").should("have.text", "1");
    cy.byTestId("split-UL").click();
    cy.byTestId("split-UL").should("have.attr", "aria-pressed", "true");
    cy.byTestId("split-PPL").should("have.attr", "aria-pressed", "false");
    cy.byTestId("onboarding-next").click();

    // Step 2: equipment. Everything starts ticked; untick one item.
    cy.byTestId("onboarding-step").should("have.text", "2");
    cy.byTestId("equipment-kettlebells").should("have.attr", "aria-pressed", "true");
    cy.byTestId("equipment-kettlebells").click();
    cy.byTestId("equipment-kettlebells").should("have.attr", "aria-pressed", "false");
    cy.byTestId("onboarding-next").click();

    // Step 3: units.
    cy.byTestId("onboarding-step").should("have.text", "3");
    cy.byTestId("units-LB").click();
    cy.byTestId("units-LB").should("have.attr", "aria-pressed", "true");
    cy.byTestId("onboarding-next").click();

    // Finished: home with the bottom nav.
    cy.location("pathname").should("eq", "/");
    cy.byTestId("nav-home").should("be.visible");
    cy.byTestId("nav-settings").should("be.visible");

    // The settings screen shows what was picked.
    cy.byTestId("nav-settings").click();
    cy.location("pathname").should("eq", "/settings");
    cy.byTestId("settings-split-UL").should("have.attr", "aria-pressed", "true");
    cy.byTestId("settings-equipment-kettlebells").should("have.attr", "aria-pressed", "false");
    cy.byTestId("settings-equipment-barbell").should("have.attr", "aria-pressed", "true");
    cy.byTestId("settings-units-LB").should("have.attr", "aria-pressed", "true");
  });

  it("the back button returns to the previous step and keeps the picks", () => {
    cy.uniqueEmail().then((email) => cy.apiSignup(email, PASSWORD));
    cy.visit("/onboarding");

    cy.byTestId("onboarding-back").should("not.exist"); // nowhere to go back to on step 1
    cy.byTestId("split-FB").should("be.visible").and("be.enabled").click();
    cy.byTestId("split-FB").should("have.attr", "aria-pressed", "true");
    cy.byTestId("onboarding-next").click();
    cy.byTestId("onboarding-step").should("have.text", "2");
    cy.byTestId("onboarding-next").click();
    cy.byTestId("onboarding-step").should("have.text", "3");

    cy.byTestId("onboarding-back").click();
    cy.byTestId("onboarding-step").should("have.text", "2");
    cy.byTestId("onboarding-back").click();
    cy.byTestId("onboarding-step").should("have.text", "1");
    cy.byTestId("split-FB").should("have.attr", "aria-pressed", "true");
  });

  it("visiting onboarding again after finishing it goes to home", () => {
    cy.uniqueEmail().then((email) => {
      cy.apiSignup(email, PASSWORD);
      cy.apiOnboard({ splitType: "PPL", units: "KG", equipment: ["barbell"] });
    });
    cy.visit("/onboarding");
    cy.location("pathname").should("eq", "/");
    cy.byTestId("nav-home").should("be.visible");
  });
});
