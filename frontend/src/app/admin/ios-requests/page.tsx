"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { MemberSearchSelect } from "@/components/MemberSearchSelect";
import { api, ApiError } from "@/lib/api";
import { IosPayoutRequest, Member, MemberOption } from "@/lib/types";
import { formatNaira, statusBadgeClass } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

function ApplyOnBehalfForm({ activeMembers, onApplied }: { activeMembers: MemberOption[]; onApplied: () => void }) {
  const guard = useSubmitGuard();
  const [memberId, setMemberId] = useState<number | "">("");
  const [available, setAvailable] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setAvailable(null);
    if (memberId === "") return;
    api.get<number>(`/api/admin/ios-requests/available?memberId=${memberId}`).then(setAvailable);
  }, [memberId]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null); setSuccess(null);
      if (!memberId) { setError("Choose a member."); return; }
      setSubmitting(true);
      try {
        const req = await api.post<IosPayoutRequest>("/api/admin/ios-requests", { memberId });
        setSuccess(`Request submitted for ${formatNaira(req.requestedAmount)}.`);
        setMemberId("");
        onApplied();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not submit request.");
      } finally {
        setSubmitting(false);
      }
    });
  }

  return (
    <form onSubmit={onSubmit} className="card p-6 space-y-4 max-w-lg">
      <h2 className="font-semibold text-[var(--ink)]">Apply on behalf of a member</h2>
      <div>
        <label className="field-label">Member</label>
        <MemberSearchSelect options={activeMembers} value={memberId} onChange={setMemberId} placeholder="Search active members by name or regno..." />
      </div>
      {memberId !== "" && (
        <p className="text-sm text-[var(--muted)]">
          Unpaid IOS available: <span className="font-semibold text-[var(--ink)]">{available === null ? "..." : formatNaira(available)}</span>
        </p>
      )}
      {error && <p className="alert-error">{error}</p>}
      {success && <p className="alert-success">{success}</p>}
      <button type="submit" disabled={submitting || !available} className="btn btn-primary disabled:opacity-50 disabled:cursor-not-allowed">
        {submitting ? "Submitting..." : "Apply"}
      </button>
    </form>
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
        Once you&rsquo;ve paid a member their unpaid interest on savings externally, mark the request here as
        paid, then post the matching debit through Monthly Upload with KIND set to IOS2 (IOS? = Yes).
      </p>
      {error && <p className="alert-error max-w-md">{error}</p>}

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
