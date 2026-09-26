"use client";

import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { Loan, LiquidationPreview } from "@/lib/types";
import { formatNaira } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

export function LiquidationRequestForm({ loan, onDone, onCancel }: {
  loan: Loan; onDone: () => void; onCancel: () => void;
}) {
  const guard = useSubmitGuard();
  const [amount, setAmount] = useState(loan.balance ? String(loan.balance) : "");
  const [preview, setPreview] = useState<LiquidationPreview | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const amt = Number(amount);
    setPreview(null);
    setPreviewError(null);
    if (!amount || !Number.isFinite(amt) || amt <= 0) return;
    setLoadingPreview(true);
    const timer = setTimeout(() => {
      api.get<LiquidationPreview>(`/api/me/loans/${loan.id}/liquidation-preview?amount=${amt}`)
        .then(setPreview)
        .catch((e) => setPreviewError(e instanceof ApiError ? e.message : "Could not compute a preview."))
        .finally(() => setLoadingPreview(false));
    }, 400);
    return () => clearTimeout(timer);
  }, [amount, loan.id]);

  async function onSubmit() {
    await guard(async () => {
      setSubmitError(null);
      setSubmitting(true);
      try {
        await api.post(`/api/me/loans/${loan.id}/liquidation-requests`, { amount: Number(amount) });
        onDone();
      } catch (e) {
        setSubmitError(e instanceof ApiError ? e.message : "Could not submit this request.");
      } finally {
        setSubmitting(false);
      }
    });
  }

  return (
    <div className="rounded-md bg-[var(--maroon-light)]/40 p-3 space-y-3">
      <p className="text-sm font-medium text-[var(--ink)]">Request liquidation of {loan.loanCode ?? `Loan #${loan.id}`}</p>
      <p className="text-xs text-[var(--muted)]">
        Enter the full balance ({formatNaira(loan.balance ?? 0)}) to request full settlement, or a smaller
        amount to partially liquidate. A flat {formatNaira(1000)} admin fee is deducted from your savings on
        top of the amount, and your savings can never go below {formatNaira(20000)}. An admin must approve
        this before it takes effect.
      </p>
      <div>
        <input type="number" min={1} value={amount} onChange={(e) => setAmount(e.target.value)} className="field-input" />
      </div>

      {loadingPreview && <p className="text-xs text-[var(--muted)]">Calculating...</p>}
      {previewError && <p className="alert-error text-xs">{previewError}</p>}

      {preview && (
        <div className="text-xs space-y-1">
          <p>Would deduct {formatNaira(preview.amount + preview.adminFee)} from your savings ({formatNaira(preview.amount)} + {formatNaira(preview.adminFee)} fee).</p>
          <p>Your savings balance: {formatNaira(preview.savingsBalance)}{!preview.sufficientSavings && (
            <span className="text-[var(--danger,#b91c1c)] font-semibold">
              {" "}- would leave savings below the {formatNaira(preview.minimumRetainedSavings)} minimum
              (at most {formatNaira(preview.maxLiquidatable)} can be requested)
            </span>
          )}</p>
          {preview.fullLiquidation ? (
            <p className="font-semibold text-[var(--maroon-dark)]">This would fully settle the loan.</p>
          ) : (
            <p>New loan balance would be {formatNaira(preview.newBalance)}.</p>
          )}
        </div>
      )}

      {submitError && <p className="alert-error text-xs">{submitError}</p>}

      <div className="flex gap-2">
        <button
          onClick={onSubmit}
          disabled={!preview || submitting || !preview.sufficientSavings}
          className="btn btn-primary !py-1.5 !px-3 text-xs disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitting ? "Submitting..." : "Submit request"}
        </button>
        <button onClick={onCancel} className="text-xs text-[var(--muted)] hover:underline">Cancel</button>
      </div>
    </div>
  );
}
