"use client";

import { useEffect, useRef, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, apiUrl, ApiError } from "@/lib/api";
import { Loan, LoanType, Member, MemberOption } from "@/lib/types";
import { formatNaira, statusBadgeClass, loanTypeAmountRangeLabel } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";
import { MemberSearchSelect } from "@/components/MemberSearchSelect";

function guarantorLabel(member: Member | undefined) {
  return member ? `${member.fullName} (${member.regno})` : "-";
}

function ApplyOnBehalfForm({ loanTypes, activeMembers, onApplied }: {
  loanTypes: LoanType[]; activeMembers: MemberOption[]; onApplied: () => void;
}) {
  const guard = useSubmitGuard();
  const [open, setOpen] = useState(false);
  const [memberId, setMemberId] = useState<number | "">("");
  const [loanTypeId, setLoanTypeId] = useState<number | "">("");
  const [amount, setAmount] = useState("");
  const [guarantorOneId, setGuarantorOneId] = useState<number | "">("");
  const [guarantorTwoId, setGuarantorTwoId] = useState<number | "">("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const selectedType = loanTypes.find((t) => t.id === loanTypeId);

  function excludeIds(...ids: (number | "")[]) {
    return ids.filter((id): id is number => id !== "");
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null); setSuccess(null);
      if (!memberId) { setError("Choose the applicant."); return; }
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
      }
      setSubmitting(true);
      try {
        await api.post("/api/admin/loans", {
          memberId,
          loanTypeId,
          requestedAmount: Number(amount),
          reason: reason || "Applied offline with signed guarantor approval form",
          guarantorOneId,
          guarantorTwoId,
        });
        setSuccess("Application submitted - both guarantors recorded as accepted from the signed form.");
        setMemberId(""); setLoanTypeId(""); setAmount(""); setGuarantorOneId(""); setGuarantorTwoId(""); setReason("");
        onApplied();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not submit application.");
      } finally {
        setSubmitting(false);
      }
    });
  }

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} className="btn btn-secondary">
        + Apply on behalf of a member
      </button>
    );
  }

  return (
    <form onSubmit={onSubmit} className="card p-6 space-y-4 max-w-lg">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-[var(--ink)]">Apply on behalf of a member</h2>
        <button type="button" onClick={() => setOpen(false)} className="text-xs text-[var(--muted)] hover:underline">Close</button>
      </div>
      <p className="text-xs text-[var(--muted)]">
        For members who applied offline with a paper form already signed by both guarantors - both go
        straight to ACCEPTED rather than waiting for them to confirm digitally.
      </p>
      <div>
        <label className="field-label">Applicant</label>
        <MemberSearchSelect
          options={activeMembers}
          value={memberId}
          onChange={setMemberId}
          excludeIds={excludeIds(guarantorOneId, guarantorTwoId)}
          placeholder="Search active members by name or regno..."
        />
      </div>
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
      </div>
      <div>
        <label className="field-label">First guarantor</label>
        <MemberSearchSelect
          options={activeMembers}
          value={guarantorOneId}
          onChange={setGuarantorOneId}
          excludeIds={excludeIds(memberId, guarantorTwoId)}
          placeholder="Search active members by name or regno..."
        />
      </div>
      <div>
        <label className="field-label">Second guarantor</label>
        <MemberSearchSelect
          options={activeMembers}
          value={guarantorTwoId}
          onChange={setGuarantorTwoId}
          excludeIds={excludeIds(memberId, guarantorOneId)}
          placeholder="Search active members by name or regno..."
        />
      </div>
      <div>
        <label className="field-label">Reason</label>
        <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={2} className="field-input"
          placeholder="Applied offline with signed guarantor approval form" />
      </div>
      {error && <p className="alert-error">{error}</p>}
      {success && <p className="alert-success">{success}</p>}
      <button type="submit" disabled={submitting} className="btn btn-primary">
        {submitting ? "Submitting..." : "Submit application"}
      </button>
    </form>
  );
}

function BatchApplyForm({ onApplied }: { onApplied: () => void }) {
  const guard = useSubmitGuard();
  const [open, setOpen] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null); setResult(null);
      const file = fileRef.current?.files?.[0];
      if (!file) { setError("Choose the filled-in template file."); return; }
      setSubmitting(true);
      try {
        const form = new FormData();
        form.append("file", file);
        const text = await api.postForm<string>("/api/admin/loans/apply-batch", form);
        setResult(String(text));
        if (fileRef.current) fileRef.current.value = "";
        onApplied();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Batch upload failed.");
      } finally {
        setSubmitting(false);
      }
    });
  }

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} className="btn btn-secondary">
        + Batch upload applications
      </button>
    );
  }

  return (
    <form onSubmit={onSubmit} className="card p-6 space-y-3 max-w-lg">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-[var(--ink)]">Batch upload applications</h2>
        <button type="button" onClick={() => setOpen(false)} className="text-xs text-[var(--muted)] hover:underline">Close</button>
      </div>
      <p className="text-xs text-[var(--muted)]">
        For a stack of paper forms at once - download the template, fill in one row per application
        (regno, loantype, amount, both guarantors&rsquo; regnos, and an optional reason), then upload it here.
        Every guarantor goes straight to ACCEPTED, same as the single on-behalf form.
      </p>
      <a href={apiUrl("/api/admin/loans/apply-template.xlsx")} className="text-[var(--maroon)] hover:underline text-sm font-medium">
        Download template (Excel)
      </a>
      <div className="flex items-center gap-3">
        <input ref={fileRef} type="file" accept=".xlsx,.xls" className="text-sm flex-1" />
        <button type="submit" disabled={submitting} className="btn btn-primary whitespace-nowrap">
          {submitting ? "Uploading..." : "Upload"}
        </button>
      </div>
      {error && <p className="alert-error">{error}</p>}
      {result && <p className="alert-success">{result}</p>}
    </form>
  );
}

function AdminLoansContent() {
  const [loans, setLoans] = useState<Loan[]>([]);
  const [members, setMembers] = useState<Record<number, Member>>({});
  const [loanTypes, setLoanTypes] = useState<LoanType[]>([]);
  const [loanTypesById, setLoanTypesById] = useState<Record<number, LoanType>>({});
  const [activeMembers, setActiveMembers] = useState<MemberOption[]>([]);
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
      setLoanTypes(rows);
      setLoanTypesById(Object.fromEntries(rows.map((t) => [t.id, t])));
    });
    api.get<MemberOption[]>("/api/members/active").then(setActiveMembers);
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
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Pending loan applications</h1>
        <div className="flex gap-2">
          <a href={apiUrl("/api/admin/loans/export.xlsx")} className="btn btn-secondary">Download pending applications (Excel)</a>
          <a href={apiUrl("/api/admin/loans/export.pdf")} className="btn btn-secondary">Download pending applications (PDF)</a>
        </div>
      </div>
      {error && <p className="alert-error max-w-md">{error}</p>}

      <div className="flex flex-wrap gap-3">
        <ApplyOnBehalfForm loanTypes={loanTypes} activeMembers={activeMembers} onApplied={load} />
        <BatchApplyForm onApplied={load} />
      </div>

      <div className="card divide-y divide-[var(--line)]">
        {loans.map((l) => {
          const member = members[l.memberId];
          const type = loanTypesById[l.loanTypeId];
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
