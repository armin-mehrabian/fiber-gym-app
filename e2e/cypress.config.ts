import { defineConfig } from "cypress";

export default defineConfig({
  e2e: {
    // The frontend from `docker compose up`. Its /api/* calls are proxied to the backend.
    baseUrl: "http://localhost:3000",
    // Fiber is phone-first, so every test runs at an iPhone-sized screen.
    viewportWidth: 390,
    viewportHeight: 844,
    // A freshly started Next.js server can take a few seconds to render its first pages.
    defaultCommandTimeout: 10000,
    video: false,
    screenshotOnRunFailure: true,
    // No retries: a pass always means the test passed on its first try.
    retries: 0,
  },
});
