"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { MemberOption } from "@/lib/types";

type Props = {
  options: MemberOption[];
  value: number | "";
  onChange: (id: number | "") => void;
  excludeIds?: number[];
  placeholder?: string;
};

export function MemberSearchSelect({ options, value, onChange, excludeIds = [], placeholder }: Props) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const selected = options.find((m) => m.id === value) ?? null;

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  const filtered = useMemo(() => {
    const excluded = new Set(excludeIds);
    const q = query.trim().toLowerCase();
    return options
      .filter((m) => !excluded.has(m.id))
      .filter((m) => !q || m.fullName.toLowerCase().includes(q) || m.regno.toLowerCase().includes(q))
      .slice(0, 30);
  }, [options, query, excludeIds]);

  return (
    <div className="ss-wrap" ref={containerRef}>
      <input
        className="field-input"
        placeholder={placeholder ?? "Search by name or regno..."}
        value={open ? query : selected ? `${selected.fullName} (${selected.regno})` : ""}
        onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
        onFocus={() => { setQuery(""); setOpen(true); }}
      />
      {open && (
        <div className="ss-panel">
          {filtered.length === 0 && <div className="ss-option text-[var(--muted)]">No matching active members.</div>}
          {filtered.map((m) => (
            <div
              key={m.id}
              className="ss-option"
              onClick={() => { onChange(m.id); setQuery(""); setOpen(false); }}
            >
              {m.fullName} <span className="text-[var(--muted)]">({m.regno})</span>
            </div>
          ))}
          {selected && (
            <div className="ss-option text-[var(--muted)]" onClick={() => { onChange(""); setQuery(""); setOpen(false); }}>
              Clear selection
            </div>
          )}
        </div>
      )}
    </div>
  );
}
