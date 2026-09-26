"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { useAuth } from "@/lib/auth-context";
import { api, ApiError } from "@/lib/api";
import { Loan, LoanLiquidationRequest, LoanType, MemberOption, ScheduleRow } from "@/lib/types";
import { formatNaira, statusBadgeClass, loanTypeAmountRangeLabel } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";
import { MemberSearchSelect } from "@/components/MemberSearchSelect";
import { LiquidationRequestForm } from "@/components/LiquidationRequestForm";

function GuarantorRow({
  loan, slot, memberOptions, onChanged,
}: { loan: Loan; slot: 1 | 2; memberOptions: MemberOption[]; onChanged: () => void }) {
  const guard = useSubmitGuard();
  const id = slot === 1 ? loan.guarantorOneId : loan.guarantorTwoId;
  const status = slot === 1 ? loan.guarantorOneStatus : loan.guarantorTwoStatus;
  const otherId = slot === 1 ? loan.guarantorTwoId : loan.guarantorOneId;
  const guarantor = memberOptions.find((m) => m.id === id);
  const [changing, setChanging] = useState(false);
  const [newId, setNewId] = useState<number | "">("");
  const [error, setError] = useState<string | null>(null);

  const canChange = loan.status === "PENDING" && status !== "ACCEPTED";

  async function submitChange() {
    await guard(async () => {
      setError(null);
      if (!newId) { setError("Choose a replacement guarantor."); return; }
      try {
        await api.post(`/api/me/loans/${loan.id}/change-guarantor`, { slot, newGuarantorId: newId });
        setChanging(false); setNewId("");
        onChanged();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not change guarantor.");
      }
    });
  }

  return (
    <div className="text-sm">
      <div className="flex items-center gap-2 flex-wrap">
        <span className="text-[var(--muted)]">Guarantor {slot}:</span>
        <span>{guarantor ? `${guarantor.fullName} (${guarantor.regno})` : `Member #${id}`}</span>
        <span className={statusBadgeClass(status)}>{status}</span>
        {canChange && !changing && (
          <button onClick={() => setChanging(true)} className="text-xs text-[var(--maroon)] hover:underline">Change</button>
        )}
      </div>
      {changing && (
        <div className="mt-2 flex items-center gap-2 flex-wrap">
          <div className="w-72">
            <MemberSearchSelect
              options={memberOptions}
              value={newId}
              onChange={setNewId}
              excludeIds={[loan.memberId, otherId ?? -1]}
              placeholder="Search replacement guarantor..."
            />
          </div>
          <button onClick={submitChange} className="btn btn-secondary !py-1.5 !px-3 text-xs">Save</button>
          <button onClick={() => { setChanging(false); setError(null); }} className="text-xs text-[var(--muted)] hover:underline">Cancel</button>
        </div>
      )}
      {error && <p className="alert-error text-xs mt-1">{error}</p>}
    </div>
  );
}

