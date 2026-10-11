// Small helpers shared by every spec. Setup goes through the API (fast);
// the flow under test always goes through the screens.

import { TIMEZONE } from "./testUser";

Cypress.Commands.add("uniqueEmail", () => {
  // The time plus a random number keeps addresses unique across runs and retries.
  const email = `e2e-${Date.now()}-${Cypress._.random(100000, 999999)}@example.com`;
  return cy.wrap(email);
});

// Every POST/PUT/DELETE must echo the XSRF-TOKEN cookie in the X-XSRF-TOKEN header,
// just like the frontend does. This reads the cookie and builds that header.
function csrfHeader(): Cypress.Chainable<Record<string, string>> {
  return cy.getCookie("XSRF-TOKEN").then((cookie): Record<string, string> => {
    if (!cookie) {
      throw new Error("No XSRF-TOKEN cookie: call GET /api/auth/csrf first");
    }
    return { "X-XSRF-TOKEN": decodeURIComponent(cookie.value) };
  });
}

Cypress.Commands.add("apiSignup", (email: string, password: string) => {
  // Requests go through the frontend's /api proxy, so the cookies land on the same origin as the pages.
  cy.request("/api/auth/csrf");
  csrfHeader().then((headers) => {
    cy.request({
      method: "POST",
      url: "/api/auth/signup",
      headers,
      body: { email, password, timezone: TIMEZONE },
    });
  });
});

Cypress.Commands.add("apiOnboard", ({ splitType, units, equipment }: OnboardingChoices) => {
  csrfHeader().then((headers) => {
    cy.request({
      method: "PUT",
      url: "/api/settings",
      headers,
      body: { splitType, units, timezone: TIMEZONE, equipment },
    });
  });
});

Cypress.Commands.add("byTestId", (id: string) => {
  return cy.get(`[data-testid="${id}"]`);
});

// Like byTestId, but only elements on screen. Next.js keeps recently visited pages alive
// (hidden) in the page, so the same test id can exist twice; this skips the hidden copy.
// A query (not a plain command), so assertions after it retry the whole lookup.
Cypress.Commands.addQuery("shown", function (id: string) {
  const getFn = cy.now("get", `[data-testid="${id}"]:visible`) as (subject: unknown) => JQuery<HTMLElement>;
  return (subject) => getFn(subject);
});

// Workout setup through the API. Weights are always in kg here, like the API itself.

Cypress.Commands.add("apiStartWorkout", () => {
  return csrfHeader().then((headers) =>
    cy.request<WorkoutSession>({ method: "POST", url: "/api/sessions", headers, body: {} }).its("body"),
  );
});

Cypress.Commands.add("apiAddExercise", (sessionId: string, exerciseId: string) => {
  return csrfHeader().then((headers) =>
    cy
      .request<WorkoutSession>({ method: "POST", url: `/api/sessions/${sessionId}/exercises`, headers, body: { exerciseId } })
      .its("body"),
  );
});

// Adds a set to the exercise at `position` (starts at 1). With completed: true it is also
// ticked off, which the API does as a second step (new sets always start un-ticked).
Cypress.Commands.add("apiAddSet", (sessionId: string, position: number, values: SetSetup) => {
  const { completed = false, ...body } = values;
  return csrfHeader().then((headers) =>
    cy
      .request<WorkoutSet>({ method: "POST", url: `/api/sessions/${sessionId}/exercises/${position}/sets`, headers, body })
      .its("body")
      .then((created) => {
        if (!completed) {
          return cy.wrap(created, { log: false });
        }
        return cy
          .request<WorkoutSet>({ method: "PATCH", url: `/api/sets/${created.id}`, headers, body: { completed: true } })
          .its("body");
      }),
  );
});

Cypress.Commands.add("apiFinish", (sessionId: string) => {
  return csrfHeader().then((headers) =>
    cy.request<WorkoutSession>({ method: "POST", url: `/api/sessions/${sessionId}/finish`, headers }).its("body"),
  );
});
