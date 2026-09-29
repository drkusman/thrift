"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { RequireAuth } from "@/components/RequireAuth";
import { BankSearchSelect } from "@/components/BankSearchSelect";
import { useAuth } from "@/lib/auth-context";
import { api, apiUrl, ApiError } from "@/lib/api";
import { Bank, Member } from "@/lib/types";
import { formatNaira, statusBadgeClass } from "@/lib/ui";
import { useSubmitGuard } from "@/lib/use-submit-guard";

function RegisterForm({ onRegistered }: { onRegistered: (m: Member) => void }) {
  const guard = useSubmitGuard();
  const [regno, setRegno] = useState("");
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [payPoint, setPayPoint] = useState("");
  const [monthlySavingsAmount, setMonthlySavingsAmount] = useState("20000");
  const [banks, setBanks] = useState<Bank[]>([]);
  const [bankId, setBankId] = useState<number | "">("");
  const [accountNo, setAccountNo] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (open && banks.length === 0) api.get<Bank[]>("/api/banks").then(setBanks);
  }, [open, banks.length]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null); setMsg(null);
      const amount = Number(monthlySavingsAmount);
      if (!amount || amount < 20000) { setError("Monthly savings must be at least ₦20,000."); return; }
      if (accountNo && !/^\d{10}$/.test(accountNo)) { setError("Account number must be exactly 10 digits."); return; }
      try {
        const m = await api.post<Member>("/api/admin/members", {
          regno, fullName, phone, email, payPoint: payPoint || null,
          monthlySavingsAmount: amount, bankId: bankId || null, accountNo: accountNo || null,
        });
        onRegistered(m);
        setMsg(`Registered. Default password is the reg. number: ${m.regno}`);
        setRegno(""); setFullName(""); setPhone(""); setEmail(""); setPayPoint("");
        setMonthlySavingsAmount("20000"); setBankId(""); setAccountNo("");
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not register member.");
      }
    });
  }

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} className="btn btn-primary">+ Register member</button>
    );
  }

  return (
    <form onSubmit={onSubmit} className="card p-6 space-y-4 max-w-lg">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-[var(--ink)]">Register a new member</h2>
        <button type="button" onClick={() => setOpen(false)} className="text-[var(--muted)] hover:text-[var(--ink)] text-sm">Cancel</button>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="field-label">Reg. number</label>
          <input value={regno} onChange={(e) => setRegno(e.target.value)} required className="field-input" />
        </div>
        <div>
          <label className="field-label">Full name</label>
          <input value={fullName} onChange={(e) => setFullName(e.target.value)} required className="field-input" />
        </div>
        <div>
          <label className="field-label">Phone</label>
          <input value={phone} onChange={(e) => setPhone(e.target.value)} className="field-input" />
        </div>
        <div>
          <label className="field-label">Email</label>
          <input value={email} onChange={(e) => setEmail(e.target.value)} className="field-input" />
        </div>
        <div>
          <label className="field-label">Pay point</label>
          <select value={payPoint} onChange={(e) => setPayPoint(e.target.value)} className="field-input">
            <option value="">Select...</option>
            <option value="CHS">CHS</option>
            <option value="MAIN">MAIN</option>
          </select>
        </div>
        <div>
          <label className="field-label">Monthly savings</label>
          <input
            type="number" min={20000} step={1000}
            value={monthlySavingsAmount}
            onChange={(e) => setMonthlySavingsAmount(e.target.value)}
            required
            className="field-input"
          />
        </div>
        <div>
          <label className="field-label">Bank</label>
          <BankSearchSelect options={banks} value={bankId} onChange={setBankId} />
        </div>
        <div>
          <label className="field-label">Account number</label>
          <input
            value={accountNo}
            onChange={(e) => setAccountNo(e.target.value.replace(/\D/g, "").slice(0, 10))}
            inputMode="numeric"
            maxLength={10}
            className="field-input"
          />
        </div>
      </div>
      {error && <p className="alert-error">{error}</p>}
      {msg && <p className="alert-success">{msg}</p>}
      <button type="submit" className="btn btn-primary">Register</button>
    </form>
  );
}

const STATUSES: Member["status"][] = ["ACTIVE", "WITHDRAWN", "RETIRED", "DECEASED", "INACTIVE"];

