import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Emits .next/standalone: a self-contained server bundle with only the
  // node_modules actually reached at runtime. frontend/Dockerfile copies it,
  // so without this the self-hosted production image cannot be built at all.
  //
  // Skipped on Vercel, which uses its own build output format and does not want
  // a standalone server bundle.
  output: process.env.VERCEL ? undefined : "standalone",

  /**
   * Optional same-origin proxy for the API.
   *
   * Session cookies default to SameSite=Lax, which browsers refuse to send on
   * cross-site requests. Hosting the frontend and backend on unrelated domains
   * (a Vercel app calling a Render service, say) therefore breaks login itself,
   * not just some requests - and the usual fix, SameSite=None, is strictly
   * weaker.
   *
   * Setting BACKEND_ORIGIN makes Next forward /api/* to the backend server-side,
   * so the browser only ever talks to the frontend's own origin. Cookies are
   * same-origin, CORS never applies, and the backend can live anywhere.
   *
   * Leave it unset to talk to the backend directly, which is what local
   * development and the single-VM docker-compose deployment do.
   */
  async rewrites() {
    const backend = process.env.BACKEND_ORIGIN;
    if (!backend) return [];
    return [
      { source: "/api/:path*", destination: `${backend}/api/:path*` },
      { source: "/actuator/health", destination: `${backend}/actuator/health` },
    ];
  },
};

export default nextConfig;
