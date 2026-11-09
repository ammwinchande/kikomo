import { NavLink, Outlet } from 'react-router-dom';
import Logo from '@/logo';
import Button from '@/shared/ui/Button';
import Toasts from '@/shared/ui/Toasts';
import { useHealth, useLogout } from '@/features/auth/hooks';
import { useStats } from '@/features/approvals/hooks';

export const NAV = [
  {to: '/approvals', label: 'Approvals'},
  {to: '/activity', label: 'Activity'},
  {to: '/audit', label: 'Audit'},
  {to: '/policy', label: 'Policy'},
  {to: '/demo', label: 'Demo'},
] as const;

export default function AppShell() {
  const health = useHealth();
  const stats = useStats();
  const logout = useLogout();
  const pending = stats.data?.pending_approvals ?? 0;
  const live = health.data?.paypal_mode === 'live';

  return (
    <div className="flex min-h-screen flex-col">
      <div className="h-1 bg-gradient-to-br from-brand to-teal" aria-hidden="true"/>
      <header className="border-b border-line bg-white">
        <div className="mx-auto flex max-w-shell flex-wrap items-center justify-between gap-4 px-8 py-3.5">
          <div className="flex items-center gap-3">
            <Logo/>
            <div className="flex flex-col leading-tight">
              <span className="text-xl font-bold tracking-tight">Kikomo</span>
              <span className="text-xs text-muted">Payment guardrails for AI agents</span>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <span
              className="inline-flex items-center gap-1.5 rounded-full bg-chip px-3 py-1.5 text-xs font-semibold text-brand-deep">
              <span className="h-1.5 w-1.5 rounded-full bg-brand" aria-hidden="true"/>
              {live ? 'PayPal sandbox · live credentials' : 'PayPal sandbox · no real money'}
            </span>
            <Button variant="ghost" onClick={() => logout.mutate()} loading={logout.isPending}>
              Sign out
            </Button>
          </div>
        </div>
        <nav aria-label="Sections" className="mx-auto flex max-w-shell gap-1 overflow-x-auto px-8">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({isActive}) =>
                `inline-flex items-center gap-2 whitespace-nowrap border-b-[3px] px-3.5 py-3 text-sm ${
                  isActive ? 'border-brand font-semibold text-ink' : 'border-transparent font-medium text-muted hover:text-ink'
                }`
              }
            >
              {item.label}
              {item.to === '/approvals' && pending > 0 && (
                <span
                  className="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-brand px-1.5 text-xs font-bold text-white"
                  aria-label={`${pending} pending`}
                >
                  {pending}
                </span>
              )}
            </NavLink>
          ))}
        </nav>
      </header>
      {health.data?.mock && (
        <output className="bg-approve-bg text-center text-xs font-semibold text-approve-fg">
          <div className="mx-auto max-w-shell px-8 py-1.5">MOCK PAYPAL — no real money moves</div>
        </output>
      )}
      <main className="mx-auto w-full max-w-shell flex-1 px-8 py-7">
        <Outlet/>
      </main>
      <Toasts/>
    </div>
  );
}
