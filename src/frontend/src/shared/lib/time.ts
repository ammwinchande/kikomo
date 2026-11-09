/** "just now", "2 min ago", "3 h ago", "yesterday", or a date for older entries. */
export function relativeTime(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) return '';
  const then = new Date(iso).getTime();
  const diff = Math.max(0, now.getTime() - then);
  const s = Math.round(diff / 1000);
  if (s < 45) return 'just now';
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.round(h / 24);
  if (d === 1) return 'yesterday';
  if (d < 7) return `${d} days ago`;
  return new Date(iso).toLocaleDateString('en-US', {month: 'short', day: 'numeric'});
}

/** "12:41" style mm:ss until the deadline (hh:mm:ss past an hour); "0:00" once passed. */
export function countdown(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) return '';
  const remaining = Math.max(0, Math.floor((new Date(iso).getTime() - now.getTime()) / 1000));
  const h = Math.floor(remaining / 3600);
  const m = Math.floor((remaining % 3600) / 60);
  const s = remaining % 60;
  const mm = h > 0 ? String(m).padStart(2, '0') : String(m);
  return `${h > 0 ? `${h}:` : ''}${mm}:${String(s).padStart(2, '0')}`;
}

export function isPast(iso: string | null | undefined, now: Date = new Date()): boolean {
  return !!iso && new Date(iso).getTime() <= now.getTime();
}

/** "09:41:07" local clock time. */
export function clockTime(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  });
}

/** "15 min" or "2 h" from seconds. */
export function humanDuration(seconds: number): string {
  if (seconds % 3600 === 0) return `${seconds / 3600} h`;
  if (seconds % 60 === 0) return `${seconds / 60} min`;
  return `${seconds} s`;
}
