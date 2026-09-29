"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { RequireAuth } from "@/components/RequireAuth";
import { LiquidatePanel } from "@/components/LiquidatePanel";
import { MemberSearchSelect } from "@/components/MemberSearchSelect";
import { api, apiUrl, ApiError } from "@/lib/api";
import { Loan, LoanType, Member, MemberOption } from "@/lib/types";
import { formatNaira, statusBadgeClass } from "@/lib/ui";

const STATUSES: Loan["status"][] = [
  "PENDING", "APPROVED", "REJECTED", "DISBURSED", "RUNNING", "PULSED", "COMPLETED", "DEFAULTED", "BAD_DEBT",
];

function AdminLoanListContent() {
  const searchParams = useSearchParams();
  // Arrives here either from a single status pill (one element) or from the Overview page's Loan
  // Lifecycle chart, whose "Running" key is an aggregate of APPROVED/DISBURSED/RUNNING - see
  // AdminAnalyticsService.loanLifecycleBreakdown() - so ?status can be a comma-separated list, with an
  // optional ?label for what to call that combined view (defaults to the raw status list otherwise).
  const [statuses, setStatuses] = useState<Loan["status"][]>(() => {
    const q = searchParams.get("status");
    return q ? (q.split(",") as Loan["status"][]) : ["PULSED"];
  });
  const [statusLabel] = useState<string | null>(() => searchParams.get("label"));
  const [memberId, setMemberId] = useState<number | "">("");
  const [statusLoans, setStatusLoans] = useState<Loan[]>([]);
  const [memberLoans, setMemberLoans] = useState<Loan[]>([]);
  const [members, setMembers] = useState<Record<number, Member>>({});
  const [loanTypesById, setLoanTypesById] = useState<Record<number, LoanType>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [liquidatingLoan, setLiquidatingLoan] = useState<Loan | null>(null);

  useEffect(() => {
    api.get<Member[]>("/api/admin/members").then((rows) => {
      setMembers(Object.fromEntries(rows.map((m) => [m.id, m])));
    });
    api.get<LoanType[]>("/api/loan-types").then((rows) => {
      setLoanTypesById(Object.fromEntries(rows.map((t) => [t.id, t])));
    });
  }, []);

  const memberOptions: MemberOption[] = useMemo(
    () => Object.values(members).map((m) => ({ id: m.id, regno: m.regno, fullName: m.fullName })),
    [members],
  );

  // Picking a member switches the table to that member's complete loan history, every status at once
  // (via /api/admin/loans/member/{id}) - the status pills below stay visible but are ignored while a
  // member is selected, so an admin can flip back to status-browsing just by clearing the member.
  function load() {
    setError(null);
    setLoading(true);
    if (memberId !== "") {
      api.get<Loan[]>(`/api/admin/loans/member/${memberId}`)
        .then(setMemberLoans)
        .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load this member's loans."))
        .finally(() => setLoading(false));
    } else {
      api.get<Loan[]>(`/api/admin/loans/by-status?status=${statuses.join(",")}`)
        .then(setStatusLoans)
        .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load loans."))
        .finally(() => setLoading(false));
    }
  }

  useEffect(load, [statuses, memberId]);

  const loans = memberId !== "" ? memberLoans : statusLoans;
  const isSingleStatus = statuses.length === 1;
  const exportQuery = `status=${statuses[0]}`;
  const heading = statusLabel ?? statuses.map((s) => s.replace("_", " ")).join(", ");

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">
          {memberId !== "" ? `Loan history - ${members[memberId as number]?.fullName ?? ""}` : "Loans by status"}
        </h1>
        {memberId === "" && isSingleStatus && (
          <div className="flex gap-2">
            <a href={apiUrl(`/api/admin/loans/export-by-status.xlsx?${exportQuery}`)} className="btn btn-secondary">Download (Excel)</a>
            <a href={apiUrl(`/api/admin/loans/export-by-status.pdf?${exportQuery}`)} className="btn btn-secondary">Download (PDF)</a>
          </div>
        )}
      </div>

      <div className="card p-4 max-w-md">
        <label className="field-label">Look up one member's full loan history (any status)</label>
        <MemberSearchSelect options={memberOptions} value={memberId} onChange={setMemberId} placeholder="Search by name or regno..." />
      </div>

      {memberId === "" && (
        <div className="flex flex-wrap gap-2">
          {STATUSES.map((s) => (
            <button
              key={s}
              onClick={() => setStatuses([s])}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors ${
                isSingleStatus && statuses[0] === s
                  ? "bg-[var(--maroon-dark)] text-white border-[var(--maroon-dark)]"
                  : "bg-white text-[var(--muted)] border-[var(--line)] hover:border-[var(--maroon)]"
              }`}
            >
              {s.replace("_", " ")}
            </button>
          ))}
        </div>
      )}
      {memberId === "" && !isSingleStatus && (
        <p className="text-sm text-[var(--muted)]">Showing: {heading}</p>
      )}

      {error && <p className="alert-error max-w-md">{error}</p>}

      {liquidatingLoan && (
        <LiquidatePanel
          loan={liquidatingLoan}
          member={members[liquidatingLoan.memberId]}
          onCancel={() => setLiquidatingLoan(null)}
          onDone={() => { setLiquidatingLoan(null); load(); }}
        />
      )}

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
              <th className="p-3"></th>
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
                  <td className="p-3"><span className={statusBadgeClass(l.status)}>{l.status.replace("_", " ")}</span></td>
                  <td className="p-3">{l.appliedAt?.slice(0, 10) ?? "-"}</td>
                  <td className="p-3">
                    {l.status === "RUNNING" && (l.balance ?? 0) > 0 && (
                      <button onClick={() => setLiquidatingLoan(l)} className="text-[var(--maroon)] hover:underline text-xs font-medium">
                        Liquidate
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
            {!loading && loans.length === 0 && (
              <tr><td colSpan={9} className="p-4 text-center text-[var(--muted)]">
                {memberId !== "" ? "This member has no loans on record." : `No ${heading.toLowerCase()} loans.`}
              </td></tr>
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
      <Suspense>
        <AdminLoanListContent />
      </Suspense>
    </RequireAuth>
  );
}