function LoansContent() {
  const { member } = useAuth();
  const guard = useSubmitGuard();
  const [loanTypes, setLoanTypes] = useState<LoanType[]>([]);
  const [activeMembers, setActiveMembers] = useState<MemberOption[]>([]);
  const [loans, setLoans] = useState<Loan[]>([]);
  const [loanTypeId, setLoanTypeId] = useState<number | "">("");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [guarantorOneId, setGuarantorOneId] = useState<number | "">("");
  const [guarantorTwoId, setGuarantorTwoId] = useState<number | "">("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [schedule, setSchedule] = useState<ScheduleRow[]>([]);
  const [liquidationRequests, setLiquidationRequests] = useState<LoanLiquidationRequest[]>([]);
  const [requestingLoanId, setRequestingLoanId] = useState<number | null>(null);

  function load() {
    api.get<Loan[]>("/api/me/loans").then(setLoans);
    api.get<LoanLiquidationRequest[]>("/api/me/loan-liquidation-requests").then(setLiquidationRequests);
  }

  useEffect(() => {
    api.get<LoanType[]>("/api/loan-types").then(setLoanTypes);
    api.get<MemberOption[]>("/api/members/active").then(setActiveMembers);
    load();
  }, []);

  function pendingRequestFor(loanId: number) {
    return liquidationRequests.find((r) => r.loanId === loanId && r.status === "PENDING");
  }

  const selectedType = loanTypes.find((t) => t.id === loanTypeId);

  function activeCountFor(typeId: number) {
    return loans.filter((l) => l.loanTypeId === typeId && (l.status === "RUNNING" || l.status === "PULSED")).length;
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null);
      if (!loanTypeId) { setError("Choose a loan type."); return; }
      if (!guarantorOneId || !guarantorTwoId) { setError("Both guarantors are required."); return; }
      if (guarantorOneId === guarantorTwoId) { setError("The two guarantors must be different members."); return; }
      if (selectedType) {
        const amt = Number(amount);
        if (selectedType.minAmount !== null && amt < selectedType.minAmount) {
          setError(`${selectedType.name} requires at least ${formatNaira(selectedType.minAmount)}.`); return;
        }
        if (selectedType.maxAmount !== null && amt > selectedType.maxAmount) {
          setError(`${selectedType.name} cannot exceed ${formatNaira(selectedType.maxAmount)}.`); return;
        }
        if (selectedType.maxConcurrentActive !== null && activeCountFor(selectedType.id) >= selectedType.maxConcurrentActive) {
          setError(`You already have ${activeCountFor(selectedType.id)} ${selectedType.name}(s) running - complete at least one before applying for another.`);
          return;
        }
      }
      setSubmitting(true);
      try {
        await api.post("/api/me/loans", {
          loanTypeId,
          requestedAmount: Number(amount),
          reason,
          guarantorOneId,
          guarantorTwoId,
        });
        setAmount(""); setReason(""); setLoanTypeId(""); setGuarantorOneId(""); setGuarantorTwoId("");
        load();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not submit application.");
      } finally {
        setSubmitting(false);
      }
    });
  }

  async function toggleSchedule(loanId: number) {
    if (expanded === loanId) { setExpanded(null); return; }
    const rows = await api.get<ScheduleRow[]>(`/api/me/loans/${loanId}/schedule`);
    setSchedule(rows);
    setExpanded(loanId);
  }

  const selfAndOtherGuarantor = (otherId: number | "") => {
    const ids = [member?.id];
    if (otherId !== "") ids.push(otherId);
    return ids.filter((id): id is number => id !== undefined);
  };

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Loans</h1>

      <form onSubmit={onSubmit} className="card p-6 space-y-4 max-w-lg">
        <h2 className="font-semibold text-[var(--ink)]">Apply for a loan</h2>
        <div>
          <label className="field-label">Loan type</label>
          <select
            className="field-input"
            value={loanTypeId}
            onChange={(e) => setLoanTypeId(e.target.value ? Number(e.target.value) : "")}
            required
          >
            <option value="">Select...</option>
            {loanTypes.map((t) => {
              const range = loanTypeAmountRangeLabel(t);
              return (
                <option key={t.id} value={t.id}>
                  {t.name} ({t.interestRate}% {t.interestMethod === "AT_SOURCE" ? "at source" : "built in"}, max {t.maxDurationMonths} months{range ? `, ${range}` : ""})
                </option>
              );
            })}
          </select>
          {selectedType && (
            <p className="text-xs text-[var(--muted)] mt-1.5">Repayment term is fixed at {selectedType.maxDurationMonths} months for this loan type.</p>
          )}
          {selectedType && selectedType.maxConcurrentActive !== null && activeCountFor(selectedType.id) >= selectedType.maxConcurrentActive && (
            <p className="text-xs text-[var(--maroon-dark)] font-medium mt-1">
              You already have {activeCountFor(selectedType.id)} {selectedType.name}(s) running - complete at least one before applying for another.
            </p>
          )}
        </div>
        <div>
          <label className="field-label">Amount requested</label>
          <input
            type="number"
            min={selectedType?.minAmount ?? 1}
            max={selectedType?.maxAmount ?? undefined}
            required
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            className="field-input"
          />
          <p className="text-xs text-[var(--muted)] mt-1.5">
            {selectedType ? (loanTypeAmountRangeLabel(selectedType) ?? "") : "Select a loan type to see its amount limits."}
          </p>
        </div>
        <div>
          <label className="field-label">First guarantor</label>
          <MemberSearchSelect
            options={activeMembers}
            value={guarantorOneId}
            onChange={setGuarantorOneId}
            excludeIds={selfAndOtherGuarantor(guarantorTwoId)}
            placeholder="Search active members by name or regno..."
          />
        </div>
        <div>
          <label className="field-label">Second guarantor</label>
          <MemberSearchSelect
            options={activeMembers}
            value={guarantorTwoId}
            onChange={setGuarantorTwoId}
            excludeIds={selfAndOtherGuarantor(guarantorOneId)}
            placeholder="Search active members by name or regno..."
          />
          <p className="text-xs text-[var(--muted)] mt-1.5">
            Both must be different active members of the thrift, and must accept before your application can be approved.
          </p>
        </div>
        <div>
          <label className="field-label">Reason</label>
          <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={2} className="field-input" />
        </div>
        {error && <p className="alert-error">{error}</p>}
        <button type="submit" disabled={submitting} className="btn btn-primary">
          {submitting ? "Submitting..." : "Submit application"}
        </button>
      </form>

      <div>
        <h2 className="font-semibold text-[var(--ink)] mb-3">Your loans</h2>
        <div className="card divide-y divide-[var(--line)]">
          {loans.map((l) => (
            <div key={l.id} className="p-4 space-y-2">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="font-semibold text-[var(--ink)] flex items-center gap-2">
                    {formatNaira(l.requestedAmount)}
                    <span className={statusBadgeClass(l.status)}>{l.status}</span>
                  </p>
                  <p className="text-xs text-[var(--muted)] mt-0.5">{l.loanCode ?? "Pending approval"} &middot; applied {l.appliedAt?.slice(0, 10)}</p>
                </div>
                <div className="text-right text-sm">
                  {l.balance !== null && <p className="font-semibold text-[var(--ink)]">Balance: {formatNaira(l.balance)}</p>}
                  <p className="text-[var(--muted)]">Monthly: {formatNaira(l.monthlyRepaymentAmount)}</p>
                  {(l.status === "DISBURSED" || l.status === "RUNNING" || l.status === "PULSED" || l.status === "COMPLETED") && (
                    <button onClick={() => toggleSchedule(l.id)} className="text-[var(--maroon)] hover:underline text-xs font-medium">
                      {expanded === l.id ? "Hide schedule" : "View schedule"}
                    </button>
                  )}
                  {l.status === "RUNNING" && (l.balance ?? 0) > 0 && !pendingRequestFor(l.id) && requestingLoanId !== l.id && (
                    <button onClick={() => setRequestingLoanId(l.id)} className="block mt-1 text-[var(--maroon)] hover:underline text-xs font-medium">
                      Request liquidation
                    </button>
                  )}
                </div>
              </div>

              {l.status === "PENDING" && (
                <div className="rounded-md bg-[var(--maroon-light)]/40 p-3 space-y-2">
                  {(l.guarantorOneStatus !== "ACCEPTED" || l.guarantorTwoStatus !== "ACCEPTED") && (
                    <p className="text-xs text-[var(--maroon-dark)] font-medium">Awaiting both guarantors to accept before this can be approved.</p>
                  )}
                  <GuarantorRow loan={l} slot={1} memberOptions={activeMembers} onChanged={load} />
                  <GuarantorRow loan={l} slot={2} memberOptions={activeMembers} onChanged={load} />
                </div>
              )}

              {pendingRequestFor(l.id) && (
                <p className="text-xs text-[var(--maroon-dark)] font-medium">
                  Liquidation of {formatNaira(pendingRequestFor(l.id)!.requestedAmount)} requested {pendingRequestFor(l.id)!.requestedAt.slice(0, 10)} - awaiting admin approval.
                </p>
              )}

              {requestingLoanId === l.id && (
                <LiquidationRequestForm
                  loan={l}
                  onCancel={() => setRequestingLoanId(null)}
                  onDone={() => { setRequestingLoanId(null); load(); }}
                />
              )}

              {expanded === l.id && (
                schedule.length === 0 ? (
                  <p className="text-xs text-[var(--muted)] mt-3">No installment schedule on record for this loan.</p>
                ) : (
                  <table className="table-elegant mt-3">
                    <thead>
                      <tr><th>#</th><th>Due</th><th className="text-right">Due amount</th><th className="text-right">Paid</th><th>Status</th></tr>
                    </thead>
                    <tbody>
                      {schedule.map((s) => (
                        <tr key={s.id}>
                          <td>{s.installmentNo}</td>
                          <td>{s.dueDate}</td>
                          <td className="text-right">{formatNaira(s.amountDue)}</td>
                          <td className="text-right">{formatNaira(s.amountPaid)}</td>
                          <td><span className={statusBadgeClass(s.status)}>{s.status}</span></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )
              )}
            </div>
          ))}
          {loans.length === 0 && <p className="p-4 text-sm text-[var(--muted)]">No loan applications yet.</p>}
        </div>
      </div>
    </div>
  );
}

export default function LoansPage() {
  return (
    <RequireAuth>
      <LoansContent />
    </RequireAuth>
  );
}
