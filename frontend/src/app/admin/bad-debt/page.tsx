"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, ApiError } from "@/lib/api";
import { BadDebtRow } from "@/lib/types";
import { formatNaira } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

function RepaymentRow({ row, onRecorded }: { row: BadDebtRow; onRecorded: (rows: BadDebtRow[]) => void }) {
  const guard = useSubmitGuard();
  const [amount, setAmount] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null);
      const value = Number(amount);
      if (!value || value <= 0) { setError("Enter a valid repayment amount."); return; }
      try {
        const rows = await api.post<BadDebtRow[]>(`/api/admin/bad-debts/${row.badDebtId}/repayment`, { amount: value });
        setAmount("");
        onRecorded(rows);
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not record this repayment.");
      }
    });
  }

  return (
    <div className="p-4 flex items-center justify-between gap-3 flex-wrap">
      <div>
        <p className="font-semibold text-[var(--ink)]">{row.fullName} ({row.regno})</p>
        <p className="text-sm text-[var(--muted)]">
          Owed since {row.createdAt.slice(0, 10)} &middot; original {formatNaira(row.originalAmount)}
        </p>
        {error && <p className="alert-error text-sm mt-1">{error}</p>}
      </div>
      <div className="flex items-center gap-3">
        <p className="text-right">
          <span className="block text-xs text-[var(--muted)]">Currently owed</span>
          <span className="font-semibold text-[var(--maroon-dark)]">{formatNaira(row.currentlyOwed)}</span>
        </p>
        <form onSubmit={onSubmit} className="flex items-center gap-2">
          <input
            type="number"
            min={1}
            placeholder="Amount"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            className="field-input w-28 text-sm"
          />
          <button type="submit" className="btn btn-gold text-sm whitespace-nowrap">Record repayment</button>
        </form>
      </div>
    </div>
  );
}

function AdminBadDebtContent() {
  const [rows, setRows] = useState<BadDebtRow[]>([]);
  const [error, setError] = useState<string | null>(null);

  function load() {
    api.get<BadDebtRow[]>("/api/admin/bad-debts")
      .then(setRows)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load the bad debt list."));
  }

  useEffect(load, []);

  const total = rows.reduce((sum, r) => sum + r.currentlyOwed, 0);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Bad debt list</h1>
      <p className="text-sm text-[var(--muted)] max-w-2xl">
        Members who withdrew from the cooperative while owing more than they had saved. Even though their
        account is closed, they can still pay this back gradually - record a repayment as it comes in, and
        they&rsquo;re removed from this list automatically once fully repaid.
      </p>
      {rows.length > 0 && (
        <p className="text-sm font-semibold text-[var(--ink)]">
          {rows.length} outstanding &middot; {formatNaira(total)} owed in total
        </p>
      )}
      {error && <p className="alert-error max-w-md">{error}</p>}

      <div className="card divide-y divide-[var(--line)]">
        {rows.map((r) => (
          <RepaymentRow key={r.badDebtId} row={r} onRecorded={setRows} />
        ))}
        {rows.length === 0 && <p className="p-4 text-sm text-[var(--muted)]">No outstanding bad debt.</p>}
      </div>
    </div>
  );
}

export default function AdminBadDebtPage() {
  return (
    <RequireAuth staffOnly>
      <AdminBadDebtContent />
    </RequireAuth>
  );
}
