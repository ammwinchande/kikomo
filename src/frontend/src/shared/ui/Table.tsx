import type { ReactNode } from 'react';

export function Table({children, ariaLabel}: Readonly<{ children: ReactNode; ariaLabel?: string }>) {
  return (
    <div className="overflow-x-auto rounded-xl border border-line bg-white shadow-card">
      <table className="w-full text-left text-sm" aria-label={ariaLabel}>
        {children}
      </table>
    </div>
  );
}

export function Th({children, className = ''}: Readonly<{ children?: ReactNode; className?: string }>) {
  return <th scope="col"
             className={`border-b border-line bg-bg px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-muted ${className}`}>{children}</th>;
}

export function Td({children, className = '', colSpan, title}: Readonly<{
  children?: ReactNode;
  className?: string;
  colSpan?: number;
  title?: string
}>) {
  return <td colSpan={colSpan} title={title}
             className={`border-b border-line px-4 py-3 align-top text-ink ${className}`}>{children}</td>;
}
