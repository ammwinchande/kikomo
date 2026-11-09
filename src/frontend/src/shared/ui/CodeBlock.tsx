import { useState } from 'react';
import Button from './Button';

export default function CodeBlock({code, label, copy = true}: Readonly<{
  code: string;
  label?: string;
  copy?: boolean
}>) {
  const [copied, setCopied] = useState(false);
  const doCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard unavailable; the text is still selectable */
    }
  };
  return (
    <div className="rounded-lg border border-line bg-bg">
      {(label || copy) && (
        <div
          className="flex items-center justify-between border-b border-line px-3 py-1.5 text-xs font-semibold text-muted">
          <span>{label}</span>
          {copy && (
            <Button variant="link" size="sm" onClick={doCopy} aria-label={`Copy ${label ?? 'code'}`}>
              {copied ? 'Copied' : 'Copy'}
            </Button>
          )}
        </div>
      )}
      <pre
        className="overflow-x-auto whitespace-pre-wrap break-all px-3 py-2.5 font-mono text-xs leading-relaxed text-ink">{code}</pre>
    </div>
  );
}
