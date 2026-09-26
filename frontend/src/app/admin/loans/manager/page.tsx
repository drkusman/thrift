"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { MemberSearchSelect } from "@/components/MemberSearchSelect";
import { LiquidatePanel } from "@/components/LiquidatePanel";
import { LoanHistoryPanel } from "@/components/LoanHistoryPanel";
import { api, ApiError } from "@/lib/api";
import { Loan, LoanType, Member, MemberOption } from "@/lib/types";
import { formatNaira, statusBadgeClass } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

/** RUNNING loans first (newest disbursed/applied first within each group), then PULSED the same way -
 *  everything else (a member's completed/rejected/etc. loans) is left out of this grid entirely. */
function sortForManager(loans: Loan[]): Loan[] {
  const rank = (status: Loan["status"]) => (status === "RUNNING" ? 0 : status === "PULSED" ? 1 : 2);
  return loans
    .filter((l) => l.status === "RUNNING" || l.status === "PULSED")
    .sort((a, b) => {
      const r = rank(a.status) - rank(b.status);
      if (r !== 0) return r;
      return (b.appliedAt ?? "").localeCompare(a.appliedAt ?? "");
    });
}

function AdminLoanManagerContent() {
  const guard = useSubmitGuard();
  const [activeMembers, setActiveMembers] = useState<MemberOption[]>([]);
  const [memberId, setMemberId] = useState<number | "">("");
  const [member, setMember] = useState<Member | null>(null);
  const [loans, setLoans] = useState<Loan[] | null>(null);
  const [loanTypesById, setLoanTypesById] = useState<Record<number, LoanType>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [liquidatingLoan, setLiquidatingLoan] = useState<Loan | null>(null);
  const [historyLoan, setHistoryLoan] = useState<Loan | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => {
    api.get<MemberOption[]>("/api/members/active").then(setActiveMembers);
    api.get<LoanType[]>("/api/loan-types").then((rows) => {
      setLoanTypesById(Object.fromEntries(rows.map((t) => [t.id, t])));
    });
  }, []);

  function load() {
    if (memberId === "") return;
    setError(null);
    setLoading(true);
    Promise.all([
      api.get<Member>(`/api/admin/members/${memberId}`),
      api.get<Loan[]>(`/api/admin/loans/member/${memberId}`),
    ])
      .then(([m, allLoans]) => {
        setMember(m);
        setLoans(sortForManager(allLoans));
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load this member's loans."))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    setLoans(null);
    setMember(null);
    setLiquidatingLoan(null);
    setHistoryLoan(null);
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [memberId]);

  async function onPulse(loanId: number) {
    await guard(async () => {
      setError(null);
      setBusyId(loanId);
      try {
        await api.post(`/api/admin/loans/${loanId}/pulse`);
        load();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not pulse this loan.");
      } finally {
        setBusyId(null);
      }
    });
  }

  async function onResume(loanId: number) {
    await guard(async () => {
      setError(null);
      setBusyId(loanId);
      try {
        await api.post(`/api/admin/loans/${loanId}/resume`);
        load();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not resume this loan.");
      } finally {
        setBusyId(null);
      }
    });
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Loan Manager</h1>
      <p className="text-sm text-[var(--muted)] max-w-2xl">
        Pick a member to see their running and pulsed loans - liquidate one (in full or in part), pulse a
        running loan to pause its monthly deduction, or resume a pulsed one.
      </p>

      <div className="card p-6 max-w-lg">
        <label className="field-label">Member</label>
        <MemberSearchSelect options={activeMembers} value={memberId} onChange={setMemberId} placeholder="Search active members by name or regno..." />
      </div>

      {error && <p className="alert-error max-w-md">{error}</p>}

      {liquidatingLoan && (
        <LiquidatePanel
          loan={liquidatingLoan}
          member={member ?? undefined}
          onCancel={() => setLiquidatingLoan(null)}
          onDone={() => { setLiquidatingLoan(null); load(); }}
        />
      )}

      {historyLoan && !liquidatingLoan && (
        <LoanHistoryPanel loan={historyLoan} onClose={() => setHistoryLoan(null)} />
      )}

      {memberId !== "" && !liquidatingLoan && !historyLoan && (
        <div className="card overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-[var(--line)] text-left text-[var(--muted)]">
                <th className="p-3">Loan code</th>
                <th className="p-3">Loan type</th>
                <th className="p-3 text-right">Amount</th>
                <th className="p-3 text-right">Monthly repayment</th>
                <th className="p-3 text-right">Balance</th>
                <th className="p-3">Status</th>
                <th className="p-3"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--line)]">
              {(loans ?? []).map((l) => {
                const type = loanTypesById[l.loanTypeId];
                return (
                  <tr key={l.id}>
                    <td className="p-3">{l.loanCode ?? `#${l.id}`}</td>
                    <td className="p-3">{type?.name ?? "-"}</td>
                    <td className="p-3 text-right">{formatNaira(l.requestedAmount)}</td>
                    <td className="p-3 text-right">{formatNaira(l.monthlyRepaymentAmount)}</td>
                    <td className="p-3 text-right">{formatNaira(l.balance)}</td>
                    <td className="p-3"><span className={statusBadgeClass(l.status)}>{l.status}</span></td>
                    <td className="p-3">
                      <div className="flex gap-2 justify-end">
                        {l.status === "RUNNING" && (
                          <>
                            {(l.balance ?? 0) > 0 && (
                              <button onClick={() => setLiquidatingLoan(l)} className="btn btn-gold text-xs">Liquidate</button>
                            )}
                            <button onClick={() => onPulse(l.id)} disabled={busyId === l.id} className="btn btn-secondary text-xs">
                              {busyId === l.id ? "..." : "Pulse"}
                            </button>
                          </>
                        )}
                        {l.status === "PULSED" && (
                          <button onClick={() => onResume(l.id)} disabled={busyId === l.id} className="btn btn-primary text-xs">
                            {busyId === l.id ? "..." : "Resume"}
                          </button>
                        )}
                        <button onClick={() => setHistoryLoan(l)} className="btn btn-secondary text-xs">History</button>
                      </div>
                    </td>
                  </tr>
                );
              })}
              {!loading && loans !== null && loans.length === 0 && (
                <tr><td colSpan={7} className="p-4 text-center text-[var(--muted)]">This member has no running or pulsed loans.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default function AdminLoanManagerPage() {
  return (
    <RequireAuth staffOnly>
      <AdminLoanManagerContent />
    </RequireAuth>
  );
}
