import type { DemoScenario } from '@/shared/api/types';
import Button from '@/shared/ui/Button';
import Card from '@/shared/ui/Card';
import Pill, { type Tone } from '@/shared/ui/Pill';

interface Scenario {
  id: DemoScenario;
  expect: string;
  tone: Tone;
  title: string;
  text: string;
  cta: string;
}

export const SCENARIOS: Scenario[] = [
  {
    id: 'small',
    expect: 'Expect: allowed',
    tone: 'allow',
    title: 'Small payment',
    text: '$12.00 to a trusted payee. Goes straight through.',
    cta: 'Run small'
  },
  {
    id: 'large',
    expect: 'Expect: needs approval',
    tone: 'approve',
    title: 'Large payment',
    text: '$480.00 to a trusted payee. Pauses until a human decides.',
    cta: 'Run large'
  },
  {
    id: 'injection',
    expect: 'Expect: denied',
    tone: 'deny',
    title: 'Injection attack',
    text: 'A web page tells the agent to pay a stranger $90.00. Kikomo says no.',
    cta: 'Run injection'
  },
  {
    id: 'all',
    expect: 'All three',
    tone: 'brand',
    title: 'Run everything',
    text: 'Allowed, needs approval and denied, one after another. Best for a first look.',
    cta: 'Run all'
  },
];

export default function ScenarioGrid({onRun, running, disabled}: Readonly<{
  onRun: (s: DemoScenario) => void;
  running: DemoScenario | null;
  disabled: boolean
}>) {
  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4" aria-label="Pick a scenario">
      {SCENARIOS.map((s) => (
        <Card key={s.id} className="flex flex-col">
          <Pill tone={s.tone} className="self-start">
            {s.expect}
          </Pill>
          <h3 className="mt-3 text-base font-semibold">{s.title}</h3>
          <p className="mt-1 flex-1 text-sm text-muted">{s.text}</p>
          <Button className="mt-4" variant={s.id === 'all' ? 'primary' : 'ghost'} onClick={() => onRun(s.id)}
                  loading={running === s.id} disabled={disabled}>
            {s.cta} →
          </Button>
        </Card>
      ))}
    </div>
  );
}
