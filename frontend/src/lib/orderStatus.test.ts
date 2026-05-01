import { describe, it, expect } from 'vitest';
import {
  computeTimelineStep,
  getStatusBadge,
  isCancellable,
  TIMELINE_STEPS,
} from './orderStatus';
import type { OrderStatus } from './types';

describe('TIMELINE_STEPS', () => {
  it('contains the four UI-SPEC step labels in order', () => {
    expect(TIMELINE_STEPS.map(s => s.label)).toEqual([
      'Sipariş Alındı',
      'Hazırlanıyor',
      'Kargoya Verildi',
      'Teslim Edildi',
    ]);
  });
});

describe('computeTimelineStep', () => {
  it.each<[OrderStatus, number, boolean]>([
    ['PENDING', 0, false],
    ['STOCK_RESERVED', 0, false],
    ['PAID', 1, false],
    ['CONFIRMED', 2, false],
    ['STOCK_FAILED', -1, true],
    ['PAYMENT_FAILED', -1, true],
    ['CANCELLED', -1, true],
  ])('maps %s → step %i / cancelled=%s', (status, expectedStep, expectedCancelled) => {
    const result = computeTimelineStep(status);
    expect(result.activeStepIndex).toBe(expectedStep);
    expect(result.isCancelled).toBe(expectedCancelled);
  });
});

describe('getStatusBadge', () => {
  it.each<[OrderStatus, string]>([
    ['PENDING', 'Onay Bekliyor'],
    ['STOCK_RESERVED', 'Onay Bekliyor'],
    ['PAID', 'Hazırlanıyor'],
    ['CONFIRMED', 'Onaylandı'],
    ['STOCK_FAILED', 'Stok Yetersiz'],
    ['PAYMENT_FAILED', 'Ödeme Başarısız'],
    ['CANCELLED', 'İptal Edildi'],
  ])('returns Turkish label for %s', (status, expectedLabel) => {
    expect(getStatusBadge(status).label).toBe(expectedLabel);
  });

  it('uses red classes for failure states', () => {
    expect(getStatusBadge('STOCK_FAILED').classes).toContain('#DC2626');
    expect(getStatusBadge('PAYMENT_FAILED').classes).toContain('#DC2626');
    expect(getStatusBadge('CANCELLED').classes).toContain('#DC2626');
  });
});

describe('isCancellable', () => {
  it('is true only for PENDING', () => {
    expect(isCancellable('PENDING')).toBe(true);
  });

  it.each<OrderStatus>([
    'STOCK_RESERVED',
    'PAID',
    'CONFIRMED',
    'STOCK_FAILED',
    'PAYMENT_FAILED',
    'CANCELLED',
  ])('is false for %s', (status) => {
    expect(isCancellable(status)).toBe(false);
  });
});
