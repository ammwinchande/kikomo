/** Minor units (cents) to a display string such as "$480.00". Null means the call moved no money. */
export function money(minor: number | null | undefined, currency: string | null = 'USD'): string {
  if (minor === null || minor === undefined) return 'No money';
  const symbol = currency === null || currency === 'USD' ? '$' : `${currency} `;
  const sign = minor < 0 ? '-' : '';
  const abs = Math.abs(minor);
  const whole = Math.floor(abs / 100).toLocaleString('en-US');
  const cents = String(abs % 100).padStart(2, '0');
  return `${sign}${symbol}${whole}.${cents}`;
}

/** "12.00" style decimal string from minor units, for agent-facing values. */
export function minorToDecimal(minor: number): string {
  return (minor / 100).toFixed(2);
}

/** Parses "$480", "480.5" or "480.50" into minor units; NaN when unparsable. */
export function decimalToMinor(text: string): number {
  const cleaned = text.replace(/[^0-9.]/g, '');
  if (!cleaned) return Number.NaN;
  const n = Number(cleaned);
  return Number.isFinite(n) ? Math.round(n * 100) : Number.NaN;
}
