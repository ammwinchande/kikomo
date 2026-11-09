import StatTile from '@/shared/ui/StatTile';
import type { StatsView } from '@/shared/api/types';

export default function StatsRow({stats, pendingShown}: Readonly<{
  stats: StatsView | undefined;
  pendingShown: number
}>) {
  const pending = stats?.pending_approvals ?? pendingShown;
  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4" aria-label="Today at a glance">
      <StatTile label="Waiting for you" value={pending} hint="Needs approval" tone="brand"/>
      <StatTile label="Allowed today" value={stats?.allowed_today ?? '–'} hint="Within policy" tone="allow"/>
      <StatTile label="Blocked today" value={stats?.denied_today ?? '–'} hint="Denied" tone="deny"/>
      <StatTile
        label="Audit chain"
        value={stats ? (stats.audit_valid ? 'Intact' : 'Broken') : '–'}
        hint={stats?.audit_valid === false ? 'Fingerprints do not match' : 'Every fingerprint checks out'}
        tone={stats?.audit_valid === false ? 'deny' : 'allow'}
      />
    </div>
  );
}
