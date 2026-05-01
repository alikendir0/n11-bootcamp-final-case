import { describe, it, expect } from 'vitest';
import { uiToBackend, backendSortToUi, pageWindow, PAGE_SIZE } from './listingParams';

describe('uiToBackend', () => {
  it('uses defaults for empty input (D-11 + D-12)', () => {
    expect(uiToBackend({})).toEqual({ page: 0, size: 20, sort: 'created_at,desc' });
  });
  it('translates 1-indexed UI page to 0-indexed backend (D-10 critical)', () => {
    expect(uiToBackend({ sayfa: 1 }).page).toBe(0);
    expect(uiToBackend({ sayfa: 2 }).page).toBe(1);
    expect(uiToBackend({ sayfa: 47 }).page).toBe(46);
  });
  it('clamps zero or negative page to 0', () => {
    expect(uiToBackend({ sayfa: 0 }).page).toBe(0);
    expect(uiToBackend({ sayfa: -5 }).page).toBe(0);
  });
  it('maps fiyat-artan to price_gross,asc', () => {
    expect(uiToBackend({ siralama: 'fiyat-artan' }).sort).toBe('price_gross,asc');
  });
  it('maps fiyat-azalan to price_gross,desc', () => {
    expect(uiToBackend({ siralama: 'fiyat-azalan' }).sort).toBe('price_gross,desc');
  });
  it('falls back to default sort on unknown key', () => {
    // @ts-expect-error testing runtime resilience
    expect(uiToBackend({ siralama: 'invalid' }).sort).toBe('created_at,desc');
  });
  it('passes through kategori as categoryFilter', () => {
    expect(uiToBackend({ kategori: 'elektronik' }).categoryFilter).toBe('elektronik');
  });
  it('passes through q', () => {
    expect(uiToBackend({ q: 'macbook' }).q).toBe('macbook');
  });
  it('PAGE_SIZE is fixed at 20 per D-12', () => {
    expect(PAGE_SIZE).toBe(20);
  });
});

describe('backendSortToUi', () => {
  it('round-trips all three sort keys', () => {
    expect(backendSortToUi('created_at,desc')).toBe('tarih-yeni');
    expect(backendSortToUi('price_gross,asc')).toBe('fiyat-artan');
    expect(backendSortToUi('price_gross,desc')).toBe('fiyat-azalan');
  });
});

describe('pageWindow', () => {
  it('returns [1] for single-page result', () => {
    expect(pageWindow(1, 1)).toEqual([1]);
  });
  it('returns [1,2,3] for three pages', () => {
    expect(pageWindow(2, 3)).toEqual([1, 2, 3]);
  });
  it('inserts ellipsis around the radius window for large counts', () => {
    expect(pageWindow(5, 47, 2)).toEqual([1, 'ellipsis', 3, 4, 5, 6, 7, 'ellipsis', 47]);
  });
  it('does not duplicate page 1 when current is near the start', () => {
    expect(pageWindow(2, 47, 2)).toEqual([1, 2, 3, 4, 'ellipsis', 47]);
  });
  it('does not duplicate last page when current is near the end', () => {
    expect(pageWindow(46, 47, 2)).toEqual([1, 'ellipsis', 44, 45, 46, 47]);
  });
});
