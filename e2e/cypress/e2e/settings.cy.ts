// Changing split, equipment, units and weekly targets after onboarding.

import { PASSWORD } from "../support/testUser";

describe("Settings", () => {
  beforeEach(() => {
    // A fresh, onboarded user for every test: PPL, kg, some equipment.
    cy.uniqueEmail().then((email) => {
      cy.apiSignup(email, PASSWORD);
      cy.apiOnboard({ splitType: "PPL", units: "KG", equipment: ["barbell", "dumbbells", "cables", "bench"] });
    });
    cy.intercept("PUT", "/api/settings").as("saveSettings");
  });

  it("changing the split saves it and it stays after a reload", () => {
    cy.visit("/settings");
    cy.byTestId("settings-split-PPL").should("have.attr", "aria-pressed", "true");

    cy.byTestId("settings-split-UL").should("be.visible").and("be.enabled").click();
    cy.wait("@saveSettings").its("response.statusCode").should("eq", 200);
    cy.byTestId("settings-saved").should("be.visible");

    cy.reload();
    cy.byTestId("settings-split-UL").should("have.attr", "aria-pressed", "true");
    cy.byTestId("settings-split-PPL").should("have.attr", "aria-pressed", "false");
  });

  it("toggling equipment saves it and it stays after a reload", () => {
    cy.visit("/settings");
    cy.byTestId("settings-equipment-cables").should("have.attr", "aria-pressed", "true");
    cy.byTestId("settings-equipment-kettlebells").should("have.attr", "aria-pressed", "false");

    cy.byTestId("settings-equipment-cables").should("be.visible").and("be.enabled").click();
    cy.wait("@saveSettings").its("response.statusCode").should("eq", 200);
    cy.byTestId("settings-saved").should("be.visible");

    // Buttons are disabled while a save runs, so wait for the first save before the next tap.
    cy.byTestId("settings-equipment-kettlebells").should("not.be.disabled").click();
    cy.wait("@saveSettings").its("response.statusCode").should("eq", 200);

    cy.reload();
    cy.byTestId("settings-equipment-cables").should("have.attr", "aria-pressed", "false");
    cy.byTestId("settings-equipment-kettlebells").should("have.attr", "aria-pressed", "true");
  });

  it("switching units saves it and it stays after a reload", () => {
    cy.visit("/settings");
    cy.byTestId("settings-units-KG").should("have.attr", "aria-pressed", "true");

    cy.byTestId("settings-units-LB").should("be.visible").and("be.enabled").click();
    cy.wait("@saveSettings").its("response.statusCode").should("eq", 200);
    cy.byTestId("settings-saved").should("be.visible");

    cy.reload();
    cy.byTestId("settings-units-LB").should("have.attr", "aria-pressed", "true");
    cy.byTestId("settings-units-KG").should("have.attr", "aria-pressed", "false");
  });

  it("raising a weekly target and saving keeps the new value after a reload", () => {
    cy.intercept("PUT", "/api/targets").as("saveTargets");
    cy.visit("/settings");
    // Targets are a detail most users never need, so the link says so.
    cy.byTestId("settings-targets-link").should("be.visible").and("contain.text", "Advanced: weekly targets").click();
    cy.location("pathname").should("eq", "/settings/targets");

    cy.byTestId("target-value-delt_rear")
      .invoke("text")
      .then(Number)
      .then((start) => {
        cy.byTestId("target-inc-delt_rear").should("be.visible").and("be.enabled").click();
        cy.byTestId("target-value-delt_rear").should("have.text", String(start + 1));

        cy.byTestId("targets-save").click();
        cy.wait("@saveTargets").its("response.statusCode").should("eq", 200);
        cy.byTestId("targets-saved").should("be.visible");

        cy.reload();
        cy.byTestId("target-value-delt_rear").should("have.text", String(start + 1));
      });
  });

  it("the save button is only enabled when a target has changed", () => {
    cy.visit("/settings/targets");
    cy.byTestId("targets-save").should("be.disabled");

    cy.byTestId("target-inc-chest_upper").should("be.visible").and("be.enabled").click();
    cy.byTestId("targets-save").should("be.enabled");

    // Back to the saved value: nothing left to save.
    cy.byTestId("target-dec-chest_upper").click();
    cy.byTestId("targets-save").should("be.disabled");
  });

  it("a target can't go above 30 or below 0 with the steppers", () => {
    cy.visit("/settings/targets");

    cy.byTestId("target-value-chest_upper")
      .invoke("text")
      .then(Number)
      .then((start) => {
        // Step up to the top...
        cy.byTestId("target-inc-chest_upper").should("be.visible").and("be.enabled");
        Cypress._.times(30 - start, () => cy.byTestId("target-inc-chest_upper").click());
        cy.byTestId("target-value-chest_upper").should("have.text", "30");
        cy.byTestId("target-inc-chest_upper").should("be.disabled");

        // ...then all the way down to the bottom.
        Cypress._.times(30, () => cy.byTestId("target-dec-chest_upper").click());
        cy.byTestId("target-value-chest_upper").should("have.text", "0");
        cy.byTestId("target-dec-chest_upper").should("be.disabled");
      });
  });
});
