"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { RequireAuth } from "@/components/RequireAuth";
import { api } from "@/lib/api";
import { AdminSummary, FiscalYearBreakdown, FiscalYearTrend, Loan, Member, SavingsRequest } from "@/lib/types";
import { formatNaira } from "@/lib/ui";

const LIFECYCLE_COLORS: Record<string, string> = {
  Pending: "#d4a017",
  Running: "#6b1f2e",
  Pulsed: "#a6455f",
  Completed: "#0b7a3b",
};

function darken(hex: string, amount = 0.32) {
  const clean = hex.replace("#", "");
  const num = parseInt(clean.length === 3 ? clean.split("").map((c) => c + c).join("") : clean, 16);
  const r = Math.round(((num >> 16) & 255) * (1 - amount));
  const g = Math.round(((num >> 8) & 255) * (1 - amount));
  const b = Math.round((num & 255) * (1 - amount));
  return `rgb(${r}, ${g}, ${b})`;
}

const LOAN_TYPE_COLORS: Record<string, string> = {
  "Emergency Loan": "#d4a017",
  "Main Loan": "#6b1f2e",
  "Product Loan": "#0f766e",
};
const LOAN_TYPE_FALLBACK_COLORS = ["#a6455f", "#0b7a3b", "#7f1d1d", "#4c1520"];

function monthLabel(month: string) {
  const [y, m] = month.split("-");
  return new Date(Number(y), Number(m) - 1, 1).toLocaleString("en-NG", { month: "short", year: "2-digit" });
}