function AdminMembersContent() {
  const { member: currentMember } = useAuth();
  const isAdmin = currentMember?.role === "ADMIN";
  const [members, setMembers] = useState<Member[]>([]);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<Member["status"] | "ALL">("ALL");
  const [resetMsg, setResetMsg] = useState<string | null>(null);
  const [resetError, setResetError] = useState<string | null>(null);
  const [roleMsg, setRoleMsg] = useState<string | null>(null);
  const [roleError, setRoleError] = useState<string | null>(null);
  const [swappingId, setSwappingId] = useState<number | null>(null);

  function load() {
    api.get<Member[]>("/api/admin/members").then(setMembers);
  }

  useEffect(load, []);

  async function resetPassword(id: number, regno: string) {
    setResetError(null); setResetMsg(null);
    try {
      await api.post(`/api/admin/members/${id}/reset-password`);
      setResetMsg(`Password for ${regno} has been reset to their reg. number (${regno}). They'll be asked to set a new one on their next sign-in.`);
    } catch (e) {
      setResetError(e instanceof ApiError ? e.message : `Could not reset the password for ${regno}. Please try again.`);
    }
  }

  async function swapRole(id: number, regno: string, newRole: "FIN_SEC" | "PRESIDENT") {
    setRoleError(null); setRoleMsg(null);
    setSwappingId(id);
    try {
      const updated = await api.post<Member>(`/api/admin/members/${id}/role`, { role: newRole });
      setMembers((prev) => prev.map((m) => (m.id === id ? updated : m)));
      setRoleMsg(`${regno} is now ${newRole === "FIN_SEC" ? "Fin. Sec." : "President"}.`);
    } catch (e) {
      setRoleError(e instanceof ApiError ? e.message : `Could not change the role for ${regno}. Please try again.`);
    } finally {
      setSwappingId(null);
    }
  }

  const filtered = members.filter((m) =>
    (statusFilter === "ALL" || m.status === statusFilter) &&
    (m.regno.toLowerCase().includes(query.toLowerCase()) || m.fullName.toLowerCase().includes(query.toLowerCase()))
  );

  const exportParams = new URLSearchParams();
  if (statusFilter !== "ALL") exportParams.set("status", statusFilter);
  if (query.trim()) exportParams.set("query", query.trim());
  const exportQuery = exportParams.toString();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Members</h1>
        <div className="flex gap-2 flex-wrap">
          <a href={apiUrl(`/api/admin/members/export.xlsx?${exportQuery}`)} className="btn btn-secondary">Download (Excel)</a>
          <a href={apiUrl(`/api/admin/members/export.pdf?${exportQuery}`)} className="btn btn-secondary">Download (PDF)</a>
          <RegisterForm onRegistered={() => load()} />
        </div>
      </div>

      {resetMsg && (
        <p className="alert-success max-w-md">
          {resetMsg} <Link href="/login" className="font-semibold underline">Go to login page</Link>
        </p>
      )}
      {resetError && <p className="alert-error max-w-md">{resetError}</p>}
      {roleMsg && <p className="alert-success max-w-md">{roleMsg}</p>}
      {roleError && <p className="alert-error max-w-md">{roleError}</p>}

      <div className="flex flex-wrap gap-2">
        {(["ALL", ...STATUSES] as const).map((s) => {
          const count = s === "ALL" ? members.length : members.filter((m) => m.status === s).length;
          return (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors ${
                statusFilter === s
                  ? "bg-[var(--maroon-dark)] text-white border-[var(--maroon-dark)]"
                  : "bg-white text-[var(--muted)] border-[var(--line)] hover:border-[var(--maroon)]"
              }`}
            >
              {s} ({count})
            </button>
          );
        })}
      </div>

      <input
        placeholder="Search by regno or name..."
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        className="field-input w-full max-w-sm"
      />

      <div className="card overflow-x-auto">
        <table className="table-elegant">
          <thead>
            <tr>
              <th>Regno</th>
              <th>Name</th>
              <th>Status</th>
              <th>Role</th>
              <th className="text-right">Monthly savings</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((m) => (
              <tr key={m.id}>
                <td className="font-semibold">{m.regno}</td>
                <td>{m.fullName}</td>
                <td>
                  <span className={statusBadgeClass(m.status)}>{m.status}</span>
                  {m.badDebt && <span className="badge badge-red ml-1">BAD DEBT</span>}
                </td>
                <td><span className="badge badge-grey">{m.role}</span></td>
                <td className="text-right">{formatNaira(m.monthlySavingsAmount)}</td>
                <td className="text-right whitespace-nowrap">
                  <a href={apiUrl(`/api/admin/members/${m.id}/transactions/export.xlsx`)} className="text-[var(--maroon)] hover:underline text-xs font-medium mr-3">Excel</a>
                  <a href={apiUrl(`/api/admin/members/${m.id}/transactions/export.pdf`)} className="text-[var(--maroon)] hover:underline text-xs font-medium mr-3">PDF</a>
                  {m.status === "ACTIVE" && (
                    <Link href={`/admin/members/withdrawal?memberId=${m.id}`} className="text-[var(--maroon)] hover:underline text-xs font-medium mr-3">Withdrawal</Link>
                  )}
                  {isAdmin && (m.role === "FIN_SEC" || m.role === "PRESIDENT") && (
                    <button
                      onClick={() => swapRole(m.id, m.regno, m.role === "FIN_SEC" ? "PRESIDENT" : "FIN_SEC")}
                      disabled={swappingId === m.id}
                      title={`Swap to ${m.role === "FIN_SEC" ? "President" : "Fin. Sec."}`}
                      className="text-[var(--maroon)] hover:underline text-xs font-medium mr-3 disabled:opacity-50"
                    >
                      {swappingId === m.id ? "Swapping..." : `Swap to ${m.role === "FIN_SEC" ? "President" : "Fin. Sec."}`}
                    </button>
                  )}
                  <button onClick={() => resetPassword(m.id, m.regno)} className="text-[var(--muted)] hover:underline text-xs font-medium">Reset password</button>
                </td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr><td colSpan={6} className="text-center py-8 text-[var(--muted)]">No members found.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default function AdminMembersPage() {
  return (
    <RequireAuth staffOnly>
      <AdminMembersContent />
    </RequireAuth>
  );
}
