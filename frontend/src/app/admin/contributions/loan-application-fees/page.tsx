"use client";

import { useMemo, useRef, useState } from "react";
import Link from "next/link";
import { RequireAuth } from "@/components/RequireAuth";
import { api, apiUrl, ApiError } from "@/lib/api";
import { useSubmitGuard } from "@/lib/use-submit-guard";

type UploadResult = { batchId: number; totalRows: number; matchedRows: number; totalAmount: number };

/** Same FY-restricted picker as the general Monthly Upload page - the thrift's fiscal year runs
 *  1 November to 31 October. */
function monthOptions() {
  const now = new Date();
  const fyStartYear = now.getMonth() + 1 >= 11 ? now.getFullYear() : now.getFullYear() - 1;
  const options: { value: string; label: string }[] = [];
  for (let monthsFromStart = 11; monthsFromStart >= 0; monthsFromStart--) {
    const d = new Date(fyStartYear, 10 + monthsFromStart, 1);
    const lastDay = new Date(d.getFullYear(), d.getMonth() + 1, 0);
    const value = `${lastDay.getFullYear()}-${String(lastDay.getMonth() + 1).padStart(2, "0")}`;
    const label = lastDay.toLocaleDateString("en-GB", { day: "2-digit", month: "long", year: "numeric" });
    options.push({ value, label });
  }
  return options;
}

function AdminLoanApplicationFeesContent() {
  const guard = useSubmitGuard();
  const options = useMemo(monthOptions, []);
  const [periodMonth, setPeriodMonth] = useState("");
  const [result, setResult] = useState<UploadResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

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
        const res = await api.postForm<UploadResult>(
          `/api/admin/contributions/laf-upload?periodMonth=${encodeURIComponent(periodMonth)}`, form);
        setResult(res);
        setPeriodMonth("");
        if (fileRef.current) fileRef.current.value = "";
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Upload failed.");
      } finally {
        setSubmitting(false);
      }
    });
  }

  return (
    <div className="space-y-6 max-w-2xl">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Loan application fee upload</h1>
      <p className="text-sm text-[var(--muted)]">
        Most application fees (sale of application forms) are collected offline rather than through a
        member&rsquo;s own online loan application. Upload a batch here to capture those in the Income
        report - every row posts as a Loan Application Fee (LAF), a debit against that member&rsquo;s
        savings. This never locks a period - it&rsquo;s an ad-hoc event like IOS or a correction, so the
        same period can be uploaded again whenever more offline payments come in.
      </p>
      <p className="text-sm text-[var(--muted)]">
        Columns: <code className="bg-[var(--maroon-light)] text-[var(--maroon-dark)] px-1.5 py-0.5 rounded">SN</code>,{" "}
        <code className="bg-[var(--maroon-light)] text-[var(--maroon-dark)] px-1.5 py-0.5 rounded">REGNO</code>,{" "}
        <code className="bg-[var(--maroon-light)] text-[var(--maroon-dark)] px-1.5 py-0.5 rounded">NAME</code>, and{" "}
        <code className="bg-[var(--maroon-light)] text-[var(--maroon-dark)] px-1.5 py-0.5 rounded">AMOUNT</code> - no KIND
        column needed, every row is always a Loan Application Fee sale.
      </p>
      <a href={apiUrl("/api/admin/contributions/laf-template.xlsx")} className="text-[var(--maroon)] hover:underline text-sm font-medium">
        Download empty template (Excel)
      </a>

      <form onSubmit={onSubmit} className="card p-6 space-y-4 max-w-lg">
        <div>
          <label className="field-label">Period (month-end)</label>
          <select value={periodMonth} onChange={(e) => setPeriodMonth(e.target.value)} required className="field-input">
            <option value="">Select...</option>
            {options.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
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

      <p className="text-sm text-[var(--muted)]">
        To review or delete a past upload (including this one), see{" "}
        <Link href="/admin/contributions" className="text-[var(--maroon)] hover:underline font-medium">Monthly Upload</Link>
        {" "}- every batch, whatever its kind, shows up in that history list and can be reversed from there.
      </p>
    </div>
  );
}

export default function AdminLoanApplicationFeesPage() {
  return (
    <RequireAuth staffOnly>
      <AdminLoanApplicationFeesContent />
    </RequireAuth>
  );
}
