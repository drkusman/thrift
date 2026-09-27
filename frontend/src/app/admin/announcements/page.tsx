"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { api, ApiError } from "@/lib/api";
import { useSubmitGuard } from "@/lib/use-submit-guard";

type Announcement = { id: number; message: string; published: boolean; createdAt: string };

function AnnouncementRow({ a, onChanged }: { a: Announcement; onChanged: () => void }) {
  const guard = useSubmitGuard();
  const [editing, setEditing] = useState(false);
  const [message, setMessage] = useState(a.message);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function startEdit() {
    setMessage(a.message);
    setError(null);
    setEditing(true);
  }

  function cancelEdit() {
    setMessage(a.message);
    setError(null);
    setEditing(false);
  }

  async function save(published: boolean, exitEditingOnSuccess: boolean) {
    await guard(async () => {
      setError(null);
      setBusy(true);
      try {
        await api.put(`/api/admin/announcements/${a.id}`, { message, published });
        if (exitEditingOnSuccess) setEditing(false);
        onChanged();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not save.");
      } finally {
        setBusy(false);
      }
    });
  }

  async function remove() {
    await guard(async () => {
      setError(null);
      setBusy(true);
      try {
        await api.del(`/api/admin/announcements/${a.id}`);
        onChanged();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not delete.");
      } finally {
        setBusy(false);
      }
    });
  }

  return (
    <div className="p-4 space-y-2">
      <div className="flex items-center gap-2">
        <span className={`badge ${a.published ? "badge-green" : "badge-grey"}`}>{a.published ? "Published" : "Hidden"}</span>
        <span className="text-xs text-[var(--muted)]">{a.createdAt.slice(0, 10)}</span>
      </div>
      {editing ? (
        <textarea value={message} onChange={(e) => setMessage(e.target.value)} className="field-input" rows={2} autoFocus />
      ) : (
        <p className="text-sm text-[var(--ink)] whitespace-pre-wrap">{a.message}</p>
      )}
      {error && <p className="alert-error text-sm">{error}</p>}
      <div className="flex gap-2">
        {editing ? (
          <>
            <button onClick={() => save(a.published, true)} disabled={busy} className="btn btn-primary text-xs">Save</button>
            <button onClick={cancelEdit} disabled={busy} className="btn btn-secondary text-xs">Cancel</button>
          </>
        ) : (
          <button onClick={startEdit} className="btn btn-secondary text-xs">Edit</button>
        )}
        <button onClick={() => save(!a.published, false)} disabled={busy} className="btn btn-gold text-xs">
          {a.published ? "Unpublish" : "Publish"}
        </button>
        <button onClick={remove} disabled={busy} className="btn btn-danger text-xs">Delete</button>
      </div>
    </div>
  );
}

function AdminAnnouncementsContent() {
  const guard = useSubmitGuard();
  const [items, setItems] = useState<Announcement[]>([]);
  const [message, setMessage] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function load() {
    api.get<Announcement[]>("/api/admin/announcements").then(setItems);
  }

  useEffect(load, []);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    await guard(async () => {
      setError(null);
      if (!message.trim()) { setError("Enter a message."); return; }
      setSubmitting(true);
      try {
        await api.post("/api/admin/announcements", { message: message.trim() });
        setMessage("");
        load();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Could not create announcement.");
      } finally {
        setSubmitting(false);
      }
    });
  }

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h1 className="text-2xl font-bold text-[var(--maroon-dark)]">Announcements</h1>
        <p className="text-sm text-[var(--muted)] mt-1">
          Scrolled across every member&rsquo;s own dashboard - never shown on admin pages. Every
          published one runs at once; unpublish or delete to take it down.
        </p>
      </div>

      <form onSubmit={onSubmit} className="card p-6 space-y-3">
        <label className="field-label">New announcement</label>
        <textarea value={message} onChange={(e) => setMessage(e.target.value)} className="field-input" rows={2}
          placeholder="e.g. AGM holds Saturday 10am at the main auditorium" />
        {error && <p className="alert-error">{error}</p>}
        <button type="submit" disabled={submitting} className="btn btn-primary">
          {submitting ? "Posting..." : "Post announcement"}
        </button>
      </form>

      <div className="card divide-y divide-[var(--line)]">
        {items.map((a) => <AnnouncementRow key={a.id} a={a} onChanged={load} />)}
        {items.length === 0 && <p className="p-4 text-sm text-[var(--muted)]">No announcements yet.</p>}
      </div>
    </div>
  );
}

export default function AdminAnnouncementsPage() {
  return (
    <RequireAuth staffOnly>
      <AdminAnnouncementsContent />
    </RequireAuth>
  );
}
