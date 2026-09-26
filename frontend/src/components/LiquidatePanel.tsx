"use client";

import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { Loan, LiquidationPreview, Member } from "@/lib/types";
import { formatNaira } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

export function LiquidatePanel({ loan, member, onDone, onCancel }: {
  loan: Loan; member: Member | undefined; onDone: () => void; onCancel: () => void;
}) {
  const guard = useSubmitGuard();
  const [amount, setAmount] = useState(loan.balance ? String(loan.balance) : "");
  const [preview, setPreview] = useState<LiquidationPreview | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    const amt = Number(amount);
    setPreview(null);
    setPreviewError(null);
    if (!amount || !Number.isFinite(amt) || amt <= 0) return;
    setLoadingPreview(true);
    const timer = setTimeout(() => {
      api.get<LiquidationPreview>(`/api/admin/loans/${loan.id}/liquidation-preview?amount=${amt}`)
        .then(setPreview)
        .catch((e) => setPreviewError(e instanceof ApiError ? e.message : "Could not compute a preview."))
        .finally(() => setLoadingPreview(false));
    }, 400);
    return () => clearTimeout(timer);
  }, [amount, loan.id]);

  async function onConfirm() {
    await guard(async () => {
      setConfirmError(null);
      setConfirming(true);
      try {
        await api.post(`/api/admin/loans/${loan.id}/liquidate`, { amount: Number(amount) });
        onDone();
      } catch (e) {
        setConfirmError(e instanceof ApiError ? e.message : "Could not liquidate this loan.");
      } finally {
        setConfirming(false);
      }
    });
  }

  return (
    <div className="card p-6 space-y-4 border-2 border-[var(--maroon)]">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-[var(--ink)]">
          Liquidate {loan.loanCode ?? `Loan #${loan.id}`} - {member?.fullName ?? `Member #${loan.memberId}`}
        </h2>
        <button onClick={onCancel} className="text-sm text-[var(--muted)] hover:underline">Cancel</button>
      </div>
      <p className="text-sm text-[var(--muted)]">
        Outstanding balance: {formatNaira(loan.balance ?? 0)}. Enter the full balance for a full liquidation
        (closes the loan), or a smaller amount to partially liquidate - the remaining repayment plan is
        recalculated automatically. A flat {formatNaira(1000)} admin fee is also deducted from savings, and
        savings can never be drawn down below a {formatNaira(20000)} minimum.
      </p>
      <div>
        <label className="field-label">Liquidation amount</label>
        <input type="number" min={1} value={amount} onChange={(e) => setAmount(e.target.value)} className="field-input" />
      </div>

      {loadingPreview && <p className="text-sm text-[var(--muted)]">Calculating...</p>}
      {previewError && <p className="alert-error">{previewError}</p>}

      {preview && (
        <div className="p-3 rounded-lg bg-[var(--maroon-light)]/40 text-sm space-y-1">
          <p>Deducted from savings: <strong>{formatNaira(preview.amount + preview.adminFee)}</strong> ({formatNaira(preview.amount)} liquidation + {formatNaira(preview.adminFee)} fee)</p>
          <p>Member&rsquo;s savings balance: {formatNaira(preview.savingsBalance)}{!preview.sufficientSavings && (
            <span className="text-[var(--danger,#b91c1c)] font-semibold">
              {" "}- would leave savings below the {formatNaira(preview.minimumRetainedSavings)} minimum
              (at most {formatNaira(preview.maxLiquidatable)} can be liquidated here)
            </span>
          )}</p>
          {preview.fullLiquidation ? (
            <p className="font-semibold text-[var(--maroon-dark)]">This fully settles the loan - it will be marked COMPLETED.</p>
          ) : (
            <>
              <p>New loan balance: {formatNaira(preview.newBalance)}</p>
              {preview.proposedMonthlyRepayment != null && preview.remainingInstallments != null ? (
                <p>Repayment plan revised: {formatNaira(preview.proposedMonthlyRepayment)}/month across the remaining {preview.remainingInstallments} installment(s).</p>
              ) : (
                <p className="text-[var(--muted)]">This loan has no remaining schedule to revise - only the balance changes.</p>
              )}
            </>
          )}
        </div>
      )}

      {confirmError && <p className="alert-error">{confirmError}</p>}

      <div className="flex gap-2">
        <button
          onClick={onConfirm}
          disabled={!preview || confirming || (preview ? !preview.sufficientSavings : true)}
          className="btn btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {confirming ? "Liquidating..." : "Confirm liquidation"}
        </button>
        <button onClick={onCancel} className="btn btn-secondary">Cancel</button>
      </div>
    </div>
  );
}
