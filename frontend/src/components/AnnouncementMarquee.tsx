"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";

type Announcement = { id: number; message: string; published: boolean; createdAt: string };

/** Member dashboard only - never rendered on admin pages. Scrolls every currently-published
 *  announcement in one continuous marquee, same pattern as GAT 2027's own news marquee. */
export function AnnouncementMarquee() {
  const [items, setItems] = useState<Announcement[] | null>(null);
  const trackRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    api.get<Announcement[]>("/api/announcements").then(setItems).catch(() => setItems([]));
  }, []);

  useEffect(() => {
    if (!trackRef.current || !items || items.length === 0) return;
    const pxPerSecond = 110;
    const distance = trackRef.current.scrollWidth / 2;
    trackRef.current.style.animationDuration = `${Math.max(6, distance / pxPerSecond)}s`;
  }, [items]);

  if (!items || items.length === 0) return null;

  return (
    <div className="marquee-bar">
      <span className="marquee-label">📢 Announcement</span>
      <div className="marquee-viewport">
        <div ref={trackRef} className="marquee-track">
          {[0, 1].map((pass) => (
            <div key={pass} className="flex">
              {items.map((a) => (
                <span key={`${pass}-${a.id}`} className="marquee-item">{a.message}</span>
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
