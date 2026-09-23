"use client";

import { useRef, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, ApiError } from "@/lib/api";

type ImportKind = "banks" | "loan-types" | "members" | "ledger";

const STEPS: { kind: ImportKind; label: string; hint: string }[] = [
  { kind: "banks", label: "1. Banks", hint: "bank.csv - bankcode, name, sortcode" },
  { kind: "loan-types", label: "2. Loan types", hint: "loantype.csv - loadCode, loanName, interestRate, interestTime, maxduration" },
  { kind: "members", label: "3. Members", hint: "members.csv - regno, fullname, phone, email, ... (import first so bank accounts on member rows can match already-imported banks)" },
  { kind: "ledger", label: "4. Ledger (can take several minutes for large files)", hint: "ledger.csv - transcode, staffid, amount, date, description, transtype, transcat, drcrstatus, ..." },
];

function ImportRow({ kind, label, hint }: { kind: ImportKind; label: string; hint: string }) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null); setResult(null);
    const file = fileRef.current?.files?.[0];
    if (!file) { setError("Choose a CSV file."); return; }
    setSubmitting(true);
    try {
      const form = new FormData();
      form.append("file", file);
      const text = await api.postForm<string>(`/api/admin/import/${kind}`, form);
      setResult(String(text));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Import failed.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="card p-5 space-y-3">
      <div>
        <h2 className="font-semibold text-[var(--ink)]">{label}</h2>
        <p className="text-xs text-[var(--muted)]">{hint}</p>
      </div>
      <div className="flex items-center gap-3">
        <input ref={fileRef} type="file" accept=".csv" className="text-sm flex-1" />
        <button type="submit" disabled={submitting} className="btn btn-primary whitespace-nowrap">
          {submitting ? "Importing..." : "Import"}
        </button>
      </div>
      {error && <p className="alert-error">{error}</p>}
      {result && <p className="alert-success">{result}</p>}
    </form>
  );
}

function AdminImportContent() {
  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Legacy data import</h1>
        <p className="text-sm text-[var(--muted)] mt-1">
          One-time, re-runnable import from the old system&rsquo;s CSV exports. Run in order: banks, loan types, members, then ledger.
          Re-running is safe - existing rows are upserted by their natural key, and already-imported ledger rows are skipped by transaction code.
        </p>
      </div>
      {STEPS.map((s) => (
        <ImportRow key={s.kind} kind={s.kind} label={s.label} hint={s.hint} />
      ))}
    </div>
  );
}

export default function AdminImportPage() {
  return (
    <RequireAuth staffOnly>
      <AdminImportContent />
    </RequireAuth>
  );
}
