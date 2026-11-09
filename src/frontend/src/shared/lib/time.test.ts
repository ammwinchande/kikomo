import { clockTime, countdown, humanDuration, isPast, relativeTime } from './time';

const now = new Date('2026-11-09T09:45:00Z');
const ago = (s: number) => new Date(now.getTime() - s * 1000).toISOString();

describe('time helpers', () => {
  it('describes relative time', () => {
    expect(relativeTime(ago(5), now)).toBe('just now');
    expect(relativeTime(ago(120), now)).toBe('2 min ago');
    expect(relativeTime(ago(3 * 3600), now)).toBe('3 h ago');
    expect(relativeTime(ago(24 * 3600), now)).toBe('yesterday');
    expect(relativeTime(null, now)).toBe('');
  });
  it('counts down to a deadline', () => {
    expect(countdown(ago(-761), now)).toBe('12:41');
    expect(countdown(ago(-5), now)).toBe('0:05');
    expect(countdown(ago(-3725), now)).toBe('1:02:05');
    expect(countdown(ago(10), now)).toBe('0:00');
    expect(isPast(ago(10), now)).toBe(true);
    expect(isPast(ago(-10), now)).toBe(false);
  });
  it('formats clock time and durations', () => {
    expect(clockTime(now.toISOString())).toMatch(/^\d{2}:\d{2}:\d{2}$/);
    expect(humanDuration(900)).toBe('15 min');
    expect(humanDuration(7200)).toBe('2 h');
    expect(humanDuration(90)).toBe('90 s');
  });
});
