import type { MetadataRoute } from "next";

// Required for `output: "export"` - a manifest route has no per-request data, so it's always static.
export const dynamic = "force-static";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "ASUU-MOAUM Thrift & Savings",
    short_name: "ASUU-MOAUM Thrift",
    description: "Member portal for the ASUU-MOAUM cooperative thrift & savings scheme.",
    start_url: "/dashboard",
    scope: "/",
    display: "standalone",
    background_color: "#faf7f5",
    theme_color: "#4c1520",
    orientation: "portrait-primary",
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
