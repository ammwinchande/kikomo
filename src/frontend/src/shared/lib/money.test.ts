import { decimalToMinor, minorToDecimal, money } from './money';

describe('money', () => {
  it('formats minor units as dollars', () => {
    expect(money(48000)).toBe('$480.00');
    expect(money(5)).toBe('$0.05');
    expect(money(123456789)).toBe('$1,234,567.89');
    expect(money(-250)).toBe('-$2.50');
  });
  it('labels calls that move no money', () => {
    expect(money(null)).toBe('No money');
    expect(money(undefined)).toBe('No money');
  });
  it('shows a non-USD currency code', () => {
    expect(money(1000, 'EUR')).toBe('EUR 10.00');
  });
  it('converts both ways', () => {
    expect(minorToDecimal(1200)).toBe('12.00');
    expect(decimalToMinor('$480')).toBe(48000);
    expect(decimalToMinor('12.5')).toBe(1250);
    expect(decimalToMinor('abc')).toBeNaN();
  });
});
