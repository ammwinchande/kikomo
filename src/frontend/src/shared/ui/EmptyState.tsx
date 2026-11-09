import type { ReactNode } from 'react';

export default function EmptyState({title, text, icon}: Readonly<{ title: string; text?: string; icon?: ReactNode }>) {
  return (
    <div
      className="flex flex-col items-center justify-center rounded-xl border border-dashed border-line bg-white px-6 py-12 text-center">
      {icon ?? <span className="mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-allow-bg text-allow-fg"
                     aria-hidden="true">✓</span>}
      <h3 className="text-base font-semibold text-ink">{title}</h3>
      {text && <p className="mt-1 max-w-md text-sm text-muted">{text}</p>}
    </div>
  );
}
