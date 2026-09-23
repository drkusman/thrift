"use client";

import { useRef, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, ApiError } from "@/lib/api";

type UploadResult = { batchId: number; totalRows: number; matchedRows: number; totalAmount: number };

function AdminContributionsContent() {
  const [periodMonth, setPeriodMonth] = useState("");
  const [result, setResult] = useState<UploadResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null); setResult(null);
    const file = fileRef.current?.files?.[0];
    if (!file || !periodMonth) { setError("Choose a period month and a file."); return; }
    setSubmitting(true);
    try {
      const form = new FormData();
      form.append("file", file);
      const res = await api.postForm<UploadResult>(`/api/admin/contributions/upload?periodMonth=${encodeURIComponent(periodMonth)}`, form);
      setResult(res);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Upload failed.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-6 max-w-lg">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Monthly contribution upload</h1>
      <p className="text-sm text-[var(--muted)]">
        Upload an Excel sheet with columns <code className="bg-[var(--maroon-light)] text-[var(--maroon-dark)] px-1.5 py-0.5 rounded">regno</code>,{" "}
        <code className="bg-[var(--maroon-light)] text-[var(--maroon-dark)] px-1.5 py-0.5 rounded">amount</code>, and optionally{" "}
        <code className="bg-[var(--maroon-light)] text-[var(--maroon-dark)] px-1.5 py-0.5 rounded">kind</code> (SAVINGS or LOAN_REPAYMENT, default SAVINGS).
      </p>

      <form onSubmit={onSubmit} className="card p-6 space-y-4">
        <div>
          <label className="field-label">Period (YYYY-MM)</label>
          <input value={periodMonth} onChange={(e) => setPeriodMonth(e.target.value)} placeholder="2026-09" required className="field-input" />
        </div>
        <div>
          <label className="field-label">Excel file (.xlsx)</label>
          <input ref={fileRef} type="file" accept=".xlsx,.xls" required className="text-sm" />
        </div>
        {error && <p className="alert-error">{error}</p>}
        {result && (
          <p className="alert-success">
            Processed {result.totalRows} rows, matched {result.matchedRows}, total {"₦" + result.totalAmount.toLocaleString("en-NG")}.
          </p>
        )}
        <button type="submit" disabled={submitting} className="btn btn-primary">
          {submitting ? "Uploading..." : "Upload"}
        </button>
      </form>
    </div>
  );
}

export default function AdminContributionsPage() {
  return (
    <RequireAuth staffOnly>
      <AdminContributionsContent />
    </RequireAuth>
  );
}
