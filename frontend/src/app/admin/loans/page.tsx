"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, ApiError } from "@/lib/api";
import { Loan, LoanType, Member } from "@/lib/types";
import { formatNaira, statusBadgeClass } from "@/lib/ui";

function guarantorLabel(member: Member | undefined) {
  return member ? `${member.fullName} (${member.regno})` : "-";
}

function AdminLoansContent() {
  const [loans, setLoans] = useState<Loan[]>([]);
  const [members, setMembers] = useState<Record<number, Member>>({});
  const [loanTypes, setLoanTypes] = useState<Record<number, LoanType>>({});
  const [error, setError] = useState<string | null>(null);

  function load() {
    api.get<Loan[]>("/api/admin/loans/pending").then(setLoans);
  }

  useEffect(() => {
    load();
    api.get<Member[]>("/api/admin/members").then((rows) => {
      setMembers(Object.fromEntries(rows.map((m) => [m.id, m])));
    });
    api.get<LoanType[]>("/api/loan-types").then((rows) => {
      setLoanTypes(Object.fromEntries(rows.map((t) => [t.id, t])));
    });
  }, []);

  async function approve(id: number) {
    setError(null);
    try {
      await api.post(`/api/admin/loans/${id}/approve`, {});
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not approve loan.");
    }
  }

  async function reject(id: number) {
    setError(null);
    try {
      await api.post(`/api/admin/loans/${id}/reject`, {});
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not reject loan.");
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Pending loan applications</h1>
      {error && <p className="alert-error max-w-md">{error}</p>}

      <div className="card divide-y divide-[var(--line)]">
        {loans.map((l) => {
          const member = members[l.memberId];
          const type = loanTypes[l.loanTypeId];
          const bothAccepted = l.guarantorOneStatus === "ACCEPTED" && l.guarantorTwoStatus === "ACCEPTED";
          return (
            <div key={l.id} className="p-4 flex items-center justify-between gap-3 flex-wrap">
              <div>
                <p className="font-semibold text-[var(--ink)]">
                  {member ? `${member.fullName} (${member.regno})` : `Member #${l.memberId}`} &middot; {type?.name ?? ""}
                </p>
                <p className="text-sm text-[var(--muted)]">
                  {formatNaira(l.requestedAmount)} over {l.durationMonths} months &middot; applied {l.appliedAt?.slice(0, 10)}
                </p>
                {l.reason && <p className="text-sm text-[var(--muted)] italic">&ldquo;{l.reason}&rdquo;</p>}
                <div className="text-xs text-[var(--muted)] mt-1 space-y-0.5">
                  <p className="flex items-center gap-1.5">
                    Guarantor 1: {guarantorLabel(members[l.guarantorOneId ?? -1])}
                    <span className={statusBadgeClass(l.guarantorOneStatus)}>{l.guarantorOneStatus}</span>
                  </p>
                  <p className="flex items-center gap-1.5">
                    Guarantor 2: {guarantorLabel(members[l.guarantorTwoId ?? -1])}
                    <span className={statusBadgeClass(l.guarantorTwoStatus)}>{l.guarantorTwoStatus}</span>
                  </p>
                </div>
                {!bothAccepted && (
                  <p className="text-xs text-[var(--maroon-dark)] font-medium mt-1">Cannot approve until both guarantors accept.</p>
                )}
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => approve(l.id)}
                  disabled={!bothAccepted}
                  title={bothAccepted ? undefined : "Both guarantors must accept first"}
                  className="btn btn-gold disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Approve &amp; disburse
                </button>
                <button onClick={() => reject(l.id)} className="btn btn-danger">Reject</button>
              </div>
            </div>
          );
        })}
        {loans.length === 0 && <p className="p-4 text-sm text-[var(--muted)]">No pending applications.</p>}
      </div>
    </div>
  );
}

export default function AdminLoansPage() {
  return (
    <RequireAuth staffOnly>
      <AdminLoansContent />
    </RequireAuth>
  );
}
