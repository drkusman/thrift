"use client";

import { useEffect, useState } from "react";
import { api, apiUrl, ApiError } from "@/lib/api";
import { Loan, LedgerEntry } from "@/lib/types";
import { formatNaira } from "@/lib/ui";

export function LoanHistoryPanel({ loan, onClose }: { loan: Loan; onClose: () => void }) {
  const [entries, setEntries] = useState<LedgerEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setEntries(null);
    setError(null);
    api.get<LedgerEntry[]>(`/api/admin/loans/${loan.id}/transactions`)
      .then((rows) => setEntries([...rows].sort((a, b) => a.date.localeCompare(b.date) || a.id - b.id)))
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load this loan's history."));
  }, [loan.id]);

  let running = 0;

  return (
    <div className="card p-6 space-y-4 border-2 border-[var(--maroon)]">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h2 className="font-semibold text-[var(--ink)]">
          History - {loan.loanCode ?? `Loan #${loan.id}`}
        </h2>
        <div className="flex gap-2">
          <a href={apiUrl(`/api/admin/loans/${loan.id}/transactions/export.xlsx`)} className="btn btn-secondary text-xs">Download Excel</a>
          <a href={apiUrl(`/api/admin/loans/${loan.id}/transactions/export.pdf`)} className="btn btn-secondary text-xs">Download PDF</a>
          <button onClick={onClose} className="text-sm text-[var(--muted)] hover:underline">Close</button>
        </div>
      </div>

      {error && <p className="alert-error">{error}</p>}

      <div className="overflow-x-auto">
        <table className="table-elegant">
          <thead>
            <tr>
              <th>Date</th>
              <th>Description</th>
              <th>Type</th>
              <th>DR/CR</th>
              <th className="text-right">Amount</th>
              <th className="text-right">Balance</th>
            </tr>
          </thead>
          <tbody>
            {(entries ?? []).map((e) => {
              running += e.drCrStatus === "CR" ? e.amount : -e.amount;
              return (
                <tr key={e.id}>
                  <td>{e.date}</td>
                  <td>{e.description}</td>
                  <td>{e.transType}</td>
                  <td>{e.drCrStatus}</td>
                  <td className={`text-right font-semibold ${e.drCrStatus === "CR" ? "text-emerald-700" : "text-rose-700"}`}>
                    {formatNaira(e.amount)}
                  </td>
                  <td className="text-right">{formatNaira(running)}</td>
                </tr>
              );
            })}
            {entries !== null && entries.length === 0 && (
              <tr><td colSpan={6} className="text-center py-8 text-[var(--muted)]">No transactions for this loan yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
