"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { RequireAuth } from "@/components/RequireAuth";
import { api, ApiError } from "@/lib/api";
import { Member, MembershipWithdrawalRequest, MembershipWithdrawalSummary } from "@/lib/types";
import { formatNaira } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

function PendingRequestRow({ req, member, onDecided }: {
  req: MembershipWithdrawalRequest; member: Member | undefined; onDecided: () => void;
}) {
  const guard = useSubmitGuard();
  const [summary, setSummary] = useState<MembershipWithdrawalSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<MembershipWithdrawalSummary>(`/api/admin/members/${req.memberId}/withdrawal-summary`).then(setSummary);
  }, [req.memberId]);

  async function reject() {
    await guard(async () => {
      setError(null);
      setBusy(true);
      try {
        await api.post(`/api/admin/members/withdrawal-requests/${req.id}/reject`);
        onDecided();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not reject this request.");
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
          <p className="text-sm text-[var(--muted)]">Requested {req.requestedAt.slice(0, 10)}</p>
        </div>
        <div className="flex gap-2">
          <Link href={`/admin/members/withdrawal?memberId=${req.memberId}`} className="btn btn-gold">Open Withdrawal Module</Link>
          <button onClick={reject} disabled={busy} className="btn btn-danger">Reject</button>
        </div>
      </div>

      {error && <p className="alert-error text-sm">{error}</p>}

      {summary && (
        <div className="p-3 rounded-lg bg-[var(--maroon-light)]/40 text-sm space-y-1">
          <p>Total savings: {formatNaira(summary.totalSavings)} &middot; Total loan: {formatNaira(summary.totalLoan)}</p>
          <p>Withdrawable (after COT of {formatNaira(summary.cot)}): <strong>{formatNaira(summary.withdrawableAmount)}</strong></p>
          {!summary.canWithdraw && (
            <p className="text-[var(--danger,#b91c1c)] font-semibold">Still has running loans - liquidate them first via the Withdrawal Module.</p>
          )}
        </div>
      )}
    </div>
  );
}

function AdminWithdrawalRequestsContent() {
  const [requests, setRequests] = useState<MembershipWithdrawalRequest[]>([]);
  const [members, setMembers] = useState<Record<number, Member>>({});
  const [error, setError] = useState<string | null>(null);

  function load() {
    api.get<MembershipWithdrawalRequest[]>("/api/admin/members/withdrawal-requests/pending")
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
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Membership withdrawal requests</h1>
      <p className="text-sm text-[var(--muted)] max-w-2xl">
        Members can request to withdraw from the cooperative entirely. Open the Withdrawal Module to
        liquidate any remaining loans and process the final payout - that also resolves this request.
      </p>
      {error && <p className="alert-error max-w-md">{error}</p>}

      <div className="card divide-y divide-[var(--line)]">
        {requests.map((r) => (
          <PendingRequestRow key={r.id} req={r} member={members[r.memberId]} onDecided={load} />
        ))}
        {requests.length === 0 && <p className="p-4 text-sm text-[var(--muted)]">No pending withdrawal requests.</p>}
      </div>
    </div>
  );
}

export default function AdminWithdrawalRequestsPage() {
  return (
    <RequireAuth staffOnly>
      <AdminWithdrawalRequestsContent />
    </RequireAuth>
  );
}
