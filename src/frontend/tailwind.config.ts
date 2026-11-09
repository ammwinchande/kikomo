import type { Config } from 'tailwindcss';

/** Design tokens from the Kikomo Admin Portal boards. */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: { sans: ['Inter', 'system-ui', 'sans-serif'] },
      colors: {
        bg: '#F4F8FB',
        ink: '#0B1F33',
        muted: '#475A6E',
        faint: '#6B7C8D',
        line: '#DCE6EE',
        soft: '#E6EEF4',
        hover: '#EEF4F9',
        brand: { DEFAULT: '#2563EB', hover: '#1D4FD7', deep: '#1D4ED8' },
        teal: '#14B8A6',
        chip: '#E8F0FE',
        allow: { bg: '#E6F7F4', fg: '#0F766E' },
        approve: { bg: '#FFF3D6', fg: '#7A4B00' },
        deny: { bg: '#FDECEC', fg: '#B4232A', hover: '#A9262C' },
        info: { bg: '#EEF4F9', fg: '#475A6E' },
        selected: '#EAF2FE',
      },
      boxShadow: { card: '0 1px 2px rgba(11,31,51,0.06), 0 1px 1px rgba(11,31,51,0.04)' },
      maxWidth: { shell: '1200px' },
    },
  },
  plugins: [],
} satisfies Config;
