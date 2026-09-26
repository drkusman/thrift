"use client";

import { Fragment, useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, ApiError } from "@/lib/api";
import { formatNaira } from "@/lib/ui";

type TypeAmount = { type: string; amount: number };

type IncomeReport = {
  fiscalYearLabel: string;
  since: string;
  until: string;
  interestOnLoans: number;
  interestByLoanType: TypeAmount[];
  liquidationFees: number;
  withdrawalCot: number;
  total: number;
};

type Comparison = { previous: IncomeReport; current: IncomeReport };

type LoanInterestRow = {
  regno: string;
  fullName: string;
  loanCode: string | null;
  loanType: string;
  disbursedAt: string;
  interestAmount: number;
};

type TransactionRow = {
  regno: string;
  fullName: string;
  date: string;
  description: string | null;
  amount: number;
};

type DrillDown =
  | { kind: "interest"; since: string; loanType: string }
  | { kind: "liquidation" | "withdrawal"; since: string };

function drillDownKey(d: DrillDown) {
  return d.kind === "interest" ? `interest:${d.since}:${d.loanType}` : `${d.kind}:${d.since}`;
}

function InterestLoansTable({ since, loanType }: { since: string; loanType: string }) {
  const [rows, setRows] = useState<LoanInterestRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setRows(null);
    setError(null);
    api.get<LoanInterestRow[]>(`/api/admin/income-report/interest-loans?since=${since}&loanType=${encodeURIComponent(loanType)}`)
      .then(setRows)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load these transactions."));
  }, [since, loanType]);

  if (error) return <p className="alert-error text-xs">{error}</p>;
  if (!rows) return <p className="text-xs text-[var(--muted)] p-2">Loading...</p>;

  return (
    <table className="w-full text-xs">
      <thead>
        <tr className="text-left text-[var(--muted)]">
          <th className="p-2">Regno</th>
          <th className="p-2">Name</th>
          <th className="p-2">Loan code</th>
          <th className="p-2">Loan type</th>
          <th className="p-2">Disbursed</th>
          <th className="p-2 text-right">Interest</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-[var(--line)]">
        {rows.map((r, i) => (
          <tr key={i}>
            <td className="p-2">{r.regno}</td>
            <td className="p-2">{r.fullName}</td>
            <td className="p-2">{r.loanCode ?? "-"}</td>
            <td className="p-2">{r.loanType}</td>
            <td className="p-2">{r.disbursedAt}</td>
            <td className="p-2 text-right">{formatNaira(r.interestAmount)}</td>
          </tr>
        ))}
        {rows.length === 0 && <tr><td colSpan={6} className="p-2 text-center text-[var(--muted)]">No transactions.</td></tr>}
      </tbody>
    </table>
  );
}

function LedgerTransactionsTable({ since, endpoint }: { since: string; endpoint: string }) {
  const [rows, setRows] = useState<TransactionRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setRows(null);
    setError(null);
    api.get<TransactionRow[]>(`${endpoint}?since=${since}`)
      .then(setRows)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load these transactions."));
  }, [since, endpoint]);

  if (error) return <p className="alert-error text-xs">{error}</p>;
  if (!rows) return <p className="text-xs text-[var(--muted)] p-2">Loading...</p>;

  return (
    <table className="w-full text-xs">
      <thead>
        <tr className="text-left text-[var(--muted)]">
          <th className="p-2">Regno</th>
          <th className="p-2">Name</th>
          <th className="p-2">Date</th>
          <th className="p-2">Description</th>
          <th className="p-2 text-right">Amount</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-[var(--line)]">
        {rows.map((r, i) => (
          <tr key={i}>
            <td className="p-2">{r.regno}</td>
            <td className="p-2">{r.fullName}</td>
            <td className="p-2">{r.date}</td>
            <td className="p-2">{r.description}</td>
            <td className="p-2 text-right">{formatNaira(r.amount)}</td>
          </tr>
        ))}
        {rows.length === 0 && <tr><td colSpan={5} className="p-2 text-center text-[var(--muted)]">No transactions.</td></tr>}
      </tbody>
    </table>
  );
}

function AmountCell({ amount, onView, active }: { amount: number; onView: () => void; active: boolean }) {
  return (
    <td className="p-3 text-right">
      <div>{formatNaira(amount)}</div>
      <button onClick={onView} className="text-xs text-[var(--maroon)] hover:underline font-medium">
        {active ? "Hide" : "View"}
      </button>
    </td>
  );
}

