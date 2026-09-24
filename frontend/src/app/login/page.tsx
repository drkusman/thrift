"use client";

import { useState } from "react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { ApiError } from "@/lib/api";
import { useSubmitGuard } from "@/lib/use-submit-guard";
import { PasswordInput } from "@/components/PasswordInput";

export default function LoginPage() {
  const { login } = useAuth();
  const router = useRouter();
  const guard = useSubmitGuard();
  const [regno, setRegno] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null);
      setSubmitting(true);
      try {
        const member = await login(regno.trim(), password);
        if (member.mustChangePassword) router.push("/change-password");
        else if (member.role === "ADMIN" || member.role === "FIN_SEC") router.push("/admin");
        else router.push("/dashboard");
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) setError("Incorrect regno or password.");
        else setError("Something went wrong. Please try again.");
      } finally {
        setSubmitting(false);
      }
    });
  }

  return (
    <main className="flex-1 flex flex-col lg:flex-row lg:min-h-screen bg-[var(--bg)]">
      <div className="relative hidden lg:block lg:flex-1">
        <Image src="/campus.jpg" alt="University campus" fill priority className="object-cover" />
        <div className="absolute inset-0 bg-gradient-to-t from-[var(--maroon-dark)] via-[var(--maroon-dark)]/30 to-transparent" />
        <div className="absolute bottom-0 left-0 right-0 p-10 text-white">
          <p className="text-xs font-bold tracking-[0.2em] uppercase text-[var(--gold)] mb-2">Rev. Fr. Moses Orshio Adasu University, Makurdi</p>
          <h2 className="text-2xl font-semibold leading-snug max-w-md">
            Building financial security for ASUU-MOAUM members, one contribution at a time.
          </h2>
        </div>
      </div>

      <div className="flex-1 flex items-center justify-center px-4 py-16">
        <form onSubmit={onSubmit} className="w-full max-w-sm card p-8 space-y-5">
          <div className="flex flex-col items-center text-center gap-3 mb-2">
            <Image src="/logo.jpg" alt="University crest" width={64} height={64} className="brand-crest !h-16 !w-16" />
            <div>
              <h1 className="text-lg font-bold text-[var(--maroon-dark)]">ASUU-MOAUM Thrift</h1>
              <p className="text-sm text-[var(--muted)] mt-1">Sign in with your registration number.</p>
            </div>
          </div>

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

          <div>
            <label className="field-label">Password</label>
            <PasswordInput value={password} onChange={setPassword} required />
            <p className="text-xs text-[var(--muted)] mt-1.5">First time? Your password is your reg. number.</p>
          </div>

          {error && <p className="alert-error">{error}</p>}

          <button type="submit" disabled={submitting} className="btn btn-primary w-full">
            {submitting ? "Signing in..." : "Sign in"}
          </button>
        </form>
      </div>
    </main>
  );
}
