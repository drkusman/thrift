import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Static export - the whole site is plain HTML/CSS/JS with no Node server needed at runtime,
  // so the backend can serve it directly as static resources instead of running a separate
  // frontend process behind nginx.
  output: "export",
  images: { unoptimized: true },
  trailingSlash: true,
  devIndicators: false,
};

export default nextConfig;
