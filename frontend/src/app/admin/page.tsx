"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { RequireAuth } from "@/components/RequireAuth";
import { api } from "@/lib/api";
import { Loan, Member, SavingsRequest } from "@/lib/types";

function AdminOverviewContent() {
  const [members, setMembers] = useState<Member[]>([]);
  const [pendingLoans, setPendingLoans] = useState<Loan[]>([]);
  const [pendingSavings, setPendingSavings] = useState<SavingsRequest[]>([]);

  useEffect(() => {
    api.get<Member[]>("/api/admin/members").then(setMembers);
    api.get<Loan[]>("/api/admin/loans/pending").then(setPendingLoans);
    api.get<SavingsRequest[]>("/api/admin/savings-requests/pending").then(setPendingSavings);
  }, []);

  return (
    <div className="space-y-8">
      <div className="rounded-2xl bg-gradient-to-br from-[var(--maroon)] to-[var(--maroon-dark)] text-white px-6 py-7 shadow-[var(--shadow-lg)]">
        <p className="text-xs font-bold tracking-[0.15em] uppercase text-[var(--gold)]">Admin</p>
        <h1 className="text-2xl font-semibold mt-1">Cooperative overview</h1>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Link href="/admin/members" className="stat-tile block hover:shadow-[var(--shadow-lg)] transition-shadow">
          <p className="stat-label">Members</p>
          <p className="stat-value">{members.length}</p>
        </Link>
        <Link href="/admin/loans" className="stat-tile block hover:shadow-[var(--shadow-lg)] transition-shadow">
          <p className="stat-label">Pending loan applications</p>
          <p className="stat-value">{pendingLoans.length}</p>
        </Link>
        <Link href="/admin/savings-requests" className="stat-tile block hover:shadow-[var(--shadow-lg)] transition-shadow">
          <p className="stat-label">Pending savings requests</p>
          <p className="stat-value">{pendingSavings.length}</p>
        </Link>
      </div>
    </div>
  );
}

export default function AdminOverviewPage() {
  return (
    <RequireAuth staffOnly>
      <AdminOverviewContent />
    </RequireAuth>
  );
}
