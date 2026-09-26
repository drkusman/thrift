"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, apiUrl, ApiError } from "@/lib/api";
import { Loan, LoanType, Member } from "@/lib/types";
import { formatNaira, statusBadgeClass } from "@/lib/ui";

const STATUSES: Loan["status"][] = [
  "PENDING", "APPROVED", "REJECTED", "DISBURSED", "RUNNING", "PULSED", "COMPLETED", "DEFAULTED",
];

function AdminLoanListContent() {
  const [status, setStatus] = useState<Loan["status"]>("PULSED");
  const [loans, setLoans] = useState<Loan[]>([]);
  const [members, setMembers] = useState<Record<number, Member>>({});
  const [loanTypesById, setLoanTypesById] = useState<Record<number, LoanType>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api.get<Member[]>("/api/admin/members").then((rows) => {
      setMembers(Object.fromEntries(rows.map((m) => [m.id, m])));
    });
    api.get<LoanType[]>("/api/loan-types").then((rows) => {
      setLoanTypesById(Object.fromEntries(rows.map((t) => [t.id, t])));
    });
  }, []);

  useEffect(() => {
    setError(null);
    setLoading(true);
    api.get<Loan[]>(`/api/admin/loans/by-status?status=${status}`)
      .then(setLoans)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load loans."))
      .finally(() => setLoading(false));
  }, [status]);

  const exportQuery = `status=${status}`;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Loans by status</h1>
        <div className="flex gap-2">
          <a href={apiUrl(`/api/admin/loans/export-by-status.xlsx?${exportQuery}`)} className="btn btn-secondary">Download (Excel)</a>
          <a href={apiUrl(`/api/admin/loans/export-by-status.pdf?${exportQuery}`)} className="btn btn-secondary">Download (PDF)</a>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        {STATUSES.map((s) => (
          <button
            key={s}
            onClick={() => setStatus(s)}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors ${
              status === s
                ? "bg-[var(--maroon-dark)] text-white border-[var(--maroon-dark)]"
                : "bg-white text-[var(--muted)] border-[var(--line)] hover:border-[var(--maroon)]"
            }`}
          >
            {s}
          </button>
        ))}
      </div>

      {error && <p className="alert-error max-w-md">{error}</p>}

      <div className="card overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-[var(--line)] text-left text-[var(--muted)]">
              <th className="p-3">Regno</th>
              <th className="p-3">Name</th>
              <th className="p-3">Loan type</th>
              <th className="p-3 text-right">Amount</th>
              <th className="p-3 text-right">Monthly repayment</th>
              <th className="p-3 text-right">Balance</th>
              <th className="p-3">Status</th>
              <th className="p-3">Applied</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--line)]">
            {loans.map((l) => {
              const member = members[l.memberId];
              const type = loanTypesById[l.loanTypeId];
              return (
                <tr key={l.id}>
                  <td className="p-3">{member?.regno ?? "-"}</td>
                  <td className="p-3">{member?.fullName ?? `Member #${l.memberId}`}</td>
                  <td className="p-3">{type?.name ?? "-"}</td>
                  <td className="p-3 text-right">{formatNaira(l.requestedAmount)}</td>
                  <td className="p-3 text-right">{formatNaira(l.monthlyRepaymentAmount)}</td>
                  <td className="p-3 text-right">{formatNaira(l.balance)}</td>
                  <td className="p-3"><span className={statusBadgeClass(l.status)}>{l.status}</span></td>
                  <td className="p-3">{l.appliedAt?.slice(0, 10) ?? "-"}</td>
                </tr>
              );
            })}
            {!loading && loans.length === 0 && (
              <tr><td colSpan={8} className="p-4 text-center text-[var(--muted)]">No {status.toLowerCase()} loans.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default function AdminLoanListPage() {
  return (
    <RequireAuth staffOnly>
      <AdminLoanListContent />
    </RequireAuth>
  );
}
