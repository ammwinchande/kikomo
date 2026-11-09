export default function Spinner({label = 'Loading'}: Readonly<{ label?: string }>) {
  return (
    <output className="flex items-center gap-2 py-6 text-sm text-muted">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-line border-t-brand" aria-hidden="true"/>
      {label}
    </output>
  );
}
