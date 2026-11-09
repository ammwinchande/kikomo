import type { HTMLAttributes, ReactNode } from 'react';

interface Props extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  padded?: boolean;
}

export default function Card({children, padded = true, className = '', ...rest}: Readonly<Props>) {
  return (
    <div className={`rounded-xl border border-line bg-white shadow-card ${padded ? 'p-5' : ''} ${className}`} {...rest}>
      {children}
    </div>
  );
}

export function CardHeader({title, subtitle, action}: Readonly<{
  title: ReactNode;
  subtitle?: ReactNode;
  action?: ReactNode
}>) {
  return (
    <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
      <div>
        <h2 className="text-base font-semibold text-ink">{title}</h2>
        {subtitle && <p className="mt-0.5 text-sm text-muted">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}
