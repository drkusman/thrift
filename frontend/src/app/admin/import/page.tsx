"use client";

import { useEffect, useRef, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, apiUrl, ApiError } from "@/lib/api";
import { useSubmitGuard } from "@/lib/use-submit-guard";

type ConnectionInfo = { host: string; port: string; dbName: string; username: string };

type ImportKind = "banks" | "loan-types" | "members" | "ledger" | "historical-loans";

const STEPS: { kind: ImportKind; label: string; hint: string }[] = [
  { kind: "banks", label: "1. Banks", hint: "bank.csv - bankcode, name, sortcode" },
  { kind: "loan-types", label: "2. Loan types", hint: "loantype.csv - loadCode, loanName, interestRate, interestTime, maxduration" },
  { kind: "members", label: "3. Members", hint: "members.csv - regno, fullname, phone, email, ... (import first so bank accounts on member rows can match already-imported banks)" },
  { kind: "ledger", label: "4. Ledger (can take several minutes for large files)", hint: "ledger.csv - transcode, staffid, amount, date, description, transtype, transcat, drcrstatus, ..." },
  { kind: "historical-loans", label: "5. Historical loans", hint: "same ledger.csv - reconstructs a real Loan record per legacy loancode (Running/Pulsed/Completed) and links its disbursement + repayment ledger rows. Run after step 4." },
];

function ImportRow({ kind, label, hint }: { kind: ImportKind; label: string; hint: string }) {
  const guard = useSubmitGuard();
  const fileRef = useRef<HTMLInputElement>(null);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null); setResult(null);
      const file = fileRef.current?.files?.[0];
      if (!file) { setError("Choose a CSV file."); return; }
      setSubmitting(true);
      try {
        const form = new FormData();
        form.append("file", file);
        const text = await api.postForm<string>(`/api/admin/import/${kind}`, form);
        setResult(String(text));
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Import failed.");
      } finally {
        setSubmitting(false);
      }
    });
  }

  return (
    <form onSubmit={onSubmit} className="card p-5 space-y-3">
      <div>
        <h2 className="font-semibold text-[var(--ink)]">{label}</h2>
        <p className="text-xs text-[var(--muted)]">{hint}</p>
      </div>
      <div className="flex items-center gap-3">
        <input ref={fileRef} type="file" accept=".csv" className="text-sm flex-1" />
        <button type="submit" disabled={submitting} className="btn btn-primary whitespace-nowrap">
          {submitting ? "Importing..." : "Import"}
        </button>
      </div>
      {error && <p className="alert-error">{error}</p>}
      {result && <p className="alert-success">{result}</p>}
    </form>
  );
}

function BackfillRepaymentsRow() {
  const guard = useSubmitGuard();
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onClick() {
    await guard(async () => {
      setError(null); setResult(null);
      setSubmitting(true);
      try {
        const text = await api.post<string>("/api/admin/import/backfill-legacy-repayments");
        setResult(String(text));
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Backfill failed.");
      } finally {
        setSubmitting(false);
      }
    });
  }

  return (
    <div className="card p-5 space-y-3">
      <div>
        <h2 className="font-semibold text-[var(--ink)]">6. Fix legacy loan repayment amounts</h2>
        <p className="text-xs text-[var(--muted)]">
          Historical loans reconstructed by step 5 never got a monthly repayment amount, so a member whose
          only active loan is a legacy one sees an incomplete &ldquo;loan payments&rdquo; figure on their dashboard.
          This fills it in from each loan&rsquo;s own repayment history (no file needed). Safe to re-run - it only
          fills loans still missing the amount.
        </p>
      </div>
      <button type="button" onClick={onClick} disabled={submitting} className="btn btn-primary whitespace-nowrap">
        {submitting ? "Fixing..." : "Run fix"}
      </button>
      {error && <p className="alert-error">{error}</p>}
      {result && <p className="alert-success">{result}</p>}
    </div>
  );
}

function DatabaseBackupRow() {
  return (
    <div className="card p-5 space-y-3">
      <div>
        <h2 className="font-semibold text-[var(--ink)]">Database backup</h2>
        <p className="text-xs text-[var(--muted)]">
          Downloads a full SQL dump of the live database (every table, not just the legacy import
          data) straight to your computer. Useful to keep a copy on hand before running or re-running
          an import above.
        </p>
      </div>
      <a href={apiUrl("/api/admin/backup/download")} className="btn btn-primary inline-block whitespace-nowrap">
        Download backup.sql
      </a>
    </div>
  );
}

function CopyableCommand({ command }: { command: string }) {
  const [copied, setCopied] = useState(false);

  async function onCopy() {
    try {
      await navigator.clipboard.writeText(command);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // clipboard access can be denied by the browser - the command is still selectable/readable
    }
  }

  return (
    <div className="relative">
      <pre className="bg-[var(--ink)] text-[var(--bg)] text-xs rounded-lg p-3 pr-16 overflow-x-auto whitespace-pre-wrap break-all">
        {command}
      </pre>
      <button
        type="button"
        onClick={onCopy}
        className="absolute top-2 right-2 text-[10px] font-semibold px-2 py-1 rounded bg-white/10 text-white hover:bg-white/20"
      >
        {copied ? "Copied" : "Copy"}
      </button>
    </div>
  );
}

