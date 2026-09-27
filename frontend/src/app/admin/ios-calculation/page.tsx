"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { RequireAuth } from "@/components/RequireAuth";
import { api, apiUrl, ApiError } from "@/lib/api";
import { formatNaira } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

type PreviewRow = { memberId: number; regno: string; fullName: string; savingsBalance: number; iosAmount: number };
type Preview = {
  fiscalYearLabel: string;
  since: string;
  until: string;
  ratePercent: number;
  rows: PreviewRow[];
  totalAmount: number;
  alreadyRun: boolean;
  totalCooperativeIncome: number;
};

/** Current fiscal year and the previous one, matching the Income report's own pair - the thrift's fiscal
 *  year runs 1 November to 31 October. */
function fyOptions() {
  const now = new Date();
  const currentStartYear = now.getMonth() + 1 >= 11 ? now.getFullYear() : now.getFullYear() - 1;
  return [0, 1].map((yearsAgo) => {
    const startYear = currentStartYear - yearsAgo;
    const since = `${startYear}-11-01`;
    const label = `${startYear}/${startYear + 1}`;
    return { since, label };
  });
}

function AdminIosCalculationContent() {
  const guard = useSubmitGuard();
  const options = useMemo(fyOptions, []);
  const [since, setSince] = useState(options[0].since);
  const [ratePercent, setRatePercent] = useState("");
  const [preview, setPreview] = useState<Preview | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [postError, setPostError] = useState<string | null>(null);
  const [postResult, setPostResult] = useState<{ batchId: number; matchedRows: number; totalAmount: number } | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [posting, setPosting] = useState(false);

  async function onPreview(e: React.FormEvent) {
    e.preventDefault();
    setPreviewError(null); setPostError(null); setPostResult(null); setPreview(null);
    const rate = Number(ratePercent);
    if (!ratePercent || !Number.isFinite(rate) || rate <= 0) { setPreviewError("Enter a valid rate."); return; }
    setLoadingPreview(true);
    try {
      const res = await api.get<Preview>(`/api/admin/ios-calculation/preview?since=${since}&ratePercent=${rate}`);
      setPreview(res);
    } catch (e) {
      setPreviewError(e instanceof ApiError ? e.message : "Could not compute the preview.");
    } finally {
      setLoadingPreview(false);
    }
  }

  async function onConfirm() {
    if (!preview) return;
    await guard(async () => {
      setPostError(null);
      setPosting(true);
      try {
        const res = await api.post<{ batchId: number; matchedRows: number; totalAmount: number }>(
          "/api/admin/ios-calculation/post", { since: preview.since, ratePercent: preview.ratePercent });
        setPostResult(res);
      } catch (e) {
        setPostError(e instanceof ApiError ? e.message : "Could not post IOS.");
      } finally {
        setPosting(false);
      }
    });
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Interest on Savings calculation</h1>
      <p className="text-sm text-[var(--muted)] max-w-2xl">
        Automatically computes each active member&rsquo;s Interest on Savings (IOS) for a fiscal year -
        their savings balance at the close of that year, multiplied by the rate you set here - instead of
        calculating it externally and uploading it via Monthly Upload. Review the preview below before
        posting; posting credits every member at once (transType IOS1), exactly as a manual IOS1 upload
        would, and shows up in Monthly Upload&rsquo;s batch history, fully reversible from there.
      </p>

      <form onSubmit={onPreview} className="card p-6 space-y-4 max-w-lg">
        <div>
          <label className="field-label">Fiscal year</label>
          <select value={since} onChange={(e) => { setSince(e.target.value); setPreview(null); setPostResult(null); }} className="field-input">
            {options.map((o) => <option key={o.since} value={o.since}>FY {o.label}</option>)}
          </select>
        </div>
        <div>
          <label className="field-label">Interest rate (%)</label>
          <input type="number" min={0} step="0.01" value={ratePercent} onChange={(e) => { setRatePercent(e.target.value); setPreview(null); setPostResult(null); }} required className="field-input" />
        </div>
        {previewError && <p className="alert-error">{previewError}</p>}
        <button type="submit" disabled={loadingPreview} className="btn btn-primary">
          {loadingPreview ? "Calculating..." : "Preview"}
        </button>
      </form>

      {postResult && (
        <div className="alert-success max-w-lg space-y-2">
          <p>
            Posted IOS to {postResult.matchedRows} member(s), total {formatNaira(postResult.totalAmount)}.{" "}
            <Link href="/admin/contributions" className="underline font-medium">View in Monthly Upload</Link>.
          </p>
          <div className="flex gap-3">
            <a href={apiUrl(`/api/admin/ios-calculation/batch/${postResult.batchId}/export.xlsx`)} className="text-xs underline font-medium">
              Download Excel
            </a>
            <a href={apiUrl(`/api/admin/ios-calculation/batch/${postResult.batchId}/export.pdf`)} className="text-xs underline font-medium">
              Download PDF
            </a>
          </div>
        </div>
      )}

      {preview && !postResult && (
        <div className="space-y-3">
          {preview.alreadyRun && (
            <p className="alert-error max-w-2xl">
              IOS has already been posted for FY {preview.fiscalYearLabel} - delete that batch from{" "}
              <Link href="/admin/contributions" className="underline font-medium">Monthly Upload</Link> first if you need to redo it.
            </p>
          )}
          <div className="flex items-center justify-between flex-wrap gap-3">
            <div>
              <h2 className="font-semibold text-[var(--ink)]">
                FY {preview.fiscalYearLabel} at {preview.ratePercent}% - {preview.rows.length} member(s), total {formatNaira(preview.totalAmount)}
              </h2>
              <div className="flex gap-3 mt-1">
                <a href={apiUrl(`/api/admin/ios-calculation/export.xlsx?since=${preview.since}&ratePercent=${preview.ratePercent}`)} className="text-xs text-[var(--maroon)] hover:underline font-medium">
                  Download Excel
                </a>
                <a href={apiUrl(`/api/admin/ios-calculation/export.pdf?since=${preview.since}&ratePercent=${preview.ratePercent}`)} className="text-xs text-[var(--maroon)] hover:underline font-medium">
                  Download PDF
                </a>
              </div>
            </div>
            <button onClick={onConfirm} disabled={posting || preview.alreadyRun} className="btn btn-danger disabled:opacity-50 disabled:cursor-not-allowed">
              {posting ? "Posting..." : "Confirm & Post"}
            </button>
          </div>

          {preview.totalCooperativeIncome > 0 && (() => {
            const pct = (preview.totalAmount / preview.totalCooperativeIncome) * 100;
            const high = pct >= 40;
            return (
              <div className={`card p-4 max-w-2xl ${high ? "!border-[#f0c9cc]" : ""}`}>
                <p className="text-xs font-bold uppercase tracking-wide text-[var(--muted)] mb-2">
                  Against FY {preview.fiscalYearLabel} cooperative income
                </p>
                <div className="flex items-center gap-6 flex-wrap">
                  <div>
                    <p className="text-[10px] uppercase text-[var(--muted)]">Proposed IOS</p>
                    <p className="font-semibold text-[var(--ink)]">{formatNaira(preview.totalAmount)}</p>
                  </div>
                  <div>
                    <p className="text-[10px] uppercase text-[var(--muted)]">Total income (FY {preview.fiscalYearLabel})</p>
                    <p className="font-semibold text-[var(--ink)]">{formatNaira(preview.totalCooperativeIncome)}</p>
                  </div>
                  <div>
                    <p className="text-[10px] uppercase text-[var(--muted)]">Share of income</p>
                    <p className={`font-bold ${high ? "text-[#a3161d]" : "text-[var(--maroon-dark)]"}`}>{pct.toFixed(1)}%</p>
                  </div>
                </div>
                {high && (
                  <p className="text-xs text-[#a3161d] mt-2">
                    This payout would use up {pct.toFixed(1)}% of the year&rsquo;s total income - worth double-checking the rate before posting.
                  </p>
                )}
              </div>
            );
          })()}

          {postError && <p className="alert-error">{postError}</p>}

          <div className="card overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--line)] text-left text-[var(--muted)]">
                  <th className="p-3">Regno</th>
                  <th className="p-3">Name</th>
                  <th className="p-3 text-right">Savings balance</th>
                  <th className="p-3 text-right">IOS</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--line)]">
                {preview.rows.map((r) => (
                  <tr key={r.memberId}>
                    <td className="p-3">{r.regno}</td>
                    <td className="p-3">{r.fullName}</td>
                    <td className="p-3 text-right">{formatNaira(r.savingsBalance)}</td>
                    <td className="p-3 text-right font-semibold">{formatNaira(r.iosAmount)}</td>
                  </tr>
                ))}
                {preview.rows.length === 0 && (
                  <tr><td colSpan={4} className="p-4 text-center text-[var(--muted)]">No members have a positive savings balance for this fiscal year.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

export default function AdminIosCalculationPage() {
  return (
    <RequireAuth staffOnly>
      <AdminIosCalculationContent />
    </RequireAuth>
  );
}
