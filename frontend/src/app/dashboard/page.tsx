"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { RequireAuth } from "@/components/RequireAuth";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api";
import { Balance, LedgerEntry } from "@/lib/types";

function formatNaira(n: number) {
  return "₦" + n.toLocaleString("en-NG");
}

function DashboardContent() {
  const { member } = useAuth();
  const [balance, setBalance] = useState<Balance | null>(null);
  const [recent, setRecent] = useState<LedgerEntry[]>([]);

  useEffect(() => {
    api.get<Balance>("/api/me/balance").then(setBalance);
    api.get<LedgerEntry[]>("/api/me/transactions").then((rows) => setRecent(rows.slice(0, 8)));
  }, []);

  return (
    <div className="space-y-8">
      <div className="rounded-2xl bg-gradient-to-br from-[var(--maroon)] to-[var(--maroon-dark)] text-white px-6 py-7 shadow-[var(--shadow-lg)]">
        <p className="text-xs font-bold tracking-[0.15em] uppercase text-[var(--gold)]">Welcome back</p>
        <h1 className="text-2xl font-semibold mt-1">{member?.fullName}</h1>
        <p className="text-sm text-white/75 mt-1">{member?.regno} &middot; {member?.deptCode}</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="stat-tile">
          <p className="stat-label">Savings balance</p>
          <p className="stat-value">{balance ? formatNaira(balance.savingsBalance) : "..."}</p>
        </div>
        <div className="stat-tile">
          <p className="stat-label">Outstanding loan</p>
          <p className="stat-value">{balance ? formatNaira(balance.outstandingLoanBalance) : "..."}</p>
        </div>
        <div className="stat-tile">
          <p className="stat-label">Monthly savings</p>
          <p className="stat-value">{member ? formatNaira(member.monthlySavingsAmount) : "..."}</p>
        </div>
      </div>

      <div className="flex gap-3 flex-wrap">
        <Link href="/dashboard/loans" className="btn btn-primary">Apply for a loan</Link>
        <Link href="/dashboard/settings" className="btn btn-secondary">Update savings / bank details</Link>
      </div>

      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-lg font-semibold text-[var(--ink)]">Recent transactions</h2>
          <Link href="/dashboard/transactions" className="text-sm font-medium text-[var(--maroon)] hover:underline">View all</Link>
        </div>
        <div className="card overflow-hidden">
          <table className="table-elegant">
            <thead>
              <tr>
                <th>Date</th>
                <th>Description</th>
                <th>Category</th>
                <th className="text-right">Amount</th>
              </tr>
            </thead>
            <tbody>
              {recent.map((e) => (
                <tr key={e.id}>
                  <td>{e.date}</td>
                  <td>{e.description}</td>
                  <td><span className="badge badge-grey">{e.transCat}</span></td>
                  <td className={`text-right font-semibold ${e.drCrStatus === "CR" ? "text-emerald-700" : "text-rose-700"}`}>
                    {e.drCrStatus === "CR" ? "+" : "-"}{formatNaira(e.amount)}
                  </td>
                </tr>
              ))}
              {recent.length === 0 && (
                <tr><td colSpan={4} className="text-center py-8 text-[var(--muted)]">No transactions yet.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  return (
    <RequireAuth>
      <DashboardContent />
    </RequireAuth>
  );
}
