"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, apiUrl, ApiError } from "@/lib/api";
import { Member } from "@/lib/types";
import { formatNaira, statusBadgeClass } from "@/lib/ui";

function RegisterForm({ onRegistered }: { onRegistered: (m: Member) => void }) {
  const [regno, setRegno] = useState("");
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [deptCode, setDeptCode] = useState("");
  const [role, setRole] = useState("MEMBER");
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [open, setOpen] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null); setMsg(null);
    try {
      const m = await api.post<Member>("/api/admin/members", { regno, fullName, phone, email, deptCode, role });
      onRegistered(m);
      setMsg(`Registered. Default password is the reg. number: ${m.regno}`);
      setRegno(""); setFullName(""); setPhone(""); setEmail(""); setDeptCode(""); setRole("MEMBER");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not register member.");
    }
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
          <label className="field-label">Department</label>
          <input value={deptCode} onChange={(e) => setDeptCode(e.target.value)} className="field-input" />
        </div>
        <div>
          <label className="field-label">Role</label>
          <select value={role} onChange={(e) => setRole(e.target.value)} className="field-input">
            <option value="MEMBER">Member</option>
            <option value="FIN_SEC">Fin. Secretary</option>
            <option value="ADMIN">Admin</option>
          </select>
        </div>
      </div>
      {error && <p className="alert-error">{error}</p>}
      {msg && <p className="alert-success">{msg}</p>}
      <button type="submit" className="btn btn-primary">Register</button>
    </form>
  );
}

function AdminMembersContent() {
  const [members, setMembers] = useState<Member[]>([]);
  const [query, setQuery] = useState("");
  const [resetMsg, setResetMsg] = useState<string | null>(null);

  function load() {
    api.get<Member[]>("/api/admin/members").then(setMembers);
  }

  useEffect(load, []);

  async function resetPassword(id: number, regno: string) {
    await api.post(`/api/admin/members/${id}/reset-password`);
    setResetMsg(`Password for ${regno} reset to their reg. number.`);
    setTimeout(() => setResetMsg(null), 5000);
  }

  const filtered = members.filter((m) =>
    m.regno.toLowerCase().includes(query.toLowerCase()) || m.fullName.toLowerCase().includes(query.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Members</h1>
        <RegisterForm onRegistered={() => load()} />
      </div>

      {resetMsg && <p className="alert-success max-w-md">{resetMsg}</p>}

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
                <td><span className={statusBadgeClass(m.status)}>{m.status}</span></td>
                <td><span className="badge badge-grey">{m.role}</span></td>
                <td className="text-right">{formatNaira(m.monthlySavingsAmount)}</td>
                <td className="text-right whitespace-nowrap">
                  <a href={apiUrl(`/api/admin/members/${m.id}/transactions/export.xlsx`)} className="text-[var(--maroon)] hover:underline text-xs font-medium mr-3">Excel</a>
                  <a href={apiUrl(`/api/admin/members/${m.id}/transactions/export.pdf`)} className="text-[var(--maroon)] hover:underline text-xs font-medium mr-3">PDF</a>
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
