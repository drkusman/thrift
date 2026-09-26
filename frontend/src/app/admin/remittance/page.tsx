"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, apiUrl, ApiError } from "@/lib/api";
import { formatNaira } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

type RemittanceRow = { regno: string; fullName: string; monthlySavings: number; loanRepayment: number; total: number };

const MONTHS = [
  "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
  "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
];

function AdminRemittanceContent() {
  const guard = useSubmitGuard();
  const now = new Date();
  const [payPoints, setPayPoints] = useState<string[]>([]);
  const [payPoint, setPayPoint] = useState("");
  const [year, setYear] = useState(now.getFullYear());
  const [monthIndex, setMonthIndex] = useState(now.getMonth());
  const [rows, setRows] = useState<RemittanceRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api.get<string[]>("/api/admin/remittance/pay-points").then(setPayPoints);
  }, []);

  async function onGenerate(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null); setRows(null);
      if (!payPoint) { setError("Choose a pay point."); return; }
      setLoading(true);
      try {
        const res = await api.get<RemittanceRow[]>(`/api/admin/remittance?payPoint=${encodeURIComponent(payPoint)}`);
        setRows(res);
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not generate the schedule.");
      } finally {
        setLoading(false);
      }
    });
  }

  const exportQuery = `payPoint=${encodeURIComponent(payPoint)}&year=${year}&month=${monthIndex + 1}`;
  const totalSavings = rows?.reduce((sum, r) => sum + r.monthlySavings, 0) ?? 0;
  const totalLoan = rows?.reduce((sum, r) => sum + r.loanRepayment, 0) ?? 0;
  const grandTotal = rows?.reduce((sum, r) => sum + r.total, 0) ?? 0;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Generate remittance schedule</h1>

      <form onSubmit={onGenerate} className="card p-6 space-y-4 max-w-lg">
        <div>
          <label className="field-label">Pay point</label>
          <select value={payPoint} onChange={(e) => setPayPoint(e.target.value)} required className="field-input">
            <option value="">Select...</option>
            {payPoints.map((p) => <option key={p} value={p}>{p}</option>)}
          </select>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="field-label">Year</label>
            <select value={year} onChange={(e) => setYear(Number(e.target.value))} className="field-input">
              {Array.from({ length: 6 }, (_, i) => now.getFullYear() - 2 + i).map((y) => (
                <option key={y} value={y}>{y}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="field-label">Month</label>
            <select value={monthIndex} onChange={(e) => setMonthIndex(Number(e.target.value))} className="field-input">
              {MONTHS.map((m, i) => <option key={m} value={i}>{m}</option>)}
            </select>
          </div>
        </div>
        {error && <p className="alert-error">{error}</p>}
        <button type="submit" disabled={loading} className="btn btn-primary">
          {loading ? "Generating..." : "Next"}
        </button>
      </form>

      {rows && (
        <div className="space-y-4">
          <div className="flex items-center justify-between flex-wrap gap-3">
            <h2 className="font-semibold text-[var(--ink)]">
              {payPoint} &middot; {MONTHS[monthIndex]} {year} &middot; {rows.length} members
            </h2>
            <div className="flex gap-2">
              <a href={apiUrl(`/api/admin/remittance/export.xlsx?${exportQuery}`)} className="btn btn-secondary">Download (Excel)</a>
              <a href={apiUrl(`/api/admin/remittance/export.pdf?${exportQuery}`)} className="btn btn-secondary">Download (PDF)</a>
            </div>
          </div>

          <div className="card overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[var(--line)] text-left text-[var(--muted)]">
                  <th className="p-3">Regno</th>
                  <th className="p-3">Name</th>
                  <th className="p-3 text-right">Monthly savings</th>
                  <th className="p-3 text-right">Loan repayment</th>
                  <th className="p-3 text-right">Total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--line)]">
                {rows.map((r) => (
                  <tr key={r.regno}>
                    <td className="p-3">{r.regno}</td>
                    <td className="p-3">{r.fullName}</td>
                    <td className="p-3 text-right">{formatNaira(r.monthlySavings)}</td>
                    <td className="p-3 text-right">{formatNaira(r.loanRepayment)}</td>
                    <td className="p-3 text-right font-medium">{formatNaira(r.total)}</td>
                  </tr>
                ))}
                {rows.length === 0 && (
                  <tr><td colSpan={5} className="p-4 text-center text-[var(--muted)]">No active members at this pay point.</td></tr>
                )}
              </tbody>
              {rows.length > 0 && (
                <tfoot>
                  <tr className="border-t border-[var(--line)] font-bold">
                    <td className="p-3" colSpan={2}>TOTAL</td>
                    <td className="p-3 text-right">{formatNaira(totalSavings)}</td>
                    <td className="p-3 text-right">{formatNaira(totalLoan)}</td>
                    <td className="p-3 text-right">{formatNaira(grandTotal)}</td>
                  </tr>
                </tfoot>
              )}
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

export default function AdminRemittancePage() {
  return (
    <RequireAuth staffOnly>
      <AdminRemittanceContent />
    </RequireAuth>
  );
}
