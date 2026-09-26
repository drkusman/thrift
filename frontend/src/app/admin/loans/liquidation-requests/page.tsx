"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, ApiError } from "@/lib/api";
import { LiquidationPreview, LoanLiquidationRequest, Member } from "@/lib/types";
import { formatNaira } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

function PendingRequestRow({ req, member, onDecided }: {
  req: LoanLiquidationRequest; member: Member | undefined; onDecided: () => void;
}) {
  const guard = useSubmitGuard();
  const [preview, setPreview] = useState<LiquidationPreview | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [decisionError, setDecisionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<LiquidationPreview>(`/api/admin/loans/${req.loanId}/liquidation-preview?amount=${req.requestedAmount}`)
      .then(setPreview)
      .catch((e) => setPreviewError(e instanceof ApiError ? e.message : "Could not compute a preview."));
  }, [req.loanId, req.requestedAmount]);

  async function approve() {
    await guard(async () => {
      setDecisionError(null);
      setBusy(true);
      try {
        await api.post(`/api/admin/loans/liquidation-requests/${req.id}/approve`);
        onDecided();
      } catch (e) {
        setDecisionError(e instanceof ApiError ? e.message : "Could not approve this request.");
      } finally {
        setBusy(false);
      }
    });
  }

  async function reject() {
    await guard(async () => {
      setDecisionError(null);
      setBusy(true);
      try {
        await api.post(`/api/admin/loans/liquidation-requests/${req.id}/reject`);
        onDecided();
      } catch (e) {
        setDecisionError(e instanceof ApiError ? e.message : "Could not reject this request.");
      } finally {
        setBusy(false);
      }
    });
  }

  return (
    <div className="p-4 space-y-2">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div>
          <p className="font-semibold text-[var(--ink)]">
            {member ? `${member.fullName} (${member.regno})` : `Member #${req.memberId}`}
          </p>
          <p className="text-sm text-[var(--muted)]">
            Requesting {formatNaira(req.requestedAmount)} on loan #{req.loanId} &middot; requested {req.requestedAt.slice(0, 10)}
          </p>
        </div>
        <div className="flex gap-2">
          <button onClick={approve} disabled={busy || (preview ? !preview.sufficientSavings : true)} className="btn btn-gold disabled:opacity-50 disabled:cursor-not-allowed">
            {busy ? "..." : "Approve"}
          </button>
          <button onClick={reject} disabled={busy} className="btn btn-danger">Reject</button>
        </div>
      </div>

      {previewError && <p className="alert-error text-sm">{previewError}</p>}
      {decisionError && <p className="alert-error text-sm">{decisionError}</p>}

      {preview && (
        <div className="p-3 rounded-lg bg-[var(--maroon-light)]/40 text-sm space-y-1">
          <p>Deducted from savings: <strong>{formatNaira(preview.amount + preview.adminFee)}</strong> ({formatNaira(preview.amount)} liquidation + {formatNaira(preview.adminFee)} fee)</p>
          <p>Member&rsquo;s savings balance: {formatNaira(preview.savingsBalance)}{!preview.sufficientSavings && (
            <span className="text-[var(--danger,#b91c1c)] font-semibold">
              {" "}- would leave savings below the {formatNaira(preview.minimumRetainedSavings)} minimum, can no longer be approved as requested
            </span>
          )}</p>
          {preview.fullLiquidation ? (
            <p className="font-semibold text-[var(--maroon-dark)]">This fully settles the loan - it will be marked COMPLETED.</p>
          ) : (
            <>
              <p>New loan balance: {formatNaira(preview.newBalance)}</p>
              {preview.proposedMonthlyRepayment != null && preview.remainingInstallments != null && (
                <p>Repayment plan will be revised to {formatNaira(preview.proposedMonthlyRepayment)}/month across the remaining {preview.remainingInstallments} installment(s).</p>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}

function AdminLiquidationRequestsContent() {
  const [requests, setRequests] = useState<LoanLiquidationRequest[]>([]);
  const [members, setMembers] = useState<Record<number, Member>>({});
  const [error, setError] = useState<string | null>(null);

  function load() {
    api.get<LoanLiquidationRequest[]>("/api/admin/loans/liquidation-requests/pending")
      .then(setRequests)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load pending requests."));
  }

  useEffect(() => {
    load();
    api.get<Member[]>("/api/admin/members").then((rows) => {
      setMembers(Object.fromEntries(rows.map((m) => [m.id, m])));
    });
  }, []);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Loan liquidation requests</h1>
      <p className="text-sm text-[var(--muted)] max-w-2xl">
        Members can request to liquidate their own running loan, in full or in part. Review the preview
        below before approving - approving posts the liquidation immediately, exactly as a direct liquidation
        from Loan Manager would.
      </p>
      {error && <p className="alert-error max-w-md">{error}</p>}

      <div className="card divide-y divide-[var(--line)]">
        {requests.map((r) => (
          <PendingRequestRow key={r.id} req={r} member={members[r.memberId]} onDecided={load} />
        ))}
        {requests.length === 0 && <p className="p-4 text-sm text-[var(--muted)]">No pending liquidation requests.</p>}
      </div>
    </div>
  );
}

export default function AdminLiquidationRequestsPage() {
  return (
    <RequireAuth staffOnly>
      <AdminLiquidationRequestsContent />
    </RequireAuth>
  );
}
