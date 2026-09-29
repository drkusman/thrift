"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import {
  IconDashboard, IconLoan, IconTransactions, IconSettings, IconMembers,
  IconSavings, IconImport, IconLogout, IconChevronLeft, IconChevronRight, IconGuarantee,
  IconChevronDown,
} from "./icons";

const STORAGE_KEY = "thrift-sidebar-collapsed";
const THEME_KEY = "thrift-theme";
const VIEW_MODE_KEY = "thrift-sidebar-view";

function currentTheme(): "light" | "dark" {
  const attr = document.documentElement.getAttribute("data-theme");
  if (attr === "light" || attr === "dark") return attr;
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

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
  { href: "/admin/member-balances", label: "Member Balances", icon: IconMembers },
  {
    label: "Loans", icon: IconLoan,
    children: [
      { href: "/admin/loans", label: "Loan Approval" },
      { href: "/admin/remittance", label: "Monthly Remittance" },
      { href: "/admin/loans/list", label: "Loan List" },
      { href: "/admin/loans/manager", label: "Loan Manager" },
      { href: "/admin/loans/liquidation-requests", label: "Liquidation requests" },
      { href: "/admin/contributions", label: "Monthly Upload" },
      { href: "/admin/contributions/loan-application-fees", label: "Loan Application Fee Upload" },
    ],
  },
  {
    label: "Operations", icon: IconSavings,
    children: [
      { href: "/admin/savings-requests", label: "Savings requests" },
      { href: "/admin/ios-requests", label: "IOS requests" },
      { href: "/admin/ios-calculation", label: "IOS Calculation" },
      { href: "/admin/members/withdrawal", label: "Membership Withdrawal" },
      { href: "/admin/withdrawal-requests", label: "Withdrawal requests" },
      { href: "/admin/bad-debt", label: "Bad debt list" },
      { href: "/admin/income-report", label: "Income report" },
    ],
  },
  { href: "/admin/announcements", label: "Announcements", icon: IconSavings },
  { href: "/admin/import", label: "Legacy import", icon: IconImport, adminOnly: true },
];

type NavLink = { href: string; label: string; icon?: (p: { className?: string }) => React.ReactElement; exact?: boolean; adminOnly?: boolean };
type NavGroup = { label: string; icon: (p: { className?: string }) => React.ReactElement; children: NavLink[] };
type NavItem = NavLink | NavGroup;