function TrendBarChart({ title, trend, color }: { title: string; trend: FiscalYearTrend | null; color: string }) {
  const chartData = (trend?.points ?? []).map((p) => ({ ...p, label: monthLabel(p.month) }));
  return (
    <div className="card p-5">
      <div className="flex items-baseline justify-between mb-4">
        <h3 className="font-semibold text-[var(--ink)]">{title}</h3>
        {trend && <span className="text-xs text-[var(--muted)]">FY {trend.label}</span>}
      </div>
      <ResponsiveContainer width="100%" height={240}>
        <BarChart data={chartData} margin={{ top: 4, right: 8, left: 8, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--line)" vertical={false} />
          <XAxis dataKey="label" tick={{ fontSize: 11, fill: "var(--muted)" }} axisLine={{ stroke: "var(--line)" }} tickLine={false} />
          <YAxis tick={{ fontSize: 11, fill: "var(--muted)" }} axisLine={false} tickLine={false}
            tickFormatter={(v: number) => (Math.abs(v) >= 1_000_000 ? `${(v / 1_000_000).toFixed(1)}M` : `${Math.round(v / 1000)}k`)} />
          <Tooltip formatter={(v) => formatNaira(Number(v))} labelStyle={{ color: "var(--ink)" }}
            contentStyle={{ borderRadius: 8, borderColor: "var(--line)" }} />
          <Bar dataKey="amount" fill={color} radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

function loanTypeColor(type: string, index: number) {
  return LOAN_TYPE_COLORS[type] ?? LOAN_TYPE_FALLBACK_COLORS[index % LOAN_TYPE_FALLBACK_COLORS.length];
}

function LoansGrantedPieChart({ breakdown }: { breakdown: FiscalYearBreakdown | null }) {
  const slices = breakdown?.slices ?? [];
  const total = slices.reduce((sum, s) => sum + s.amount, 0);
  return (
    <div className="card p-5">
      <div className="flex items-baseline justify-between mb-4">
        <h3 className="font-semibold text-[var(--ink)]">Loans granted by type</h3>
        {breakdown && <span className="text-xs text-[var(--muted)]">FY {breakdown.label}</span>}
      </div>
      {total === 0 ? (
        <p className="text-sm text-[var(--muted)] py-16 text-center">No loans granted this fiscal year yet.</p>
      ) : (
        <ResponsiveContainer width="100%" height={240}>
          <PieChart>
            <Pie data={slices} dataKey="amount" nameKey="type" innerRadius={50} outerRadius={80} paddingAngle={2}>
              {slices.map((s, i) => (
                <Cell key={s.type} fill={loanTypeColor(s.type, i)} />
              ))}
            </Pie>
            <Tooltip formatter={(v) => formatNaira(Number(v))} contentStyle={{ borderRadius: 8, borderColor: "var(--line)" }} />
            <Legend wrapperStyle={{ fontSize: 12 }} />
          </PieChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}

/** A classic tilted "3D" pie: two stacked ellipses (a darkened one offset down as the drum wall, a
 *  normal one on top as the face) fake the extrusion recharts doesn't support natively. */
function LoanLifecyclePieChart3D({ breakdown }: { breakdown: FiscalYearBreakdown | null }) {
  const slices = breakdown?.slices ?? [];
  const total = slices.reduce((sum, s) => sum + s.amount, 0);
  const colorFor = (stage: string) => LIFECYCLE_COLORS[stage] ?? "#999";

  return (
    <div className="card p-5 max-w-2xl">
      <div className="flex items-baseline justify-between mb-4">
        <h3 className="font-semibold text-[var(--ink)]">Loan lifecycle</h3>
        <span className="text-xs text-[var(--muted)]">All time</span>
      </div>
      {total === 0 ? (
        <p className="text-sm text-[var(--muted)] py-16 text-center">No loans yet.</p>
      ) : (
        <>
          <div className="relative" style={{ height: 240 }}>
            <div className="absolute inset-0" style={{ top: 16, transform: "scaleY(0.82)", transformOrigin: "center" }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={slices} dataKey="amount" nameKey="type" outerRadius={85} isAnimationActive={false}>
                    {slices.map((s) => (
                      <Cell key={s.type} fill={darken(colorFor(s.type))} stroke="none" />
                    ))}
                  </Pie>
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="absolute inset-0" style={{ transform: "scaleY(0.82)", transformOrigin: "center" }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={slices} dataKey="amount" nameKey="type" outerRadius={85} paddingAngle={1}>
                    {slices.map((s) => (
                      <Cell key={s.type} fill={colorFor(s.type)} stroke="#fff" strokeWidth={1.5} />
                    ))}
                  </Pie>
                  <Tooltip formatter={(v, n) => [`${v} loan${Number(v) === 1 ? "" : "s"}`, n]}
                    contentStyle={{ borderRadius: 8, borderColor: "var(--line)" }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </div>
          <div className="flex justify-center gap-5 mt-3">
            {slices.map((s) => (
              <span key={s.type} className="flex items-center gap-1.5 text-xs text-[var(--muted)]">
                <span className="w-2.5 h-2.5 rounded-full inline-block" style={{ background: colorFor(s.type) }} />
                {s.type} ({s.amount})
              </span>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function AdminOverviewContent() {
  const [members, setMembers] = useState<Member[]>([]);
  const [pendingLoans, setPendingLoans] = useState<Loan[]>([]);
  const [pendingSavings, setPendingSavings] = useState<SavingsRequest[]>([]);
  const [summary, setSummary] = useState<AdminSummary | null>(null);
  const [savingsTrend, setSavingsTrend] = useState<FiscalYearTrend | null>(null);
  const [loansGrantedByType, setLoansGrantedByType] = useState<FiscalYearBreakdown | null>(null);
  const [loanRepaymentsTrend, setLoanRepaymentsTrend] = useState<FiscalYearTrend | null>(null);
  const [loanLifecycle, setLoanLifecycle] = useState<FiscalYearBreakdown | null>(null);

  useEffect(() => {
    api.get<Member[]>("/api/admin/members").then(setMembers);
    api.get<Loan[]>("/api/admin/loans/pending").then(setPendingLoans);
    api.get<SavingsRequest[]>("/api/admin/savings-requests/pending").then(setPendingSavings);
    api.get<AdminSummary>("/api/admin/analytics/summary").then(setSummary);
    api.get<FiscalYearTrend>("/api/admin/analytics/monthly-savings").then(setSavingsTrend);
    api.get<FiscalYearBreakdown>("/api/admin/analytics/loans-granted-by-type").then(setLoansGrantedByType);
    api.get<FiscalYearTrend>("/api/admin/analytics/loan-repayments").then(setLoanRepaymentsTrend);
    api.get<FiscalYearBreakdown>("/api/admin/analytics/loan-lifecycle").then(setLoanLifecycle);
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

      <div>
        <h2 className="text-lg font-semibold text-[var(--ink)] mb-3">Cooperative financial position</h2>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="stat-tile">
            <p className="stat-label">Total monthly savings</p>
            <p className="stat-value !text-xl">{summary ? formatNaira(summary.totalMonthlySavings) : "..."}</p>
          </div>
          <div className="stat-tile">
            <p className="stat-label">Total loan payments</p>
            <p className="stat-value !text-xl">{summary ? formatNaira(summary.totalLoanPayments) : "..."}</p>
          </div>
          <div className="stat-tile">
            <p className="stat-label">Total monthly deductions</p>
            <p className="stat-value !text-xl">{summary ? formatNaira(summary.totalMonthlyDeductions) : "..."}</p>
          </div>
          <div className="stat-tile">
            <p className="stat-label">Total savings (pooled)</p>
            <p className="stat-value !text-xl">{summary ? formatNaira(summary.totalSavings) : "..."}</p>
          </div>
          <div className="stat-tile">
            <p className="stat-label">Total loan balance</p>
            <p className="stat-value !text-xl">{summary ? formatNaira(summary.totalLoanBalance) : "..."}</p>
          </div>
          <div className="stat-tile">
            <p className="stat-label">Cooperative equity</p>
            <p className={`stat-value !text-xl ${summary && summary.totalEquity < 0 ? "!text-rose-700" : ""}`}>
              {summary ? formatNaira(summary.totalEquity) : "..."}
            </p>
          </div>
        </div>
      </div>

      <div>
        <h2 className="text-lg font-semibold text-[var(--ink)] mb-3">
          Fiscal year trends <span className="text-sm font-normal text-[var(--muted)]">(1 Nov - 31 Oct)</span>
        </h2>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <TrendBarChart title="Monthly savings collected" trend={savingsTrend} color="var(--maroon)" />
          <LoansGrantedPieChart breakdown={loansGrantedByType} />
          <TrendBarChart title="Loan repayments received" trend={loanRepaymentsTrend} color="#0b7a3b" />
        </div>
      </div>

      <LoanLifecyclePieChart3D breakdown={loanLifecycle} />
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
