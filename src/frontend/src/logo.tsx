/** Kikomo mark: a shield with the brand gradient. */
export default function Logo({size = 36}: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 36 36" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id="kikomo-logo" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#2563EB"/>
          <stop offset="1" stopColor="#14B8A6"/>
        </linearGradient>
      </defs>
      <path d="M18 2 L32 8 V18 C32 26 26 32 18 34 C10 32 4 26 4 18 V8 Z" fill="url(#kikomo-logo)"/>
      <path d="M12 18.5 L16.5 23 L25 13.5" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round"
            strokeLinejoin="round"/>
    </svg>
  );
}
