"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, ApiError } from "@/lib/api";
import { Loan, LoanType, ScheduleRow } from "@/lib/types";
import { formatNaira, statusBadgeClass } from "@/lib/ui";

function LoansContent() {
  const [loanTypes, setLoanTypes] = useState<LoanType[]>([]);
  const [loans, setLoans] = useState<Loan[]>([]);
  const [loanTypeId, setLoanTypeId] = useState<number | "">("");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [duration, setDuration] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [schedule, setSchedule] = useState<ScheduleRow[]>([]);

  function load() {
    api.get<Loan[]>("/api/me/loans").then(setLoans);
  }

  useEffect(() => {
    api.get<LoanType[]>("/api/loan-types").then(setLoanTypes);
    load();
  }, []);

  const selectedType = loanTypes.find((t) => t.id === loanTypeId);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!loanTypeId) { setError("Choose a loan type."); return; }
    setSubmitting(true);
    try {
      await api.post("/api/me/loans", {
        loanTypeId,
        requestedAmount: Number(amount),
        reason,
        durationMonths: duration ? Number(duration) : undefined,
      });
      setAmount(""); setReason(""); setDuration(""); setLoanTypeId("");
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not submit application.");
    } finally {
      setSubmitting(false);
    }
  }

  async function toggleSchedule(loanId: number) {
    if (expanded === loanId) { setExpanded(null); return; }
    const rows = await api.get<ScheduleRow[]>(`/api/me/loans/${loanId}/schedule`);
    setSchedule(rows);
    setExpanded(loanId);
  }

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
            {loanTypes.map((t) => (
              <option key={t.id} value={t.id}>{t.name} ({t.interestRate}% {t.interestMethod === "AT_SOURCE" ? "at source" : "built in"}, max {t.maxDurationMonths} months)</option>
            ))}
          </select>
        </div>
        <div>
          <label className="field-label">Amount requested</label>
          <input type="number" min={1} required value={amount} onChange={(e) => setAmount(e.target.value)} className="field-input" />
        </div>
        <div>
          <label className="field-label">
            Duration (months){selectedType ? ` - up to ${selectedType.maxDurationMonths}` : ""}
          </label>
          <input type="number" min={1} max={selectedType?.maxDurationMonths} value={duration} onChange={(e) => setDuration(e.target.value)}
            placeholder={selectedType ? String(selectedType.maxDurationMonths) : ""} className="field-input" />
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
            <div key={l.id} className="p-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="font-semibold text-[var(--ink)] flex items-center gap-2">
                    {formatNaira(l.requestedAmount)}
                    <span className={statusBadgeClass(l.status)}>{l.status}</span>
                  </p>
                  <p className="text-xs text-[var(--muted)] mt-0.5">{l.loanCode ?? "Pending approval"} &middot; applied {l.appliedAt?.slice(0, 10)}</p>
                </div>
                <div className="text-right text-sm">
                  <p className="text-[var(--muted)]">Monthly: {formatNaira(l.monthlyRepaymentAmount)}</p>
                  {(l.status === "DISBURSED" || l.status === "RUNNING" || l.status === "COMPLETED") && (
                    <button onClick={() => toggleSchedule(l.id)} className="text-[var(--maroon)] hover:underline text-xs font-medium">
                      {expanded === l.id ? "Hide schedule" : "View schedule"}
                    </button>
                  )}
                </div>
              </div>
              {expanded === l.id && (
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
