import { defineConfig } from "vitest/config";

// Unit tests for pure helpers (*.test.ts). They run in plain Node: no browser needed.
export default defineConfig({
  test: {
    environment: "node",
  },
});
