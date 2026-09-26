"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, apiUrl, ApiError } from "@/lib/api";
import { formatNaira } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

type UploadResult = { batchId: number; totalRows: number; matchedRows: number; totalAmount: number };
type Batch = {
  id: number; periodMonth: string; fileName: string | null; uploadedAt: string;
  totalRows: number; matchedRows: number; totalAmount: number; hasFile: boolean; locksPeriod: boolean;
};

/** The thrift's fiscal year runs 1 November to 31 October (matching AdminAnalyticsService's own
 *  currentFiscalYearRange) - the period picker is restricted to the 12 months of the current FY,
 *  most recent (October) first, so it can't be used to post against an out-of-year period by mistake. */
function monthOptions() {
  const now = new Date();
  const fyStartYear = now.getMonth() + 1 >= 11 ? now.getFullYear() : now.getFullYear() - 1;
  const options: { value: string; label: string }[] = [];
  for (let monthsFromStart = 11; monthsFromStart >= 0; monthsFromStart--) {
    const d = new Date(fyStartYear, 10 + monthsFromStart, 1); // 10 = November (0-indexed)
    const lastDay = new Date(d.getFullYear(), d.getMonth() + 1, 0);
    const value = `${lastDay.getFullYear()}-${String(lastDay.getMonth() + 1).padStart(2, "0")}`;
    const label = lastDay.toLocaleDateString("en-GB", { day: "2-digit", month: "long", year: "numeric" });
    options.push({ value, label });
  }
  return options;
}

function AdminContributionsContent() {
  const guard = useSubmitGuard();
  const options = useMemo(monthOptions, []);
  const [periodMonth, setPeriodMonth] = useState("");
  const [isIos, setIsIos] = useState(false);
  const [uploadedPeriods, setUploadedPeriods] = useState<Set<string>>(new Set());
  const [batches, setBatches] = useState<Batch[]>([]);
  const [result, setResult] = useState<UploadResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  function refresh() {
    api.get<string[]>("/api/admin/contributions/periods").then((rows) => setUploadedPeriods(new Set(rows)));
    api.get<Batch[]>("/api/admin/contributions/batches").then(setBatches);
  }

  useEffect(refresh, []);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null); setResult(null);
      const file = fileRef.current?.files?.[0];
      if (!file || !periodMonth) { setError("Choose a period and a file."); return; }
      setSubmitting(true);
      try {
        const form = new FormData();
        form.append("file", file);
        const mainContribution = !isIos;
        const res = await api.postForm<UploadResult>(
          `/api/admin/contributions/upload?periodMonth=${encodeURIComponent(periodMonth)}&mainContribution=${mainContribution}`, form);
        setResult(res);
        setPeriodMonth("");
        if (fileRef.current) fileRef.current.value = "";
        refresh();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Upload failed.");
      } finally {
        setSubmitting(false);
      }
    });
  }

  async function onDelete(batch: Batch) {
    if (!confirm(`Delete the ${batch.periodMonth} upload (${batch.matchedRows} posted rows)? This reverses every ledger entry and schedule payment it created, and reopens the period for re-upload.`)) return;
    setError(null);
    setDeletingId(batch.id);
    try {
      await api.del(`/api/admin/contributions/batches/${batch.id}`);
      refresh();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not delete this batch.");
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <div className="space-y-6 max-w-2xl">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Monthly contribution upload</h1>
      <p className="text-sm text-[var(--muted)]">
        Upload an Excel sheet with columns <code className="bg-[var(--maroon-light)] text-[var(--maroon-dark)] px-1.5 py-0.5 rounded">SN</code>,{" "}
        <code className="bg-[var(--maroon-light)] text-[var(--maroon-dark)] px-1.5 py-0.5 rounded">REGNO</code>,{" "}
        <code className="bg-[var(--maroon-light)] text-[var(--maroon-dark)] px-1.5 py-0.5 rounded">NAME</code>,{" "}
        <code className="bg-[var(--maroon-light)] text-[var(--maroon-dark)] px-1.5 py-0.5 rounded">AMOUNT</code>, and{" "}
        <code className="bg-[var(--maroon-light)] text-[var(--maroon-dark)] px-1.5 py-0.5 rounded">KIND</code> - SAVINGS, LOAN_REPAYMENT, CASH DEPOSIT, REFUND OF OVER DEDUCTION, IOS1, or IOS2 (default SAVINGS).
      </p>
      <a href={apiUrl("/api/admin/contributions/template.xlsx")} className="text-[var(--maroon)] hover:underline text-sm font-medium">
        Download empty template (Excel)
      </a>

      <form onSubmit={onSubmit} className="card p-6 space-y-4 max-w-lg">
        <div>
          <label className="field-label">IOS?</label>
          <div className="flex gap-4 text-sm">
            <label className="flex items-center gap-1.5">
              <input type="radio" name="isIos" checked={!isIos} onChange={() => setIsIos(false)} />
              No - regular monthly contribution
            </label>
            <label className="flex items-center gap-1.5">
              <input type="radio" name="isIos" checked={isIos} onChange={() => setIsIos(true)} />
              Yes - IOS1, IOS2, Cash Deposit, or Refund
            </label>
          </div>
          <p className="text-xs text-[var(--muted)] mt-1">
            {isIos
              ? "This won't lock the period - it can be uploaded any number of times, even for a period that already has its main contribution posted."
              : "This locks the period against a double-upload, same as before."}
          </p>
        </div>
        <div>
          <label className="field-label">Period (month-end)</label>
          <select value={periodMonth} onChange={(e) => setPeriodMonth(e.target.value)} required className="field-input">
            <option value="">Select...</option>
            {options.map((o) => {
              const locked = !isIos && uploadedPeriods.has(o.value);
              return (
                <option key={o.value} value={o.value} disabled={locked}>
                  {o.label}{locked ? " (already uploaded)" : ""}
                </option>
              );
            })}
          </select>
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

      <div className="space-y-3">
        <h2 className="font-semibold text-[var(--ink)]">Uploaded periods</h2>
        <div className="card divide-y divide-[var(--line)]">
          {batches.map((b) => (
            <div key={b.id} className="p-4 flex items-center justify-between gap-3 flex-wrap">
              <div>
                <p className="font-semibold text-[var(--ink)] flex items-center gap-2">
                  {b.periodMonth} &middot; {b.fileName ?? "(no file name)"}
                  <span className={`badge ${b.locksPeriod ? "badge-grey" : "badge-gold"}`}>
                    {b.locksPeriod ? "Locks period" : "IOS / Correction"}
                  </span>
                </p>
                <p className="text-sm text-[var(--muted)]">
                  {b.matchedRows}/{b.totalRows} rows matched &middot; {formatNaira(b.totalAmount)} &middot; uploaded {b.uploadedAt.slice(0, 10)}
                </p>
              </div>
              <div className="flex gap-2">
                {b.hasFile && (
                  <a href={apiUrl(`/api/admin/contributions/batches/${b.id}/file`)} className="btn btn-secondary">
                    Download
                  </a>
                )}
                <button onClick={() => onDelete(b)} disabled={deletingId === b.id} className="btn btn-danger">
                  {deletingId === b.id ? "Deleting..." : "Delete"}
                </button>
              </div>
            </div>
          ))}
          {batches.length === 0 && <p className="p-4 text-sm text-[var(--muted)]">No uploads yet.</p>}
        </div>
      </div>
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
