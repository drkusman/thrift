"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { RequireAuth } from "@/components/RequireAuth";
import { MemberSearchSelect } from "@/components/MemberSearchSelect";
import { LiquidatePanel } from "@/components/LiquidatePanel";
import { api, ApiError } from "@/lib/api";
import { Loan, LoanType, Member, MemberOption, MembershipWithdrawalSummary, MembershipWithdrawalRequest } from "@/lib/types";
import { formatNaira } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

function AdminWithdrawalModuleContent() {
  const searchParams = useSearchParams();
  const guard = useSubmitGuard();
  const [activeMembers, setActiveMembers] = useState<MemberOption[]>([]);
  const [memberId, setMemberId] = useState<number | "">(() => {
    const q = searchParams.get("memberId");
    return q ? Number(q) : "";
  });
  const [member, setMember] = useState<Member | null>(null);
  const [loans, setLoans] = useState<Loan[] | null>(null);
  const [loanTypesById, setLoanTypesById] = useState<Record<number, LoanType>>({});
  const [summary, setSummary] = useState<MembershipWithdrawalSummary | null>(null);
  const [pendingRequest, setPendingRequest] = useState<MembershipWithdrawalRequest | null>(null);
  const [liquidatingLoan, setLiquidatingLoan] = useState<Loan | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [withdrawing, setWithdrawing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  useEffect(() => {
    api.get<MemberOption[]>("/api/members/active").then(setActiveMembers);
    api.get<LoanType[]>("/api/loan-types").then((rows) => {
      setLoanTypesById(Object.fromEntries(rows.map((t) => [t.id, t])));
    });
  }, []);

  function load() {
    if (memberId === "") return;
    setError(null);
    Promise.all([
      api.get<Member>(`/api/admin/members/${memberId}`),
      api.get<Loan[]>(`/api/admin/loans/member/${memberId}`),
      api.get<MembershipWithdrawalSummary>(`/api/admin/members/${memberId}/withdrawal-summary`),
      api.get<MembershipWithdrawalRequest[]>("/api/admin/members/withdrawal-requests/pending"),
    ])
      .then(([m, allLoans, s, pending]) => {
        setMember(m);
        setLoans(allLoans.filter((l) => l.status === "RUNNING" || l.status === "PULSED"));
        setSummary(s);
        setPendingRequest(pending.find((r) => r.memberId === memberId) ?? null);
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load this member."));
  }

  useEffect(() => {
    setLoans(null);
    setMember(null);
    setSummary(null);
    setPendingRequest(null);
    setLiquidatingLoan(null);
    setConfirming(false);
    setSuccessMsg(null);
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [memberId]);

  async function onWithdraw() {
    await guard(async () => {
      setError(null);
      setWithdrawing(true);
      try {
        if (pendingRequest) {
          await api.post(`/api/admin/members/withdrawal-requests/${pendingRequest.id}/approve`);
        } else {
          await api.post(`/api/admin/members/${memberId}/withdraw`);
        }
        setConfirming(false);
        setSuccessMsg(`${member?.fullName ?? "This member"} has been withdrawn.`);
        load();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not process this withdrawal.");
      } finally {
        setWithdrawing(false);
      }
    });
  }

  const today = new Date().toISOString().slice(0, 10);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Membership Withdrawal</h1>
      <p className="text-sm text-[var(--muted)] max-w-2xl">
        Pick a member to see their running/pulsed loans and their withdrawal summary. Every loan must be
        liquidated to zero before the final withdrawal can be processed.
      </p>

      <div className="card p-6 max-w-lg">
        <label className="field-label">Member</label>
        <MemberSearchSelect options={activeMembers} value={memberId} onChange={setMemberId} placeholder="Search active members by name or regno..." />
      </div>

      {error && <p className="alert-error max-w-md">{error}</p>}
      {successMsg && <p className="alert-success max-w-md">{successMsg}</p>}

      {member && (
        <>
          <h2 className="font-semibold text-[var(--ink)]">
            Withdrawal module for {member.fullName} ({member.regno}) as at {today}
          </h2>

          {pendingRequest && (
            <p className="text-sm text-[var(--maroon-dark)] font-medium">
              This member has a pending withdrawal request from {pendingRequest.requestedAt.slice(0, 10)} - the button below will approve it.
            </p>
          )}

          {liquidatingLoan ? (
            <LiquidatePanel
              loan={liquidatingLoan}
              member={member}
              onCancel={() => setLiquidatingLoan(null)}
              onDone={() => { setLiquidatingLoan(null); load(); }}
            />
          ) : (
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
                        <td className="p-3">{l.status}</td>
                        <td className="p-3">
                          {l.status === "RUNNING" && (l.balance ?? 0) > 0 && (
                            <button onClick={() => setLiquidatingLoan(l)} className="btn btn-gold text-xs">Liquidate</button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                  {loans !== null && loans.length === 0 && (
                    <tr><td colSpan={7} className="p-4 text-center text-[var(--muted)]">No running or pulsed loans - clear to withdraw.</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          )}

          {summary && (
            <div className="card overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-[var(--line)] text-left text-[var(--muted)]">
                    <th className="p-3">Total savings</th>
                    <th className="p-3">Total loan</th>
                    <th className="p-3">Balance</th>
                    <th className="p-3">COT</th>
                    <th className="p-3">Withdrawable</th>
                    <th className="p-3"></th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td className="p-3">{formatNaira(summary.totalSavings)}</td>
                    <td className="p-3">{formatNaira(summary.totalLoan)}</td>
                    <td className="p-3">{formatNaira(summary.balance)}</td>
                    <td className="p-3">{formatNaira(summary.cot)}</td>
                    <td className="p-3 font-semibold text-[var(--maroon-dark)]">{formatNaira(summary.withdrawableAmount)}</td>
                    <td className="p-3">
                      {!confirming ? (
                        <button
                          onClick={() => setConfirming(true)}
                          disabled={!summary.canWithdraw || summary.balance <= 0}
                          className="btn btn-danger disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          Withdraw
                        </button>
                      ) : (
                        <div className="flex gap-2 items-center">
                          <span className="text-xs text-[var(--muted)]">Pay out {formatNaira(summary.withdrawableAmount)} and close this account?</span>
                          <button onClick={onWithdraw} disabled={withdrawing} className="btn btn-danger text-xs">
                            {withdrawing ? "Processing..." : "Confirm"}
                          </button>
                          <button onClick={() => setConfirming(false)} className="text-xs text-[var(--muted)] hover:underline">Cancel</button>
                        </div>
                      )}
                    </td>
                  </tr>
                </tbody>
              </table>
              {!summary.canWithdraw && (
                <p className="p-3 text-xs text-[var(--muted)]">Every loan must be liquidated to zero before this member can be withdrawn.</p>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}

export default function AdminWithdrawalModulePage() {
  return (
    <RequireAuth staffOnly>
      <Suspense>
        <AdminWithdrawalModuleContent />
      </Suspense>
    </RequireAuth>
  );
}
