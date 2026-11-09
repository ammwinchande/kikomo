import type { AgentView } from '@/shared/api/types';
import { money } from '@/shared/lib/money';
import Button from '@/shared/ui/Button';
import Card from '@/shared/ui/Card';
import Pill from '@/shared/ui/Pill';

export default function AgentCard({agent, busy, onToggle}: Readonly<{
  agent: AgentView;
  busy: boolean;
  onToggle: (suspend: boolean) => void
}>) {
  const suspended = agent.status === 'suspended';
  const budget = agent.daily_budget_minor;
  const pct = budget ? Math.min(100, Math.round((agent.spent_today_minor / budget) * 100)) : 0;
  return (
    <Card>
      <div className="flex items-center gap-3">
        <span
          className="flex h-10 w-10 items-center justify-center rounded-full bg-chip text-base font-bold text-brand-deep"
          aria-hidden="true">
          {agent.name.slice(0, 1).toUpperCase()}
        </span>
        <div className="min-w-0 flex-1">
          <div className="truncate font-semibold">{agent.name}</div>
          <div className="text-xs text-muted">Connected over MCP</div>
        </div>
        <Pill tone={suspended ? 'deny' : 'allow'}>{suspended ? 'Suspended' : 'Active'}</Pill>
      </div>

      <div className="mt-4">
        <div className="flex items-center justify-between text-xs text-muted">
          <span>Spent today</span>
          <span className="font-semibold text-ink">
            {money(agent.spent_today_minor)}
            {budget !== null && <span className="font-normal text-muted"> / {money(budget)}</span>}
          </span>
        </div>
        <progress className="mt-1.5 h-2 overflow-hidden rounded-full bg-soft" aria-valuenow={pct}
                  aria-valuemin={0} aria-valuemax={100} aria-label="Daily budget used">
          <div className={`h-full rounded-full ${pct >= 90 ? 'bg-deny-fg' : 'bg-brand'}`} style={{width: `${pct}%`}}/>
        </progress>
      </div>

      <div className={`mt-4 rounded-lg border p-3 ${suspended ? 'border-deny-fg/20 bg-deny-bg' : 'border-line bg-bg'}`}>
        <div className="text-sm font-semibold">{suspended ? 'Agent is suspended' : 'Kill switch: suspend agent'}</div>
        <p className="mt-0.5 text-xs text-muted">
          {suspended ? 'Every call is denied with AGENT_SUSPENDED until you resume it.' : 'Stops every call from this agent immediately. Resume any time.'}
        </p>
        <Button variant={suspended ? 'primary' : 'danger'} className="mt-3 w-full" onClick={() => onToggle(!suspended)}
                loading={busy}>
          {suspended ? 'Resume agent' : 'Suspend agent'}
        </Button>
      </div>
    </Card>
  );
}
