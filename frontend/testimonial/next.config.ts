import type { NextConfig } from "next";

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
  // NOTE: `cacheComponents` / `partialPrefetching` are intentionally NOT enabled.
  // Cache Components is designed for static-shell + streaming patterns; every
  // authenticated page here (dashboard, inbox, embed config) is fully dynamic
  // and owner-scoped, fetched from the Java backend at request time. Enabling
  // it would force every dynamic read into a <Suspense> boundary without
  // any benefit. Revisit if public, shareable pages are added later.
};

export default nextConfig;
