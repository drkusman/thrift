"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { Sidebar } from "./Sidebar";

export function RequireAuth({ staffOnly = false, children }: { staffOnly?: boolean; children: React.ReactNode }) {
  const { member, loading } = useAuth();
  const router = useRouter();
  const isStaff = member?.role === "ADMIN" || member?.role === "FIN_SEC";

  useEffect(() => {
    if (loading) return;
    if (!member) { router.push("/login"); return; }
    if (member.mustChangePassword) { router.push("/change-password"); return; }
    if (staffOnly && !isStaff) { router.push("/dashboard"); return; }
  }, [loading, member, isStaff, staffOnly, router]);

  if (loading || !member || member.mustChangePassword || (staffOnly && !isStaff)) {
    return (
      <div className="flex-1 flex items-center justify-center text-sm text-[var(--muted)] bg-[var(--bg)]">
        Loading...
      </div>
    );
  }

  return (
    <div className="flex-1 flex bg-[var(--bg)] min-h-0">
      <Sidebar />
      <main className="flex-1 min-h-0 overflow-y-auto px-6 py-8 md:px-10">
        <div className="max-w-5xl mx-auto">{children}</div>
      </main>
    </div>
  );
}
