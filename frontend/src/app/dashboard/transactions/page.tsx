"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, apiUrl } from "@/lib/api";
import { LedgerEntry } from "@/lib/types";

function formatNaira(n: number) {
  return "₦" + n.toLocaleString("en-NG");
}

function TransactionsContent() {
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get<LedgerEntry[]>("/api/me/transactions").then((rows) => {
      setEntries(rows);
      setLoading(false);
    });
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Transaction history</h1>
        <div className="flex gap-2">
          <a href={apiUrl("/api/me/transactions/export.xlsx")} className="btn btn-secondary">Download Excel</a>
          <a href={apiUrl("/api/me/transactions/export.pdf")} className="btn btn-secondary">Download PDF</a>
        </div>
      </div>

      <div className="card overflow-hidden">
        <table className="table-elegant">
          <thead>
            <tr>
              <th>Date</th>
              <th>Description</th>
              <th>Category</th>
              <th>DR/CR</th>
              <th className="text-right">Amount</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((e) => (
              <tr key={e.id}>
                <td>{e.date}</td>
                <td>{e.description}</td>
                <td><span className="badge badge-grey">{e.transCat}</span></td>
                <td>{e.drCrStatus}</td>
                <td className={`text-right font-semibold ${e.drCrStatus === "CR" ? "text-emerald-700" : "text-rose-700"}`}>
                  {formatNaira(e.amount)}
                </td>
              </tr>
            ))}
            {!loading && entries.length === 0 && (
              <tr><td colSpan={5} className="text-center py-8 text-[var(--muted)]">No transactions yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default function TransactionsPage() {
  return (
    <RequireAuth>
      <TransactionsContent />
    </RequireAuth>
  );
}
