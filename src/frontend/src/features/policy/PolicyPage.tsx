import { useMemo } from 'react';
import Banner from '@/shared/ui/Banner';
import Button from '@/shared/ui/Button';
import EmptyState from '@/shared/ui/EmptyState';
import { pickPrimaryAgent, useAgents } from '@/features/agents/hooks';
import ActivePolicyCard from './components/ActivePolicyCard';
import ComposeCard from './components/ComposeCard';
import DraftDiffCard from './components/DraftDiffCard';
import { useActivatePolicy, useActivePolicy, useCompileDraft } from './hooks';
import { diffPolicies, prepareForActivation } from './lib/policy-rows';
import { usePolicyStore } from './store';

export default function PolicyPage() {
  const agents = useAgents(false);
  const agent = pickPrimaryAgent(agents.data);
  const active = useActivePolicy(agent?.id);
  const compile = useCompileDraft();
  const activate = useActivatePolicy(agent?.id);
  const {sentence, draft, compiler, liveVersion, setSentence, discard, dismissLive} = usePolicyStore();

  const diff = useMemo(() => (draft ? diffPolicies(active.data, draft) : null), [draft, active.data]);

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="text-lg font-semibold">Spending policy</h1>
        <p className="text-sm text-muted">
          Write the rules in a sentence and let AI draft them. The draft does nothing until you confirm it, and only
          fixed rules ever
          decide a payment.
        </p>
      </div>

      {liveVersion !== null && (
        <Banner tone="allow" action={<Button variant="link" size="sm" onClick={dismissLive}>Dismiss</Button>}>
          New policy is live. Version {liveVersion} now decides every call.
        </Banner>
      )}

      <div className="grid gap-6 lg:grid-cols-[360px_1fr]">
        <ActivePolicyCard policy={active.data} loading={active.isPending && !!agent}/>
        <div className="grid content-start gap-6">
          <ComposeCard
            sentence={sentence}
            onChange={setSentence}
            compiling={compile.isPending}
            disabled={!agent}
            onCompile={() => compile.mutate({text: sentence, allowlist: active.data?.payeeAllowlist ?? []})}
          />
          {draft && diff ? (
            <DraftDiffCard
              diff={diff}
              compiler={compiler}
              onDiscard={discard}
              activating={activate.isPending}
              onActivate={() => activate.mutate(prepareForActivation(draft, active.data))}
            />
          ) : (
            <EmptyState title="Compile a draft to see exactly what would change"
                        text="Nothing goes live until you confirm it."
                        icon={<span className="mb-3 text-2xl" aria-hidden="true">✎</span>}/>
          )}
        </div>
      </div>
    </div>
  );
}
