"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, ApiError } from "@/lib/api";
import { GuaranteeRequest } from "@/lib/types";
import { formatNaira, statusBadgeClass } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

function GuaranteeCard({ req, onChanged }: { req: GuaranteeRequest; onChanged: () => void }) {
  const guard = useSubmitGuard();
  const [error, setError] = useState<string | null>(null);

  async function respond(accept: boolean) {
    await guard(async () => {
      setError(null);
      try {
        await api.post(`/api/me/guarantee-requests/${req.loanId}/${accept ? "accept" : "reject"}`, {});
        onChanged();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not record your response.");
      }
    });
  }

  return (
    <div className="p-4 space-y-2">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div>
          <p className="font-semibold text-[var(--ink)]">
            {req.applicantName} ({req.applicantRegno})
          </p>
          <p className="text-sm text-[var(--muted)]">
            {formatNaira(req.requestedAmount)} &middot; applied {req.appliedAt?.slice(0, 10)}
          </p>
          {req.reason && <p className="text-sm text-[var(--muted)] italic">&ldquo;{req.reason}&rdquo;</p>}
          <p className="text-xs text-[var(--muted)] mt-1 flex items-center gap-1.5">
            Your response: <span className={statusBadgeClass(req.myStatus)}>{req.myStatus}</span>
            <span className="ml-2">Other guarantor: <span className={statusBadgeClass(req.otherGuarantorStatus)}>{req.otherGuarantorStatus}</span></span>
          </p>
        </div>
        {req.myStatus === "PENDING" && (
          <div className="flex gap-2">
            <button onClick={() => respond(true)} className="btn btn-gold">Accept</button>
            <button onClick={() => respond(false)} className="btn btn-danger">Reject</button>
          </div>
        )}
        {req.myStatus !== "PENDING" && (
          <button onClick={() => respond(req.myStatus !== "ACCEPTED")} className="text-xs text-[var(--maroon)] hover:underline">
            Change to {req.myStatus === "ACCEPTED" ? "Reject" : "Accept"}
          </button>
        )}
      </div>
      {error && <p className="alert-error text-xs">{error}</p>}
    </div>
  );
}

function GuaranteesContent() {
  const [requests, setRequests] = useState<GuaranteeRequest[]>([]);

  function load() {
    api.get<GuaranteeRequest[]>("/api/me/guarantee-requests").then(setRequests);
  }

  useEffect(() => { load(); }, []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Guarantee requests</h1>
        <p className="text-sm text-[var(--muted)] mt-1">
          Loan applications where another member has named you as a guarantor.
        </p>
      </div>
      <div className="card divide-y divide-[var(--line)]">
        {requests.map((r) => (
          <GuaranteeCard key={r.loanId} req={r} onChanged={load} />
        ))}
        {requests.length === 0 && <p className="p-4 text-sm text-[var(--muted)]">No guarantee requests.</p>}
      </div>
    </div>
  );
}

export default function GuaranteesPage() {
  return (
    <RequireAuth>
      <GuaranteesContent />
    </RequireAuth>
  );
}
