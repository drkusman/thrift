"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";

function NavLink({ href, children }: { href: string; children: React.ReactNode }) {
  const pathname = usePathname();
  const active = pathname === href || (href !== "/dashboard" && href !== "/admin" && pathname.startsWith(href));
  return (
    <Link
      href={href}
      className={`px-3 py-1.5 rounded-full text-sm font-medium transition-colors ${
        active ? "bg-[var(--maroon-light)] text-[var(--maroon-dark)]" : "text-[var(--muted)] hover:text-[var(--ink)] hover:bg-[var(--maroon-light)]/60"
      }`}
    >
      {children}
    </Link>
  );
}

export function Header() {
  const { member, logout } = useAuth();
  const router = useRouter();
  if (!member) return null;
  const isStaff = member.role === "ADMIN" || member.role === "FIN_SEC";

  async function onLogout() {
    await logout();
    router.push("/login");
  }

  return (
    <header className="bg-white border-b border-[var(--line)] sticky top-0 z-50">
      <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between gap-4">
        <div className="flex items-center gap-3 shrink-0">
          <Image src="/logo.jpg" alt="ASUU-MOAUM crest" width={36} height={36} className="brand-crest rounded-full" />
          <div className="leading-tight">
            <p className="font-bold text-[var(--maroon-dark)] text-sm">ASUU-MOAUM</p>
            <p className="text-[10px] font-medium text-[var(--muted)] tracking-wide uppercase">Thrift &amp; Savings</p>
          </div>
        </div>

        <nav className="flex items-center gap-1 flex-wrap justify-center">
          {isStaff ? (
            <>
              <NavLink href="/admin">Overview</NavLink>
              <NavLink href="/admin/members">Members</NavLink>
              <NavLink href="/admin/loans">Loans</NavLink>
              <NavLink href="/admin/savings-requests">Savings requests</NavLink>
              <NavLink href="/admin/contributions">Monthly upload</NavLink>
              <NavLink href="/admin/import">Legacy import</NavLink>
            </>
          ) : (
            <>
              <NavLink href="/dashboard">Dashboard</NavLink>
              <NavLink href="/dashboard/loans">Loans</NavLink>
              <NavLink href="/dashboard/transactions">Transactions</NavLink>
              <NavLink href="/dashboard/settings">Settings</NavLink>
            </>
          )}
        </nav>

        <div className="flex items-center gap-3 text-sm shrink-0">
          <span className="hidden sm:block text-[var(--muted)]">{member.fullName.split(" ")[0]} <span className="text-xs">({member.regno})</span></span>
          <button onClick={onLogout} className="btn btn-secondary !px-3 !py-1.5 !text-xs">Log out</button>
        </div>
      </div>
    </header>
  );
}
