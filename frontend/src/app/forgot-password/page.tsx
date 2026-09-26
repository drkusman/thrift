"use client";

import { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { useSubmitGuard } from "@/lib/use-submit-guard";

type ResetRequestResult = { maskedEmail: string | null; devModeLink: string | null };

export default function ForgotPasswordPage() {
  const guard = useSubmitGuard();
  const [regno, setRegno] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<ResetRequestResult | null>(null);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null);
      setSubmitting(true);
      try {
        const resetBaseUrl = `${window.location.origin}/reset-password`;
        const res = await api.post<ResetRequestResult>("/api/auth/forgot-password", { regno: regno.trim(), resetBaseUrl });
        setResult(res);
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Something went wrong. Please try again.");
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
            <h1 className="text-lg font-bold text-[var(--maroon-dark)]">Forgot your password?</h1>
            <p className="text-sm text-[var(--muted)] mt-1">
              Enter your registration number and, if an email is on file, we&rsquo;ll send you a reset link.
            </p>
          </div>
        </div>

        {result ? (
          <div className="space-y-4">
            {result.maskedEmail && (
              <p className="alert-success">A reset link has been sent to {result.maskedEmail}. It expires in 30 minutes.</p>
            )}
            {result.devModeLink && (
              <div className="alert-success space-y-2">
                <p>No email is configured to deliver this yet - here&rsquo;s the reset link directly:</p>
                <a href={result.devModeLink} className="block break-all font-medium text-[var(--maroon)] hover:underline">
                  {result.devModeLink}
                </a>
              </div>
            )}
            {!result.maskedEmail && !result.devModeLink && (
              <p className="text-sm text-[var(--muted)]">
                If that registration number matches an active account with an email on file, a reset link has been sent to it.
              </p>
            )}
            <p className="text-center text-sm">
              <Link href="/login" className="font-semibold text-[var(--maroon)] hover:underline">Back to sign in</Link>
            </p>
          </div>
        ) : (
          <form onSubmit={onSubmit} className="space-y-5">
            <div>
              <label className="field-label">Reg. Number</label>
              <input
                className="field-input"
                value={regno}
                onChange={(e) => setRegno(e.target.value)}
                placeholder="e.g. A00006"
                required
                autoFocus
              />
            </div>
            {error && <p className="alert-error">{error}</p>}
            <button type="submit" disabled={submitting} className="btn btn-primary w-full">
              {submitting ? "Sending..." : "Send reset link"}
            </button>
            <p className="text-center text-sm">
              <Link href="/login" className="font-semibold text-[var(--maroon)] hover:underline">Back to sign in</Link>
            </p>
          </form>
        )}
      </div>
    </main>
  );
}
