"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, apiUrl, ApiError } from "@/lib/api";
import { formatNaira } from "@/lib/ui";

type MemberBalanceRow = {
  memberId: number;
  regno: string;
  fullName: string;
  savingsBalance: number;
  loanBalance: number;
  netEquity: number;
};

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function AdminMemberBalancesContent() {
  const [asOfDate, setAsOfDate] = useState(todayIso());
  const [rows, setRows] = useState<MemberBalanceRow[] | null>(null);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function run() {
    setError(null);
    setLoading(true);
    api.get<MemberBalanceRow[]>(`/api/admin/member-balances?asOfDate=${asOfDate}`)
      .then(setRows)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load the report."))
      .finally(() => setLoading(false));
  }

  useEffect(run, []);

  const filtered = (rows ?? []).filter((r) => {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    return r.regno.toLowerCase().includes(q) || r.fullName.toLowerCase().includes(q);
  });

  const totals = filtered.reduce(
    (acc, r) => ({
      savings: acc.savings + r.savingsBalance,
      loan: acc.loan + r.loanBalance,
      equity: acc.equity + r.netEquity,
    }),
    { savings: 0, loan: 0, equity: 0 }
  );

  const exportQuery = `asOfDate=${asOfDate}`;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Member balances</h1>
        <div className="flex gap-2">
          <a href={apiUrl(`/api/admin/member-balances/export.xlsx?${exportQuery}`)} className="btn btn-secondary">Download (Excel)</a>
          <a href={apiUrl(`/api/admin/member-balances/export.pdf?${exportQuery}`)} className="btn btn-secondary">Download (PDF)</a>
        </div>
      </div>
      <p className="text-sm text-[var(--muted)] max-w-2xl">
        Every active member&rsquo;s savings and loan balance, computed from every transaction on record up
        to and including the date chosen below - e.g. a fiscal year-end like 2026-10-31.
      </p>

      <div className="card p-4 flex flex-wrap items-end gap-3">
        <div>
          <label className="field-label">As at date</label>
          <input type="date" value={asOfDate} max={todayIso()} onChange={(e) => setAsOfDate(e.target.value)} className="field-input" />
        </div>
        <button onClick={run} disabled={loading} className="btn btn-primary">
          {loading ? "Running..." : "Run report"}
        </button>
        <div className="flex-1 min-w-[200px]">
          <label className="field-label">Search</label>
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Filter by regno or name..."
            className="field-input"
          />
        </div>
      </div>

      {error && <p className="alert-error max-w-md">{error}</p>}

      <div className="card overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-[var(--line)] text-left text-[var(--muted)]">
              <th className="p-3">Regno</th>
              <th className="p-3">Name</th>
              <th className="p-3 text-right">Savings balance</th>
              <th className="p-3 text-right">Loan balance</th>
              <th className="p-3 text-right">Net equity</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--line)]">
            {filtered.map((r) => (
              <tr key={r.memberId}>
                <td className="p-3">{r.regno}</td>
                <td className="p-3">{r.fullName}</td>
                <td className="p-3 text-right">{formatNaira(r.savingsBalance)}</td>
                <td className="p-3 text-right">{formatNaira(r.loanBalance)}</td>
                <td className="p-3 text-right font-semibold">{formatNaira(r.netEquity)}</td>
              </tr>
            ))}
            {!loading && filtered.length === 0 && (
              <tr><td colSpan={5} className="p-4 text-center text-[var(--muted)]">No active members match.</td></tr>
            )}
          </tbody>
          {filtered.length > 0 && (
            <tfoot>
              <tr className="border-t border-[var(--line)] font-semibold">
                <td className="p-3" colSpan={2}>Total ({filtered.length})</td>
                <td className="p-3 text-right">{formatNaira(totals.savings)}</td>
                <td className="p-3 text-right">{formatNaira(totals.loan)}</td>
                <td className="p-3 text-right">{formatNaira(totals.equity)}</td>
              </tr>
            </tfoot>
          )}
        </table>
      </div>
    </div>
  );
}

export default function AdminMemberBalancesPage() {
  return (
    <RequireAuth staffOnly>
      <AdminMemberBalancesContent />
    </RequireAuth>
  );
}
