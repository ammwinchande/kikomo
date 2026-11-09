import { useEffect } from 'react';
import EmptyState from '@/shared/ui/EmptyState';
import Spinner from '@/shared/ui/Spinner';
import { pickPrimaryAgent, useAgents } from '@/features/agents/hooks';
import { useActivePolicy } from '@/features/policy/hooks';
import CallsTable from './components/CallsTable';
import WhyPanel from './components/WhyPanel';
import { useCalls } from './hooks';
import { useActivityStore } from './store';

export default function ActivityPage() {
  const calls = useCalls();
  const agents = useAgents();
  const agent = pickPrimaryAgent(agents.data);
  const policy = useActivePolicy(agent?.id);
  const {selectedCallId, explainOpen, select, toggleExplain} = useActivityStore();

  const list = calls.data ?? [];
  useEffect(() => {
    if (!selectedCallId && list.length > 0) select(list[0].id);
  }, [list, selectedCallId, select]);
  const selected = list.find((c) => c.id === selectedCallId) ?? null;

  function getCall() {
    return list.length === 0 ? (
      <EmptyState title="No calls yet" text="Run a demo scenario or connect an agent and its calls will appear here."
                  icon={<span className="mb-3 text-2xl" aria-hidden="true">◎</span>}/>
    ) : (
      <CallsTable calls={list} selectedId={selectedCallId} onSelect={select}/>
    );
  }

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="text-lg font-semibold">Every call, and why it was decided that way</h1>
        <p className="text-sm text-muted">Select a row to see the reasoning</p>
      </div>
      <div className="grid gap-6 lg:grid-cols-[1fr_420px]">
        <section aria-label="Agent calls">
          {calls.isPending ?
            <Spinner label="Loading calls"/>
            : getCall()}
        </section>
        <aside>
          <WhyPanel call={selected} policy={policy.data} agentSuspended={agent?.status === 'suspended'}
                    explainOpen={explainOpen} onToggleExplain={toggleExplain}/>
        </aside>
      </div>
    </div>
  );
}
