"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { useAuth } from "@/lib/auth-context";
import { api, ApiError } from "@/lib/api";
import { Bank, IosPayoutRequest, MembershipWithdrawalRequest, MembershipWithdrawalSummary, SavingsRequest, UnpaidIosCredit } from "@/lib/types";
import { formatNaira, statusBadgeClass } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

function SettingsContent() {
  const { member, refresh } = useAuth();
  const bankGuard = useSubmitGuard();
  const savingsGuard = useSubmitGuard();
  const iosGuard = useSubmitGuard();
  const [banks, setBanks] = useState<Bank[]>([]);
  const [bankId, setBankId] = useState<number | "">("");
  const [accountNo, setAccountNo] = useState("");
  const [bankMsg, setBankMsg] = useState<string | null>(null);
  const [bankError, setBankError] = useState<string | null>(null);

  const [amount, setAmount] = useState("");
  const [savingsError, setSavingsError] = useState<string | null>(null);
  const [savingsMsg, setSavingsMsg] = useState<string | null>(null);
  const [requests, setRequests] = useState<SavingsRequest[]>([]);

  const [iosCredits, setIosCredits] = useState<UnpaidIosCredit[]>([]);
  const [iosError, setIosError] = useState<string | null>(null);
  const [iosMsg, setIosMsg] = useState<string | null>(null);
  const [iosRequests, setIosRequests] = useState<IosPayoutRequest[]>([]);
  const [applyingId, setApplyingId] = useState<number | null>(null);

  const withdrawalGuard = useSubmitGuard();
  const [withdrawalSummary, setWithdrawalSummary] = useState<MembershipWithdrawalSummary | null>(null);
  const [withdrawalRequests, setWithdrawalRequests] = useState<MembershipWithdrawalRequest[]>([]);
  const [withdrawalError, setWithdrawalError] = useState<string | null>(null);
  const [withdrawalMsg, setWithdrawalMsg] = useState<string | null>(null);
  const [requestingWithdrawal, setRequestingWithdrawal] = useState(false);

  function loadIos() {
    api.get<UnpaidIosCredit[]>("/api/me/ios-available").then(setIosCredits);
    api.get<IosPayoutRequest[]>("/api/me/ios-requests").then(setIosRequests);
  }

  function loadWithdrawal() {
    api.get<MembershipWithdrawalSummary>("/api/me/withdrawal-summary").then(setWithdrawalSummary);
    api.get<MembershipWithdrawalRequest[]>("/api/me/withdrawal-requests").then(setWithdrawalRequests);
  }

  useEffect(() => {
    api.get<Bank[]>("/api/banks").then(setBanks);
    api.get<SavingsRequest[]>("/api/me/savings-requests").then(setRequests);
    loadIos();
    loadWithdrawal();
  }, []);

  const hasPendingWithdrawal = withdrawalRequests.some((r) => r.status === "PENDING");

  async function onRequestWithdrawal() {
    await withdrawalGuard(async () => {
      setWithdrawalError(null); setWithdrawalMsg(null);
      setRequestingWithdrawal(true);
      try {
        await api.post<MembershipWithdrawalRequest>("/api/me/withdrawal-requests");
        setWithdrawalMsg("Withdrawal request submitted - awaiting admin approval.");
        loadWithdrawal();
      } catch (e) {
        setWithdrawalError(e instanceof ApiError ? e.message : "Could not submit request.");
      } finally {
        setRequestingWithdrawal(false);
      }
    });
  }

  useEffect(() => {
    // Prefill the form from the member's currently saved bank details.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (member?.bankId) setBankId(member.bankId);
    if (member?.accountNo) setAccountNo(member.accountNo);
  }, [member]);

  async function onSaveBank(e: React.FormEvent) {
    e.preventDefault();
    await bankGuard(async () => {
      setBankError(null); setBankMsg(null);
      if (!bankId) { setBankError("Choose a bank."); return; }
      try {
        await api.post("/api/me/bank-account", { bankId, accountNo });
        await refresh();
        setBankMsg("Bank details saved.");
      } catch (e) {
        setBankError(e instanceof ApiError ? e.message : "Could not save bank details.");
      }
    });
  }

  async function onRequestSavings(e: React.FormEvent) {
    e.preventDefault();
    await savingsGuard(async () => {
      setSavingsError(null); setSavingsMsg(null);
      try {
        const req = await api.post<SavingsRequest>("/api/me/savings-requests", { amount: Number(amount) });
        await refresh();
        setRequests((prev) => [req, ...prev]);
        setAmount("");
        setSavingsMsg(req.status === "APPROVED" ? "Amount updated immediately." : "Request submitted - awaiting admin approval (amounts above ₦70,000 require approval).");
      } catch (e) {
        setSavingsError(e instanceof ApiError ? e.message : "Could not submit request.");
      }
    });
  }

  async function onApplyForIos(credit: UnpaidIosCredit) {
    await iosGuard(async () => {
      setIosError(null); setIosMsg(null);
      setApplyingId(credit.ledgerEntryId);
      try {
        const req = await api.post<IosPayoutRequest>("/api/me/ios-requests", { ledgerEntryId: credit.ledgerEntryId });
        setIosRequests((prev) => [req, ...prev]);
        setIosMsg(`Request submitted for ${formatNaira(req.requestedAmount)} - awaiting admin processing.`);
        loadIos();
      } catch (e) {
        setIosError(e instanceof ApiError ? e.message : "Could not submit request.");
      } finally {
        setApplyingId(null);
      }
    });
  }

  return (
    <div className="space-y-8 max-w-lg">
      <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Settings</h1>

      <form onSubmit={onSaveBank} className="card p-6 space-y-4">
        <h2 className="font-semibold text-[var(--ink)]">Bank account</h2>
        <div>
          <label className="field-label">Bank</label>
          <select value={bankId} onChange={(e) => setBankId(e.target.value ? Number(e.target.value) : "")} className="field-input" required>
            <option value="">Select...</option>
            {banks.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        </div>
        <div>
          <label className="field-label">Account number</label>
          <input value={accountNo} onChange={(e) => setAccountNo(e.target.value)} required className="field-input" />
        </div>
        {bankError && <p className="alert-error">{bankError}</p>}
        {bankMsg && <p className="alert-success">{bankMsg}</p>}
        <button type="submit" className="btn btn-primary">Save</button>
      </form>

      <form onSubmit={onRequestSavings} className="card p-6 space-y-4">
        <h2 className="font-semibold text-[var(--ink)]">Monthly savings amount</h2>
        <p className="text-sm text-[var(--muted)]">
          Current: {member ? formatNaira(member.monthlySavingsAmount) : "-"}. Minimum ₦20,000; amounts above ₦70,000 need admin approval.
        </p>
        <div>
          <label className="field-label">New amount</label>
          <input type="number" min={20000} value={amount} onChange={(e) => setAmount(e.target.value)} required className="field-input" />
        </div>
        {savingsError && <p className="alert-error">{savingsError}</p>}
        {savingsMsg && <p className="alert-success">{savingsMsg}</p>}
        <button type="submit" className="btn btn-primary">Request change</button>

        {requests.length > 0 && (
          <div className="pt-3 border-t border-[var(--line)]">
            <p className="text-xs font-bold uppercase tracking-wide text-[var(--muted)] mb-2">Past requests</p>
            <ul className="text-sm space-y-1.5">
              {requests.map((r) => (
                <li key={r.id} className="flex justify-between items-center">
                  <span>{formatNaira(r.requestedAmount)}</span>
                  <span className={statusBadgeClass(r.status)}>{r.status}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </form>

      <div className="card p-6 space-y-4">
        <h2 className="font-semibold text-[var(--ink)]">Interest on savings (IOS)</h2>
        <p className="text-sm text-[var(--muted)]">
          Each year&rsquo;s unpaid interest is applied for on its own, not blended into one total - there&rsquo;s
          no amount to type in, and this isn&rsquo;t limited to the current fiscal year.
        </p>
        {iosError && <p className="alert-error">{iosError}</p>}
        {iosMsg && <p className="alert-success">{iosMsg}</p>}

        {iosCredits.length === 0 ? (
          <p className="text-sm text-[var(--muted)]">No unpaid IOS to apply for.</p>
        ) : (
          <ul className="space-y-2">
            {iosCredits.map((c) => (
              <li key={c.ledgerEntryId} className="flex items-center justify-between gap-3 p-3 rounded-lg bg-[var(--maroon-light)]/40">
                <div>
                  <p className="font-medium text-[var(--ink)]">{formatNaira(c.amount)}</p>
                  <p className="text-xs text-[var(--muted)]">{c.description} &middot; {c.date}</p>
                </div>
                <button
                  onClick={() => onApplyForIos(c)}
                  disabled={applyingId === c.ledgerEntryId}
                  className="btn btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {applyingId === c.ledgerEntryId ? "Applying..." : "Apply"}
                </button>
              </li>
            ))}
          </ul>
        )}

        {iosRequests.length > 0 && (
          <div className="pt-3 border-t border-[var(--line)]">
            <p className="text-xs font-bold uppercase tracking-wide text-[var(--muted)] mb-2">Past requests</p>
            <ul className="text-sm space-y-1.5">
              {iosRequests.map((r) => (
                <li key={r.id} className="flex justify-between items-center">
                  <span>{formatNaira(r.requestedAmount)}</span>
                  <span className={statusBadgeClass(r.status)}>{r.status}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      <div className="card p-6 space-y-4">
        <h2 className="font-semibold text-[var(--ink)]">Membership withdrawal</h2>
        <p className="text-sm text-[var(--muted)]">
          Leaving the cooperative pays out your savings less a cost-on-turnover (COT) charge, once every
          loan you have is fully liquidated. Requesting here just notifies the admin - nothing happens to
          your account until they process it.
        </p>
        {withdrawalError && <p className="alert-error">{withdrawalError}</p>}
        {withdrawalMsg && <p className="alert-success">{withdrawalMsg}</p>}

        {withdrawalSummary && (
          <div className="text-sm space-y-1">
            <p>Total savings: {formatNaira(withdrawalSummary.totalSavings)}</p>
            <p>Total loan: {formatNaira(withdrawalSummary.totalLoan)}</p>
            <p>COT: {formatNaira(withdrawalSummary.cot)}</p>
            <p className="font-semibold text-[var(--ink)]">Withdrawable now: {formatNaira(withdrawalSummary.withdrawableAmount)}</p>
            {!withdrawalSummary.canWithdraw && (
              <p className="text-xs text-[var(--muted)]">You still have running loans - these must be liquidated before withdrawal can be finalized.</p>
            )}
          </div>
        )}

        <button
          onClick={onRequestWithdrawal}
          disabled={hasPendingWithdrawal || requestingWithdrawal}
          className="btn btn-danger disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {requestingWithdrawal ? "Submitting..." : hasPendingWithdrawal ? "Request pending" : "Request withdrawal"}
        </button>

        {withdrawalRequests.length > 0 && (
          <div className="pt-3 border-t border-[var(--line)]">
            <p className="text-xs font-bold uppercase tracking-wide text-[var(--muted)] mb-2">Past requests</p>
            <ul className="text-sm space-y-1.5">
              {withdrawalRequests.map((r) => (
                <li key={r.id} className="flex justify-between items-center">
                  <span>{r.requestedAt.slice(0, 10)}</span>
                  <span className={statusBadgeClass(r.status)}>{r.status}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}

export default function SettingsPage() {
  return (
    <RequireAuth>
      <SettingsContent />
    </RequireAuth>
  );
}
