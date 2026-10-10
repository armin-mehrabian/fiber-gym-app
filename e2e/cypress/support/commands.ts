// Small helpers shared by every spec. Setup goes through the API (fast);
// the flow under test always goes through the screens.

// Every user gets the same zone, so test results don't depend on the machine's clock settings.
const TIMEZONE = "Europe/London";

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
