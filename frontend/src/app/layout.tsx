import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/lib/auth-context";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "ASUU-MOAUM Thrift",
  description: "ASUU-MOAUM cooperative thrift & savings portal",
  manifest: "/manifest.webmanifest",
  appleWebApp: {
    capable: true,
    title: "ASUU-MOAUM Thrift",
    statusBarStyle: "default",
  },
};

export const viewport = {
  themeColor: "#4c1520",
};

// Applies a saved theme before first paint, so there's no flash of the wrong theme on load.
const themeInitScript = `(function(){try{var t=localStorage.getItem("thrift-theme");if(t==="light"||t==="dark")document.documentElement.setAttribute("data-theme",t);}catch(e){}})();`;

// Registered as a plain script (not a client component) so it still runs on the static export with
// no JS framework overhead - a service worker is what makes the site installable as a PWA at all.
const swRegisterScript = `if("serviceWorker" in navigator){window.addEventListener("load",function(){navigator.serviceWorker.register("/sw.js").catch(function(){});});}`;

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <head>
        <link rel="apple-touch-icon" href="/icon-192.png" />
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
        <script dangerouslySetInnerHTML={{ __html: swRegisterScript }} />
      </head>
      <body className="h-full flex flex-col overflow-hidden">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
