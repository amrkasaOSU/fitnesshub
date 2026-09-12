import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Emits .next/standalone: a self-contained server bundle with only the
  // node_modules actually reached at runtime. frontend/Dockerfile copies it,
  // so without this the production image cannot be built at all.
  output: "standalone",
};

export default nextConfig;
