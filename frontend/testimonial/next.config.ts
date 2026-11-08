import type { NextConfig } from "next";

const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  experimental: {
    // Lets `next dev` maintain the AGENTS.md block for AI coding agents.
    agentFeedback: true,
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  // The session cookie is the auth mechanism, and it is SameSite=Lax. That
  // only works if the API is same-origin with the app, so requests to
  // /api/* are proxied to the Java backend rather than called cross-origin.
  // The browser therefore only ever sees one origin, and the cookie is
  // attached without needing SameSite=None (which would require HTTPS).
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },
  // NOTE: `cacheComponents` / `partialPrefetching` are intentionally NOT enabled.
  // Cache Components is designed for static-shell + streaming patterns; every
  // authenticated page here (dashboard, inbox, embed config) is fully dynamic
  // and owner-scoped, fetched from the Java backend at request time. Enabling
  // it would force every dynamic read into a <Suspense> boundary without
  // any benefit. Revisit if public, shareable pages are added later.
};

export default nextConfig;
