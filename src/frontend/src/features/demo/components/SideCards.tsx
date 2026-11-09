import type { DemoConnectInfo } from '@/shared/api/types';
import Card, { CardHeader } from '@/shared/ui/Card';
import CodeBlock from '@/shared/ui/CodeBlock';

export function RecordingScript() {
  const steps = [
    'Small payment. It goes through. Show it in Activity.',
    'Large payment. Switch to Approvals, press Approve.',
    'Injection. Read the Why panel on the denied call.',
    'Kill switch. Suspend, run small again, see it refused.',
    'Audit. Simulate tampering, then Verify chain.',
  ];
  return (
    <Card>
      <CardHeader title="A 3-minute recording"/>
      <ol className="grid gap-2 text-sm">
        {steps.map((s, i) => (
          <li key={s} className="flex gap-3">
            <span
              className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-chip text-xs font-bold text-brand-deep">{i + 1}</span>
            <span>{s}</span>
          </li>
        ))}
      </ol>
    </Card>
  );
}

export function MaliciousPageCard({url}: Readonly<{ url: string }>) {
  return (
    <Card>
      <CardHeader title="The malicious page"/>
      <p className="text-sm text-muted">
        Looks like a product listing. Hidden in it is an order to pay a stranger. You can open it to show the audience
        what the agent read.
      </p>
      <a href={url} target="_blank" rel="noreferrer"
         className="mt-3 inline-flex items-center gap-2 rounded-lg border border-line bg-white px-3.5 py-2 text-sm font-medium text-ink hover:bg-hover">
        Open /demo/malicious-page ↗
      </a>
    </Card>
  );
}

export function ConnectCard({info}: Readonly<{ info: DemoConnectInfo | undefined }>) {
  const url = info?.mcp_url ?? `${window.location.origin}/mcp`;
  const http = `URL     ${url}\nHeader  Authorization: Bearer YOUR_DEMO_AGENT_TOKEN\nHeader  Accept: application/json, text/event-stream`;
  const curl = `curl -s ${url} \\\n  -H "Authorization: Bearer $DEMO_AGENT_TOKEN" \\\n  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \\\n  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'`;
  return (
    <Card>
      <CardHeader title="Connect your own agent" subtitle="Streamable HTTP, stateless JSON-RPC."/>
      <div className="grid gap-3">
        <CodeBlock label="Over HTTP" code={http}/>
        <CodeBlock label="List the tools with curl" code={curl}/>
      </div>
      <p className="mt-3 text-xs text-muted">Use the sandbox token only. It works for this demo and nothing else.</p>
    </Card>
  );
}
