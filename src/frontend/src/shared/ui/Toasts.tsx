import { useUiStore } from '../store/ui-store';

export default function Toasts() {
  const toasts = useUiStore((s) => s.toasts);
  const dismiss = useUiStore((s) => s.dismiss);
  if (toasts.length === 0) return null;
  return (
    <div className="pointer-events-none fixed bottom-5 right-5 z-50 flex flex-col gap-2" aria-live="polite">
      {toasts.map((t) => (
        <output
          key={t.id}
          role={t.kind === 'error' ? 'alert' : 'status'}
          className={`pointer-events-auto flex items-center gap-3 rounded-lg border px-4 py-3 text-sm shadow-card ${
            t.kind === 'error' ? 'border-deny-fg/30 bg-deny-bg text-deny-fg' : t.kind === 'success' ? 'border-allow-fg/30 bg-allow-bg text-allow-fg' : 'border-line bg-white text-ink'
          }`}
        >
          <span>{t.text}</span>
          <button type="button" onClick={() => dismiss(t.id)}
                  className="text-xs font-semibold opacity-70 hover:opacity-100" aria-label="Dismiss">
            ✕
          </button>
        </output>
      ))}
    </div>
  );
}
