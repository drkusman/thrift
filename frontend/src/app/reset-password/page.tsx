"use client";

import { Suspense, useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { useSubmitGuard } from "@/lib/use-submit-guard";
import { PasswordInput } from "@/components/PasswordInput";

function ResetPasswordContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const guard = useSubmitGuard();

  const [checking, setChecking] = useState(true);
  const [valid, setValid] = useState(false);
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!token) { setChecking(false); return; }
    api.get<boolean>(`/api/auth/reset-token-valid?token=${encodeURIComponent(token)}`)
      .then(setValid)
      .finally(() => setChecking(false));
  }, [token]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null);
      if (password !== confirm) { setError("Passwords do not match."); return; }
      setSubmitting(true);
      try {
        await api.post("/api/auth/reset-password", { token, password });
        setSuccess(true);
        setTimeout(() => router.push("/login"), 1500);
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not reset password.");
      } finally {
        setSubmitting(false);
      }
    });
  }

  return (
    <main className="flex-1 flex items-center justify-center px-4 py-16 bg-[var(--bg)]">
      <div className="w-full max-w-sm card p-8 space-y-5">
        <div className="flex flex-col items-center text-center gap-3 mb-2">
          <Image src="/logo.jpg" alt="University crest" width={56} height={56} className="brand-crest !h-14 !w-14" />
          <div>
            <h1 className="text-lg font-bold text-[var(--maroon-dark)]">Reset your password</h1>
          </div>
        </div>

        {checking ? (
          <p className="text-sm text-[var(--muted)] text-center">Checking your reset link...</p>
        ) : !valid ? (
          <div className="space-y-4">
            <p className="alert-error">This reset link is invalid or has expired.</p>
            <p className="text-center text-sm">
              <Link href="/forgot-password" className="font-semibold text-[var(--maroon)] hover:underline">Request a new link</Link>
            </p>
          </div>
        ) : success ? (
          <p className="alert-success">Password changed successfully. Redirecting to sign in...</p>
        ) : (
          <form onSubmit={onSubmit} className="space-y-5">
            <div>
              <label className="field-label">New password</label>
              <PasswordInput value={password} onChange={setPassword} minLength={6} required autoComplete="new-password" autoFocus />
            </div>
            <div>
              <label className="field-label">Confirm new password</label>
              <PasswordInput value={confirm} onChange={setConfirm} minLength={6} required autoComplete="new-password" />
            </div>
            {error && <p className="alert-error">{error}</p>}
            <button type="submit" disabled={submitting} className="btn btn-primary w-full">
              {submitting ? "Saving..." : "Reset password"}
            </button>
          </form>
        )}
      </div>
    </main>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={null}>
      <ResetPasswordContent />
    </Suspense>
  );
}
