import Button from '@/shared/ui/Button';
import Card, { CardHeader } from '@/shared/ui/Card';
import Pill from '@/shared/ui/Pill';

interface Props {
  sentence: string;
  onChange: (s: string) => void;
  onCompile: () => void;
  compiling: boolean;
  disabled: boolean;
}

export default function ComposeCard({sentence, onChange, onCompile, compiling, disabled}: Readonly<Props>) {
  return (
    <Card>
      <CardHeader title="Describe the policy you want" action={<Pill tone="brand">AI drafts. Rules decide.</Pill>}/>
      <label className="grid gap-1.5 text-sm font-medium">
        In plain words <textarea
        value={sentence}
        onChange={(e) => onChange(e.target.value)}
        rows={3}
        className="w-full rounded-lg border border-line bg-white px-3 py-2.5 text-sm text-ink"
        placeholder="Allow dev tools up to $500, approve over $25"
      />
      </label>
      <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
        <span className="text-xs text-muted">Limited to 300 AI calls a day, shared with explanations.</span>
        <Button onClick={onCompile} loading={compiling} disabled={disabled || !sentence.trim()}>
          Compile draft
        </Button>
      </div>
    </Card>
  );
}
