"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, ApiError } from "@/lib/api";
import { Member, SavingsRequest } from "@/lib/types";
import { formatNaira } from "@/lib/ui";

function AdminSavingsContent() {
  const [requests, setRequests] = useState<SavingsRequest[]>([]);
  const [members, setMembers] = useState<Record<number, Member>>({});
  const [error, setError] = useState<string | null>(null);

  function load() {
    api.get<SavingsRequest[]>("/api/admin/savings-requests/pending").then(setRequests);
  }

  useEffect(() => {
    load();
    api.get<Member[]>("/api/admin/members").then((rows) => {
      setMembers(Object.fromEntries(rows.map((m) => [m.id, m])));
    });
  }, []);

  async function approve(id: number) {
    setError(null);
    try { await api.post(`/api/admin/savings-requests/${id}/approve`); load(); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Could not approve request."); }
  }

  async function reject(id: number) {
    setError(null);
    try { await api.post(`/api/admin/savings-requests/${id}/reject`); load(); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Could not reject request."); }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Pending savings amount changes</h1>
      {error && <p className="alert-error max-w-md">{error}</p>}

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
                  Requesting {formatNaira(r.requestedAmount)} &middot; current {member ? formatNaira(member.monthlySavingsAmount) : "-"}
                </p>
              </div>
              <div className="flex gap-2">
                <button onClick={() => approve(r.id)} className="btn btn-gold">Approve</button>
                <button onClick={() => reject(r.id)} className="btn btn-danger">Reject</button>
              </div>
            </div>
          );
        })}
        {requests.length === 0 && <p className="p-4 text-sm text-[var(--muted)]">No pending requests.</p>}
      </div>
    </div>
  );
}

export default function AdminSavingsRequestsPage() {
  return (
    <RequireAuth staffOnly>
      <AdminSavingsContent />
    </RequireAuth>
  );
}
