"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { MemberSearchSelect } from "@/components/MemberSearchSelect";
import { api, apiUrl, ApiError } from "@/lib/api";
import { IosPayoutRequest, Member, MemberOption, UnpaidIosCredit } from "@/lib/types";
import { formatNaira, statusBadgeClass } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

function ApplyOnBehalfForm({ activeMembers, onApplied }: { activeMembers: MemberOption[]; onApplied: () => void }) {
  const guard = useSubmitGuard();
  const [memberId, setMemberId] = useState<number | "">("");
  const [credits, setCredits] = useState<UnpaidIosCredit[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [applyingId, setApplyingId] = useState<number | null>(null);

  useEffect(() => {
    setCredits(null);
    if (memberId === "") return;
    api.get<UnpaidIosCredit[]>(`/api/admin/ios-requests/available?memberId=${memberId}`).then(setCredits);
  }, [memberId]);

  async function onApply(credit: UnpaidIosCredit) {
    await guard(async () => {
      setError(null); setSuccess(null);
      if (!memberId) { setError("Choose a member."); return; }
      setApplyingId(credit.ledgerEntryId);
      try {
        const req = await api.post<IosPayoutRequest>("/api/admin/ios-requests", { memberId, ledgerEntryId: credit.ledgerEntryId });
        setSuccess(`Request submitted for ${formatNaira(req.requestedAmount)}.`);
        setCredits((prev) => prev?.filter((c) => c.ledgerEntryId !== credit.ledgerEntryId) ?? null);
        onApplied();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not submit request.");
      } finally {
        setApplyingId(null);
      }
    });
  }

  return (
    <div className="card p-6 space-y-4 max-w-lg">
      <h2 className="font-semibold text-[var(--ink)]">Apply on behalf of a member</h2>
      <div>
        <label className="field-label">Member</label>
        <MemberSearchSelect options={activeMembers} value={memberId} onChange={setMemberId} placeholder="Search active members by name or regno..." />
      </div>
      {error && <p className="alert-error">{error}</p>}
      {success && <p className="alert-success">{success}</p>}

      {memberId !== "" && credits !== null && (
        credits.length === 0 ? (
          <p className="text-sm text-[var(--muted)]">No unpaid IOS for this member.</p>
        ) : (
          <ul className="space-y-2">
            {credits.map((c) => (
              <li key={c.ledgerEntryId} className="flex items-center justify-between gap-3 p-3 rounded-lg bg-[var(--maroon-light)]/40">
                <div>
                  <p className="font-medium text-[var(--ink)]">{formatNaira(c.amount)}</p>
                  <p className="text-xs text-[var(--muted)]">{c.description} &middot; {c.date}</p>
                </div>
                <button
                  onClick={() => onApply(c)}
                  disabled={applyingId === c.ledgerEntryId}
                  className="btn btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {applyingId === c.ledgerEntryId ? "Applying..." : "Apply"}
                </button>
              </li>
            ))}
          </ul>
        )
      )}
    </div>
  );
}

function AdminIosContent() {
  const [requests, setRequests] = useState<IosPayoutRequest[]>([]);
  const [members, setMembers] = useState<Record<number, Member>>({});
  const [activeMembers, setActiveMembers] = useState<MemberOption[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  function load() {
    api.get<IosPayoutRequest[]>("/api/admin/ios-requests/pending").then(setRequests);
  }

  useEffect(() => {
    load();
    api.get<Member[]>("/api/admin/members").then((rows) => {
      setMembers(Object.fromEntries(rows.map((m) => [m.id, m])));
    });
    api.get<MemberOption[]>("/api/members/active").then(setActiveMembers);
  }, []);

  async function markPaid(id: number) {
    setError(null);
    setBusyId(id);
    try { await api.post(`/api/admin/ios-requests/${id}/mark-paid`); load(); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Could not mark this request as paid."); }
    finally { setBusyId(null); }
  }

  async function reject(id: number) {
    setError(null);
    setBusyId(id);
    try { await api.post(`/api/admin/ios-requests/${id}/reject`); load(); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Could not reject this request."); }
    finally { setBusyId(null); }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">IOS payout requests</h1>
      <p className="text-sm text-[var(--muted)] max-w-2xl">
        Once you&rsquo;ve paid members their unpaid interest on savings externally, download the pending
        list below and re-upload it through Monthly Upload with KIND set to IOS2 (IOS? = Yes) - each row
        automatically clears itself from this queue once posted. You can still mark a request as paid or
        reject it individually below instead, if you prefer.
      </p>
      {error && <p className="alert-error max-w-md">{error}</p>}

      {requests.length > 0 && (
        <a
          href={apiUrl("/api/admin/ios-requests/pending/export.xlsx")}
          className="btn btn-gold inline-block"
        >
          Download pending list ({requests.length})
        </a>
      )}

      <ApplyOnBehalfForm activeMembers={activeMembers} onApplied={load} />

      <div className="card divide-y divide-[var(--line)]">
        {requests.map((r) => {
          const member = members[r.memberId];
          return (
            <div key={r.id} className="p-4 flex items-center justify-between gap-3 flex-wrap">
              <div>
                <p className="font-semibold text-[var(--ink)]">
                  {member ? `${member.fullName} (${member.regno})` : `Member #${r.memberId}`}
                </p>
                <p className="text-sm text-[var(--muted)]">
                  Requesting {formatNaira(r.requestedAmount)} &middot; requested {r.requestedAt.slice(0, 10)}{" "}
                  <span className={statusBadgeClass(r.status)}>{r.status}</span>
                </p>
              </div>
              <div className="flex gap-2">
                <button onClick={() => markPaid(r.id)} disabled={busyId === r.id} className="btn btn-gold">Mark as paid</button>
                <button onClick={() => reject(r.id)} disabled={busyId === r.id} className="btn btn-danger">Reject</button>
              </div>
            </div>
          );
        })}
        {requests.length === 0 && <p className="p-4 text-sm text-[var(--muted)]">No pending requests.</p>}
      </div>
    </div>
  );
}

export default function AdminIosRequestsPage() {
  return (
    <RequireAuth staffOnly>
      <AdminIosContent />
    </RequireAuth>
  );
}
