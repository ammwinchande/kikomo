import Banner from '@/shared/ui/Banner';
import Pill from '@/shared/ui/Pill';
import { useDecideApproval } from '@/features/approvals/hooks';
import ScenarioGrid from './components/ScenarioGrid';
import { ConnectCard, MaliciousPageCard, RecordingScript } from './components/SideCards';
import StepFeed from './components/StepFeed';
import { demoUnavailable, pendingApprovalId, useConnectInfo, useRun, useStartRun } from './hooks';
import { useDemoStore } from './store';

export default function DemoPage() {
  const {runId, clear} = useDemoStore();
  const start = useStartRun();
  const run = useRun(runId);
  const connect = useConnectInfo();
  const decide = useDecideApproval();
  const off = demoUnavailable(connect.error) || demoUnavailable(start.error);
  const approvalId = pendingApprovalId(run.data);
  const busy = start.isPending || run.data?.status === 'running';

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold">Demo runner</h1>
          <p className="max-w-2xl text-sm text-muted">
            A scripted agent talks to Kikomo over real MCP, so what you see here is what a judge's agent would see. All
            money is PayPal
            sandbox money.
          </p>
        </div>
        <Pill tone={off ? 'deny' : 'allow'}>{off ? 'Demo mode off' : 'Demo mode on'}</Pill>
      </div>

      {off && (
        <Banner tone="deny" role="alert">
          The demo runner is switched off on this server (DEMO_MODE=false). Connect your own agent instead.
        </Banner>
      )}

      <ScenarioGrid onRun={(s) => start.mutate(s)} running={start.isPending ? (start.variables ?? null) : null}
                    disabled={off || !!busy}/>

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <StepFeed
          run={run.data}
          loading={start.isPending || (!!runId && run.isPending)}
          approvalId={approvalId}
          approving={decide.isPending}
          onApprove={(id) => decide.mutate({id, decision: 'approve'})}
          onClear={clear}
        />
        <aside className="grid content-start gap-4">
          <RecordingScript/>
          <MaliciousPageCard url={connect.data?.malicious_page_url ?? '/demo/malicious-page'}/>
          <ConnectCard info={connect.data}/>
        </aside>
      </div>
    </div>
  );
}
