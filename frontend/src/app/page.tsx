"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";

export default function HomePage() {
  const { member, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (loading) return;
    if (!member) { router.push("/login"); return; }
    if (member.mustChangePassword) { router.push("/change-password"); return; }
    if (member.role === "ADMIN" || member.role === "FIN_SEC") router.push("/admin");
    else router.push("/dashboard");
  }, [loading, member, router]);

  return <div className="flex-1 flex items-center justify-center text-sm text-slate-400">Loading...</div>;
}