function RestoreInstructionsRow() {
  const [info, setInfo] = useState<ConnectionInfo | null>(null);

  useEffect(() => {
    api.get<ConnectionInfo>("/api/admin/backup/connection-info").then(setInfo).catch(() => {});
  }, []);

  return (
    <div className="card p-5 space-y-4 border-2 border-[#f0c9cc]">
      <div>
        <h2 className="font-semibold text-[var(--ink)]">Restore from backup</h2>
        <p className="text-xs alert-error mt-2">
          This replaces every row in the live database with whatever is in the backup file - anything
          entered or changed since that backup was taken is permanently lost. There is no undo button in
          the app for this on purpose: it has to be run by hand over SSH so a real person deliberately
          types it, and it always takes a fresh safety copy of the current data first.
        </p>
      </div>
      {!info ? (
        <p className="text-xs text-[var(--muted)]">Loading connection details...</p>
      ) : (
        <ol className="space-y-3 text-xs text-[var(--muted)] list-decimal list-inside">
          <li>
            SSH into the production server, then upload the backup.sql you want to restore (e.g. with <code>scp</code>) so it
            exists there, for example at <code>/tmp/restore.sql</code>.
          </li>
          <li>
            Take a fresh safety copy of what&rsquo;s currently in the database, in case anything goes wrong:
            <CopyableCommand
              command={`pg_dump -h ${info.host} -p ${info.port} -U ${info.username} -d ${info.dbName} -f pre-restore-$(date +%Y%m%d-%H%M%S).sql`}
            />
          </li>
          <li>
            Wipe the current data and reload it from the uploaded file (replace <code>/tmp/restore.sql</code> with the actual path):
            <CopyableCommand
              command={`psql -h ${info.host} -p ${info.port} -U ${info.username} -d ${info.dbName} -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO ${info.username}; GRANT ALL ON SCHEMA public TO public;" && psql -h ${info.host} -p ${info.port} -U ${info.username} -d ${info.dbName} -v ON_ERROR_STOP=1 -f /tmp/restore.sql`}
            />
          </li>
          <li>
            Restart the backend so it picks up the restored data cleanly:
            <CopyableCommand command="sudo systemctl restart thrift-backend" />
          </li>
        </ol>
      )}
      <p className="text-xs text-[var(--muted)]">
        Each command will prompt for the database password (the same one in the server&rsquo;s own <code>application.yml</code>).
      </p>
    </div>
  );
}

function ResetDatabaseRow() {
  const [info, setInfo] = useState<ConnectionInfo | null>(null);

  useEffect(() => {
    api.get<ConnectionInfo>("/api/admin/backup/connection-info").then(setInfo).catch(() => {});
  }, []);

  return (
    <div className="card p-5 space-y-4 border-2 border-[#f0c9cc]">
      <div>
        <h2 className="font-semibold text-[var(--ink)]">Reset database (start over with a fresh data set)</h2>
        <p className="text-xs alert-error mt-2">
          This empties every table in the live database - all members, loans, ledger entries, everything -
          so you can re-run the Legacy import steps above against a brand new set of CSVs. Same as Restore
          above: run by hand over SSH, and it takes a fresh safety copy first. The bootstrap <code>ADMIN001</code> login
          always comes back on its own after the restart below - the app recreates it automatically whenever it's missing.
        </p>
      </div>
      {!info ? (
        <p className="text-xs text-[var(--muted)]">Loading connection details...</p>
      ) : (
        <ol className="space-y-3 text-xs text-[var(--muted)] list-decimal list-inside">
          <li>
            SSH into the production server.
          </li>
          <li>
            Take a fresh safety copy of what&rsquo;s currently in the database, in case anything goes wrong:
            <CopyableCommand
              command={`pg_dump -h ${info.host} -p ${info.port} -U ${info.username} -d ${info.dbName} -f pre-reset-$(date +%Y%m%d-%H%M%S).sql`}
            />
          </li>
          <li>
            Empty every table (this keeps the table structure and Flyway&rsquo;s migration history, only the rows are removed):
            <CopyableCommand
              command={`psql -h ${info.host} -p ${info.port} -U ${info.username} -d ${info.dbName} -v ON_ERROR_STOP=1 -c "DO \\$\\$ DECLARE r RECORD; BEGIN FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history') LOOP EXECUTE 'TRUNCATE TABLE public.' || quote_ident(r.tablename) || ' CASCADE'; END LOOP; END \\$\\$;"`}
            />
          </li>
          <li>
            Restart the backend - this recreates the <code>ADMIN001</code> login automatically since the members table is now empty:
            <CopyableCommand command="sudo systemctl restart thrift-backend" />
          </li>
          <li>
            Go back to the Legacy import steps at the top of this page and upload your new set of CSVs, in order.
          </li>
        </ol>
      )}
      <p className="text-xs text-[var(--muted)]">
        Each command will prompt for the database password (the same one in the server&rsquo;s own <code>application.yml</code>).
      </p>
    </div>
  );
}

function AdminImportContent() {
  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Legacy data import</h1>
        <p className="text-sm text-[var(--muted)] mt-1">
          One-time, re-runnable import from the old system&rsquo;s CSV exports. Run in order: banks, loan types, members, then ledger.
          Re-running is safe - existing rows are upserted by their natural key, and already-imported ledger rows are skipped by transaction code.
        </p>
      </div>
      {STEPS.map((s) => (
        <ImportRow key={s.kind} kind={s.kind} label={s.label} hint={s.hint} />
      ))}
      <BackfillRepaymentsRow />
      <DatabaseBackupRow />
      <RestoreInstructionsRow />
      <ResetDatabaseRow />
    </div>
  );
}

export default function AdminImportPage() {
  return (
    <RequireAuth adminOnly>
      <AdminImportContent />
    </RequireAuth>
  );
}
