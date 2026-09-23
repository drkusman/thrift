"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { Header } from "./Header";

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
    <div className="flex-1 flex flex-col bg-[var(--bg)]">
      <Header />
      <main className="flex-1 max-w-6xl w-full mx-auto px-4 py-8">{children}</main>
    </div>
  );
}