function isGroup(item: NavItem): item is NavGroup {
  return "children" in item;
}

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
  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>({});
  const [dark, setDark] = useState(false);
  const [viewMode, setViewMode] = useState<"member" | "staff">(() => {
    try {
      return localStorage.getItem(VIEW_MODE_KEY) === "member" ? "member" : "staff";
    } catch {
      return "staff";
    }
  });

  useEffect(() => {
    setDark(currentTheme() === "dark");
  }, []);

  function toggleTheme() {
    const next = currentTheme() === "dark" ? "light" : "dark";
    try {
      localStorage.setItem(THEME_KEY, next);
    } catch {
      // per-viewer convenience only - fine if it doesn't persist
    }
    document.documentElement.setAttribute("data-theme", next);
    setDark(next === "dark");
  }

  function isGroupOpen(group: NavGroup) {
    const explicit = openGroups[group.label];
    if (explicit !== undefined) return explicit;
    return group.children.some((c) => pathname.startsWith(c.href));
  }

  function toggleGroup(label: string, currentlyOpen: boolean) {
    setOpenGroups((prev) => ({ ...prev, [label]: !currentlyOpen }));
  }

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

  function setMode(mode: "member" | "staff") {
    setViewMode(mode);
    try {
      localStorage.setItem(VIEW_MODE_KEY, mode);
    } catch {
      // per-viewer convenience only - fine if it doesn't persist
    }
  }

  if (!member) return null;
  // Admins and Fin. Secretaries are members of the thrift too - they get their own savings/loans
  // dashboard alongside the admin tools, switched via the Member/Staff toggle above the nav rather
  // than showing both link sets stacked at once (keeps the sidebar from getting too long).
  const isStaff = member.role === "ADMIN" || member.role === "FIN_SEC" || member.role === "PRESIDENT";
  const isAdmin = member.role === "ADMIN";
  const staffLinks = isAdmin ? STAFF_LINKS : STAFF_LINKS.filter((item) => !("adminOnly" in item && item.adminOnly));
  const groups = isStaff
    ? [{ label: null, links: viewMode === "staff" ? staffLinks : MEMBER_LINKS }]
    : [{ label: null, links: MEMBER_LINKS }];

  async function onLogout() {
    await logout();
    router.push("/login");
  }

  return (
    <aside
      className={`relative shrink-0 bg-[#3a0f18] border-r border-white/10 flex flex-col min-h-0 transition-[width] duration-200 ${
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

      <div className={`flex items-center gap-3 px-4 h-16 border-b border-white/10 ${collapsed ? "justify-center px-0" : ""}`}>
        <Image src="/logo.jpg" alt="ASUU-MOAUM crest" width={32} height={32} className="brand-crest !h-8 !w-8 shrink-0" />
        {!collapsed && (
          <div className="leading-tight overflow-hidden">
            <p className="font-bold text-white text-sm truncate">ASUU-MOAUM</p>
            <p className="text-[10px] font-medium text-white/50 tracking-wide uppercase truncate">Thrift &amp; Savings</p>
          </div>
        )}
      </div>

      {isStaff && !collapsed && (
        <div className="px-2 pt-3">
          <div className="grid grid-cols-2 gap-1 p-1 rounded-lg bg-black/25">
            <button
              type="button"
              onClick={() => setMode("member")}
              className={`rounded-md py-1.5 text-xs font-semibold transition-colors ${
                viewMode === "member" ? "bg-[var(--gold)] text-[#3a0f18]" : "text-white/60 hover:text-white"
              }`}
            >
              Member
            </button>
            <button
              type="button"
              onClick={() => setMode("staff")}
              className={`rounded-md py-1.5 text-xs font-semibold transition-colors ${
                viewMode === "staff" ? "bg-[var(--gold)] text-[#3a0f18]" : "text-white/60 hover:text-white"
              }`}
            >
              {isAdmin ? "Admin" : member.role === "PRESIDENT" ? "President" : "Fin. Sec."}
            </button>
          </div>
        </div>
      )}

      <nav className="flex-1 py-3 px-2 space-y-4 overflow-y-auto">
        {groups.map((group) => (
          <div key={group.label ?? "main"} className="space-y-1">
            {group.label && !collapsed && (
              <p className="px-3 pt-1 pb-1 text-[10px] font-bold uppercase tracking-wide text-white/40">
                {group.label}
              </p>
            )}
            {(group.links as NavItem[]).map((item) => {
              if (isGroup(item)) {
                const open = isGroupOpen(item);
                const Icon = item.icon;
                return (
                  <div key={item.label}>
                    <button
                      type="button"
                      onClick={() => (collapsed ? router.push(item.children[0].href) : toggleGroup(item.label, open))}
                      title={collapsed ? item.label : undefined}
                      className={`w-full flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                        collapsed ? "justify-center px-0" : ""
                      } text-white/70 hover:text-white hover:bg-white/10`}
                    >
                      <Icon className="w-5 h-5 shrink-0" />
                      {!collapsed && <span className="truncate flex-1 text-left">{item.label}</span>}
                      {!collapsed && (
                        <IconChevronDown className={`w-3.5 h-3.5 shrink-0 transition-transform ${open ? "rotate-180" : ""}`} />
                      )}
                    </button>
                    {!collapsed && open && (
                      <div className="mt-1 ml-4 pl-3 border-l border-white/10 space-y-1">
                        {item.children.map((child) => {
                          const active = pathname === child.href || pathname.startsWith(child.href + "/");
                          return (
                            <Link
                              key={child.href}
                              href={child.href}
                              className={`block rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                                active
                                  ? "bg-[var(--gold)]/20 text-[var(--gold)]"
                                  : "text-white/70 hover:text-white hover:bg-white/10"
                              }`}
                            >
                              {child.label}
                            </Link>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              }

              const link = item;
              const active = link.exact ? pathname === link.href : pathname.startsWith(link.href);
              const Icon = link.icon!;
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  title={collapsed ? link.label : undefined}
                  className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                    collapsed ? "justify-center px-0" : ""
                  } ${
                    active
                      ? "bg-[var(--gold)]/20 text-[var(--gold)]"
                      : "text-white/70 hover:text-white hover:bg-white/10"
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

      <div className="border-t border-white/10 p-2 space-y-1">
        {!collapsed && (
          <p className="px-3 pt-1 pb-2 text-xs text-white/60 truncate">
            {member.fullName} <span className="block text-[10px] text-white/40">{member.regno}</span>
          </p>
        )}
        <button
          type="button"
          onClick={toggleTheme}
          title={collapsed ? (dark ? "Switch to light mode" : "Switch to dark mode") : undefined}
          className={`w-full flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-white/70 hover:text-white hover:bg-white/10 ${
            collapsed ? "justify-center px-0" : ""
          }`}
        >
          <span className="w-5 h-5 shrink-0 grid place-items-center text-base leading-none">{dark ? "☀️" : "🌙"}</span>
          {!collapsed && <span>{dark ? "Light mode" : "Dark mode"}</span>}
        </button>
        <button
          onClick={onLogout}
          title={collapsed ? "Log out" : undefined}
          className={`w-full flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-white/70 hover:text-white hover:bg-white/10 ${
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
