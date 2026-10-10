import { defineConfig } from "vitest/config";

// Unit tests for pure helpers (*.test.ts). They run in plain Node: no browser needed.
export default defineConfig({
  // Understand the "@/..." import paths from tsconfig.json, like the Next.js build does.
  resolve: { tsconfigPaths: true },
  test: {
    environment: "node",
  },
});
