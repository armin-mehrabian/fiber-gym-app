import type { NextConfig } from "next";

// Where the Spring Boot API lives (http://backend:8080 in Docker).
// Rewrite targets are read at BUILD time and baked into the build, so the
// Dockerfile passes BACKEND_URL as a build arg, not only at runtime.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // Produces a self-contained server (.next/standalone) for a small Docker image.
  output: "standalone",

  // Next.js 16 defaults from create-next-app: data is fetched per request unless
  // explicitly cached, and dynamic parts stream in behind <Suspense>.
  cacheComponents: true,
  partialPrefetching: true,

  // Tailwind CSS v4 runs as a Turbopack loader on CSS files.
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },

  // Browser calls to /api/* are proxied to the backend: same origin,
  // so no CORS and the session cookie is sent automatically.
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
};

export default nextConfig;
