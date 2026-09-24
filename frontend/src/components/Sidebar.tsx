"use client";

import { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import {
  IconDashboard, IconLoan, IconTransactions, IconSettings, IconMembers,
  IconSavings, IconUpload, IconImport, IconLogout, IconChevronLeft, IconChevronRight, IconGuarantee,
} from "./icons";

const STORAGE_KEY = "thrift-sidebar-collapsed";

const MEMBER_LINKS = [
  { href: "/dashboard", label: "Dashboard", icon: IconDashboard, exact: true },
  { href: "/dashboard/loans", label: "Loans", icon: IconLoan },
  { href: "/dashboard/guarantees", label: "Guarantee requests", icon: IconGuarantee },
  { href: "/dashboard/transactions", label: "Transactions", icon: IconTransactions },
  { href: "/dashboard/settings", label: "Settings", icon: IconSettings },
];

const STAFF_LINKS = [
  { href: "/admin", label: "Overview", icon: IconDashboard, exact: true },
  { href: "/admin/members", label: "Members", icon: IconMembers },
  { href: "/admin/loans", label: "Loans", icon: IconLoan },
  { href: "/admin/savings-requests", label: "Savings requests", icon: IconSavings },
  { href: "/admin/contributions", label: "Monthly upload", icon: IconUpload },
  { href: "/admin/import", label: "Legacy import", icon: IconImport },
];

export function Sidebar() {
  const { member, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) === "1";
    } catch {
      return false;
    }
  });

  function toggle() {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem(STORAGE_KEY, next ? "1" : "0");
      } catch {
        // per-viewer convenience only - fine if it doesn't persist
      }
      return next;
    });
  }

  if (!member) return null;
  // Admins and Fin. Secretaries are members of the thrift too - they get their own savings/loans
  // dashboard on top of the admin tools, not instead of it.
  const isStaff = member.role === "ADMIN" || member.role === "FIN_SEC";
  const groups = isStaff
    ? [{ label: "My account", links: MEMBER_LINKS }, { label: "Administration", links: STAFF_LINKS }]
    : [{ label: null, links: MEMBER_LINKS }];

  async function onLogout() {
    await logout();
    router.push("/login");
  }

  return (
    <aside
      className={`relative shrink-0 bg-white border-r border-[var(--line)] flex flex-col min-h-0 transition-[width] duration-200 ${
        collapsed ? "w-[68px]" : "w-[240px]"
      }`}
    >
      <button
        onClick={toggle}
        title={collapsed ? "Expand sidebar" : "Collapse sidebar"}
        aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
        className="absolute -right-3.5 top-14 z-10 w-7 h-7 rounded-full bg-white border border-[var(--line)] shadow-md flex items-center justify-center text-[var(--muted)] hover:text-[var(--maroon-dark)] hover:border-[var(--maroon)]"
      >
        {collapsed ? <IconChevronRight className="w-3.5 h-3.5" /> : <IconChevronLeft className="w-3.5 h-3.5" />}
      </button>

      <div className={`flex items-center gap-3 px-4 h-16 border-b border-[var(--line)] ${collapsed ? "justify-center px-0" : ""}`}>
        <Image src="/logo.jpg" alt="ASUU-MOAUM crest" width={32} height={32} className="brand-crest !h-8 !w-8 shrink-0" />
        {!collapsed && (
          <div className="leading-tight overflow-hidden">
            <p className="font-bold text-[var(--maroon-dark)] text-sm truncate">ASUU-MOAUM</p>
            <p className="text-[10px] font-medium text-[var(--muted)] tracking-wide uppercase truncate">Thrift &amp; Savings</p>
          </div>
        )}
      </div>

      <nav className="flex-1 py-3 px-2 space-y-4 overflow-y-auto">
        {groups.map((group) => (
          <div key={group.label ?? "main"} className="space-y-1">
            {group.label && !collapsed && (
              <p className="px-3 pt-1 pb-1 text-[10px] font-bold uppercase tracking-wide text-[var(--muted)]/70">
                {group.label}
              </p>
            )}
            {group.links.map((link) => {
              const active = link.exact ? pathname === link.href : pathname.startsWith(link.href);
              const Icon = link.icon;
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  title={collapsed ? link.label : undefined}
                  className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                    collapsed ? "justify-center px-0" : ""
                  } ${
                    active
                      ? "bg-[var(--maroon-light)] text-[var(--maroon-dark)]"
                      : "text-[var(--muted)] hover:text-[var(--ink)] hover:bg-[var(--maroon-light)]/60"
                  }`}
                >
                  <Icon className="w-5 h-5 shrink-0" />
                  {!collapsed && <span className="truncate">{link.label}</span>}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>

      <div className="border-t border-[var(--line)] p-2 space-y-1">
        {!collapsed && (
          <p className="px-3 pt-1 pb-2 text-xs text-[var(--muted)] truncate">
            {member.fullName} <span className="block text-[10px]">{member.regno}</span>
          </p>
        )}
        <button
          onClick={onLogout}
          title={collapsed ? "Log out" : undefined}
          className={`w-full flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-[var(--muted)] hover:text-[var(--ink)] hover:bg-[var(--maroon-light)]/60 ${
            collapsed ? "justify-center px-0" : ""
          }`}
        >
          <IconLogout className="w-5 h-5 shrink-0" />
          {!collapsed && <span>Log out</span>}
        </button>
      </div>
    </aside>
  );
}
