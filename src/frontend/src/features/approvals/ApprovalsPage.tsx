import Banner from '@/shared/ui/Banner';
import EmptyState from '@/shared/ui/EmptyState';
import Spinner from '@/shared/ui/Spinner';
import { pickPrimaryAgent, useAgents, useSetAgentStatus } from '@/features/agents/hooks';
import { useActivePolicy } from '@/features/policy/hooks';
import ActivePolicyCard from './components/ActivePolicyCard';
import AgentCard from './components/AgentCard';
import ApprovalCard from './components/ApprovalCard';
import StatsRow from './components/StatsRow';
import { useApprovals, useDecideApproval, useStats } from './hooks';
import { useApprovalsStore } from './store';

export default function ApprovalsPage() {
  const approvals = useApprovals();
  const stats = useStats();
  const agents = useAgents();
  const agent = pickPrimaryAgent(agents.data);
  const policy = useActivePolicy(agent?.id);
  const decide = useDecideApproval();
  const setStatus = useSetAgentStatus();
  const decided = useApprovalsStore((s) => s.decided);
  const reset = useApprovalsStore((s) => s.reset);

  // Pending requests, plus the ones just decided here so their card can show the outcome until dismissed.
  const cards = (approvals.data ?? [])
    .filter((a) => a.status === 'pending' || decided[a.id])
    .sort((a, b) => (a.created_at ?? '').localeCompare(b.created_at ?? ''));
  const open = cards.filter((a) => a.status === 'pending' && !decided[a.id]).length;

  return (
    <div className="grid gap-6">
      {agent?.status === 'suspended' && (
        <Banner tone="deny" role="alert">
          <strong>{agent.name} is suspended.</strong> Every call from this agent is denied with AGENT_SUSPENDED until
          you resume it.
        </Banner>
      )}

      <StatsRow stats={stats.data} pendingShown={open}/>

      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <section aria-labelledby="pending-heading">
          <div className="mb-3 flex items-end justify-between">
            <div>
              <h1 id="pending-heading" className="text-lg font-semibold">
                Pending approvals
              </h1>
              <p className="text-sm text-muted">Requests auto-expire if nobody decides</p>
            </div>
          </div>
          {approvals.isPending ? (
            <Spinner label="Loading approvals"/>
          ) : cards.length === 0 ? (
            <EmptyState title="Nothing is waiting on you"
                        text="Payments inside the policy go through on their own. Anything risky will show up here first."/>
          ) : (
            <div className="grid gap-4">
              {cards.map((a) => (
                <ApprovalCard
                  key={a.id}
                  approval={a}
                  policy={policy.data}
                  localDecision={decided[a.id]}
                  busy={decide.isPending && decide.variables?.id === a.id}
                  onApprove={() => decide.mutate({id: a.id, decision: 'approve'})}
                  onDeny={() => decide.mutate({id: a.id, decision: 'deny'})}
                  onReset={() => reset(a.id)}
                />
              ))}
            </div>
          )}
        </section>

        <aside className="grid content-start gap-4">
          {agent ? (
            <AgentCard agent={agent} busy={setStatus.isPending}
                       onToggle={(suspend) => setStatus.mutate({id: agent.id, suspend})}/>
          ) : agents.isPending ? (
            <Spinner label="Loading agent"/>
          ) : null}
          <ActivePolicyCard policy={policy.data}/>
        </aside>
      </div>
    </div>
  );
}
