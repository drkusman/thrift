"use client";

import { useEffect, useState } from "react";

const KEY = "thrift-theme";

function currentTheme(): "light" | "dark" {
  const attr = document.documentElement.getAttribute("data-theme");
  if (attr === "light" || attr === "dark") return attr;
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function ThemeToggle({ className }: { className?: string }) {
  const [dark, setDark] = useState(false);

  useEffect(() => {
    setDark(currentTheme() === "dark");
  }, []);

  function toggle() {
    const next = currentTheme() === "dark" ? "light" : "dark";
    try {
      localStorage.setItem(KEY, next);
    } catch {
      // per-viewer convenience only - fine if it doesn't persist
    }
    document.documentElement.setAttribute("data-theme", next);
    setDark(next === "dark");
  }

  const label = dark ? "Switch to light mode" : "Switch to dark mode";
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={label}
      title={label}
      className={`w-9 h-9 shrink-0 grid place-items-center rounded-lg border border-[var(--line)] text-[var(--ink)] hover:bg-[var(--maroon-light)]/60 ${className ?? ""}`}
    >
      {dark ? "☀️" : "🌙"}
    </button>
  );
}
