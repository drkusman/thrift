"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Bank } from "@/lib/types";

type Props = {
  options: Bank[];
  value: number | "";
  onChange: (id: number | "") => void;
  placeholder?: string;
};

export function BankSearchSelect({ options, value, onChange, placeholder }: Props) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const selected = options.find((b) => b.id === value) ?? null;

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return options
      .filter((b) => !q || b.name.toLowerCase().includes(q))
      .sort((a, b) => a.name.localeCompare(b.name))
      .slice(0, 40);
  }, [options, query]);

  return (
    <div className="ss-wrap" ref={containerRef}>
      <input
        className="field-input"
        placeholder={placeholder ?? "Search for a bank..."}
        value={open ? query : selected ? selected.name : ""}
        onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
        onFocus={() => { setQuery(""); setOpen(true); }}
      />
      {open && (
        <div className="ss-panel">
          {filtered.length === 0 && <div className="ss-option text-[var(--muted)]">No matching bank.</div>}
          {filtered.map((b) => (
            <div
              key={b.id}
              className="ss-option"
              onClick={() => { onChange(b.id); setQuery(""); setOpen(false); }}
            >
              {b.name}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
