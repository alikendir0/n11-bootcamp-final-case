import { describe, it, expect } from 'vitest';
import { formatTRY, formatTRDate } from './format';

describe('formatTRY', () => {
  it('formats 1299.9 as Turkish currency with ₺ symbol', () => {
    const result = formatTRY(1299.9);
    // Normalize: strip all whitespace and non-breaking spaces; check number portion and symbol
    const normalized = result.replace(/[\s  ]/g, '');
    // Must contain the number in Turkish format (comma decimal, dot thousands)
    expect(normalized).toMatch(/1\.299,90/);
    // Must contain the lira symbol
    expect(normalized).toContain('₺');
  });
  it('formats 0 as 0,00 with ₺ symbol', () => {
    const normalized = formatTRY(0).replace(/[\s  ]/g, '');
    expect(normalized).toMatch(/0,00/);
    expect(normalized).toContain('₺');
  });
  it('formats large prices with thousand-separator dots', () => {
    const normalized = formatTRY(1234567.89).replace(/[\s  ]/g, '');
    expect(normalized).toMatch(/1\.234\.567,89/);
    expect(normalized).toContain('₺');
  });
  it('formats fractional 12.5 with two decimals', () => {
    const normalized = formatTRY(12.5).replace(/[\s  ]/g, '');
    expect(normalized).toMatch(/12,50/);
    expect(normalized).toContain('₺');
  });
});

describe('formatTRDate', () => {
  it('formats Date object as 28 Nisan 2026', () => {
    expect(formatTRDate(new Date(2026, 3, 28))).toBe('28 Nisan 2026');
  });
  it('formats ISO date string', () => {
    expect(formatTRDate('2026-04-28T00:00:00Z')).toContain('Nisan 2026');
  });
  it('uses long month name in Turkish', () => {
    const result = formatTRDate(new Date(2026, 0, 15));  // 15 Ocak 2026
    expect(result).toBe('15 Ocak 2026');
  });
});