function AdminIncomeReportContent() {
  const [comparison, setComparison] = useState<Comparison | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<DrillDown | null>(null);

  useEffect(() => {
    api.get<Comparison>("/api/admin/income-report")
      .then(setComparison)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load the income report."));
  }, []);

  function toggle(d: DrillDown) {
    setExpanded((prev) => (prev && drillDownKey(prev) === drillDownKey(d) ? null : d));
  }

  if (error) return <p className="alert-error max-w-md">{error}</p>;
  if (!comparison) return <p className="text-sm text-[var(--muted)]">Loading...</p>;

  const { previous, current } = comparison;
  // Union of loan types across both years, so a type present in only one year still shows a row (as 0 for the other).
  const loanTypes = Array.from(new Set([...previous.interestByLoanType, ...current.interestByLoanType].map((t) => t.type)));
  const amountFor = (report: IncomeReport, type: string) => report.interestByLoanType.find((t) => t.type === type)?.amount ?? 0;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Income report</h1>
      <p className="text-sm text-[var(--muted)] max-w-2xl">
        The cooperative&rsquo;s own income for FY {current.fiscalYearLabel} alongside FY {previous.fiscalYearLabel} for
        quick comparison - interest booked on loans at disbursement, the admin fee on loan liquidations, and
        the COT deducted on membership withdrawals. Click &ldquo;View&rdquo; under any figure to see exactly
        what makes it up.
      </p>

      <div className="card overflow-x-auto max-w-3xl">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-[var(--line)] text-left text-[var(--muted)]">
              <th className="p-3">Source</th>
              <th className="p-3 text-right">FY {previous.fiscalYearLabel}</th>
              <th className="p-3 text-right">FY {current.fiscalYearLabel}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--line)]">
            <tr>
              <td className="p-3 font-medium" colSpan={3}>Interest on loans</td>
            </tr>
            {loanTypes.map((type) => {
              const prevD: DrillDown = { kind: "interest", since: previous.since, loanType: type };
              const curD: DrillDown = { kind: "interest", since: current.since, loanType: type };
              return (
                <Fragment key={type}>
                  <tr>
                    <td className="p-3 pl-6 text-[var(--muted)]">{type}</td>
                    <AmountCell amount={amountFor(previous, type)} onView={() => toggle(prevD)} active={!!expanded && drillDownKey(expanded) === drillDownKey(prevD)} />
                    <AmountCell amount={amountFor(current, type)} onView={() => toggle(curD)} active={!!expanded && drillDownKey(expanded) === drillDownKey(curD)} />
                  </tr>
                  {expanded && expanded.kind === "interest" && (drillDownKey(expanded) === drillDownKey(prevD) || drillDownKey(expanded) === drillDownKey(curD)) && (
                    <tr>
                      <td colSpan={3} className="p-0">
                        <div className="bg-[var(--maroon-light)]/20 p-2">
                          <InterestLoansTable since={expanded.since} loanType={expanded.loanType} />
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
            <tr className="font-medium">
              <td className="p-3">Total interest</td>
              <td className="p-3 text-right">{formatNaira(previous.interestOnLoans)}</td>
              <td className="p-3 text-right">{formatNaira(current.interestOnLoans)}</td>
            </tr>

            <tr>
              <td className="p-3">Admin charge on liquidations</td>
              <AmountCell amount={previous.liquidationFees} onView={() => toggle({ kind: "liquidation", since: previous.since })} active={!!expanded && expanded.kind === "liquidation" && expanded.since === previous.since} />
              <AmountCell amount={current.liquidationFees} onView={() => toggle({ kind: "liquidation", since: current.since })} active={!!expanded && expanded.kind === "liquidation" && expanded.since === current.since} />
            </tr>
            {expanded && expanded.kind === "liquidation" && (
              <tr>
                <td colSpan={3} className="p-0">
                  <div className="bg-[var(--maroon-light)]/20 p-2">
                    <LedgerTransactionsTable since={expanded.since} endpoint="/api/admin/income-report/liquidation-fees" />
                  </div>
                </td>
              </tr>
            )}

            <tr>
              <td className="p-3">Admin charge on withdrawal (COT)</td>
              <AmountCell amount={previous.withdrawalCot} onView={() => toggle({ kind: "withdrawal", since: previous.since })} active={!!expanded && expanded.kind === "withdrawal" && expanded.since === previous.since} />
              <AmountCell amount={current.withdrawalCot} onView={() => toggle({ kind: "withdrawal", since: current.since })} active={!!expanded && expanded.kind === "withdrawal" && expanded.since === current.since} />
            </tr>
            {expanded && expanded.kind === "withdrawal" && (
              <tr>
                <td colSpan={3} className="p-0">
                  <div className="bg-[var(--maroon-light)]/20 p-2">
                    <LedgerTransactionsTable since={expanded.since} endpoint="/api/admin/income-report/withdrawal-cot" />
                  </div>
                </td>
              </tr>
            )}
          </tbody>
          <tfoot>
            <tr className="border-t border-[var(--line)] font-semibold text-[var(--maroon-dark)]">
              <td className="p-3">Total income</td>
              <td className="p-3 text-right">{formatNaira(previous.total)}</td>
              <td className="p-3 text-right">{formatNaira(current.total)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  );
}

export default function AdminIncomeReportPage() {
  return (
    <RequireAuth staffOnly>
      <AdminIncomeReportContent />
    </RequireAuth>
  );
}
