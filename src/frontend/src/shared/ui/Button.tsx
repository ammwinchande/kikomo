import type { ButtonHTMLAttributes, ReactNode } from 'react';

export type ButtonVariant = 'primary' | 'ghost' | 'danger' | 'link';
export type ButtonSize = 'md' | 'sm';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  children: ReactNode;
}

const base =
  'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60 whitespace-nowrap';
const variants: Record<ButtonVariant, string> = {
  primary: 'bg-brand text-white hover:bg-brand-hover',
  ghost: 'bg-white text-ink border border-line hover:bg-hover',
  danger: 'bg-deny-fg text-white hover:bg-deny-hover',
  link: 'bg-transparent text-brand hover:underline px-0',
};
const sizes: Record<ButtonSize, string> = {md: 'px-3.5 py-2 text-sm', sm: 'px-2.5 py-1.5 text-xs'};

export default function Button({
                                 variant = 'primary',
                                 size = 'md',
                                 loading = false,
                                 className = '',
                                 children,
                                 disabled,
                                 ...rest
                               }: Readonly<Props>) {
  return (
    <button
      type="button"
      className={`${base} ${variants[variant]} ${variant === 'link' ? '' : sizes[size]} ${className}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading && <span className="h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent"
                        aria-hidden="true"/>}
      {children}
    </button>
  );
}
