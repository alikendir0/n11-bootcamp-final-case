import { describe, it, expect } from 'vitest';
import { computeTaksit, TAKSIT_TIERS } from './taksit';

describe('computeTaksit', () => {
  it('returns six rows for the six locked tiers', () => {
    const rows = computeTaksit(1200);
    expect(rows).toHaveLength(6);
    expect(rows.map(r => r.installments)).toEqual([1, 2, 3, 6, 9, 12]);
  });
  it('1-installment monthly equals price', () => {
    expect(computeTaksit(1200)[0]?.monthly).toBe(1200);
  });
  it('uses Math.ceil to keep monthly >= principal/n (LOC-03)', () => {
    expect(computeTaksit(100)[2]?.monthly).toBe(34);   // 100/3 = 33.33 → 34
    expect(computeTaksit(1000)[5]?.monthly).toBe(84);  // 1000/12 = 83.33 → 84
  });
  it('handles zero price', () => {
    expect(computeTaksit(0).every(r => r.monthly === 0)).toBe(true);
  });
});

describe('TAKSIT_TIERS', () => {
  it('locks the six visible tiers per UI-SPEC', () => {
    expect([...TAKSIT_TIERS]).toEqual([1, 2, 3, 6, 9, 12]);
  });
});
