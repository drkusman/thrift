"use client";

import { useState } from "react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api, ApiError } from "@/lib/api";
import { useSubmitGuard } from "@/lib/use-submit-guard";

export default function ChangePasswordPage() {
  const { member, refresh, loading, logout } = useAuth();
  const router = useRouter();
  const guard = useSubmitGuard();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!loading && !member) {
    router.push("/login");
    return null;
  }

  async function onSwitchAccount() {
    await logout();
    router.push("/login");
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null);
      if (newPassword !== confirm) {
        setError("New password and confirmation do not match.");
        return;
      }
      setSubmitting(true);
      try {
        await api.post("/api/me/password", { currentPassword, newPassword });
        await refresh();
        if (member?.role === "ADMIN" || member?.role === "FIN_SEC") router.push("/admin");
        else router.push("/dashboard");
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not change password.");
      } finally {
        setSubmitting(false);
      }
    });
  }

  return (
    <main className="flex-1 flex items-center justify-center px-4 py-16 bg-[var(--bg)]">
      <form onSubmit={onSubmit} className="w-full max-w-sm card p-8 space-y-5">
        <div className="flex flex-col items-center text-center gap-3 mb-2">
          <Image src="/logo.jpg" alt="University crest" width={56} height={56} className="brand-crest !h-14 !w-14" />
          <div>
            <h1 className="text-lg font-bold text-[var(--maroon-dark)]">Change your password</h1>
            <p className="text-sm text-[var(--muted)] mt-1">
              Your current password is your reg. number. Choose a new one to continue.
            </p>
          </div>
        </div>

        <div>
          <label className="field-label">Current password</label>
          <input
            type="password"
            className="field-input"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
          />
        </div>
        <div>
          <label className="field-label">New password</label>
          <input
            type="password"
            className="field-input"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            minLength={6}
            required
          />
        </div>
        <div>
          <label className="field-label">Confirm new password</label>
          <input
            type="password"
            className="field-input"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            minLength={6}
            required
          />
        </div>

        {error && <p className="alert-error">{error}</p>}

        <button type="submit" disabled={submitting} className="btn btn-primary w-full">
          {submitting ? "Saving..." : "Change password"}
        </button>

        <p className="text-center text-sm text-[var(--muted)]">
          Not you?{" "}
          <button type="button" onClick={onSwitchAccount} className="font-semibold text-[var(--maroon)] hover:underline">
            Log out and use a different account
          </button>
        </p>
      </form>
    </main>
  );
}
