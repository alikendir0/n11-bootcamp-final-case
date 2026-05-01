import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { OrderTimeline } from './OrderTimeline';

describe('OrderTimeline', () => {
  it('renders the four step labels for an active (non-cancelled) timeline', () => {
    render(<OrderTimeline status="PAID" />);
    expect(screen.getByText('Sipariş Alındı')).toBeInTheDocument();
    expect(screen.getByText('Hazırlanıyor')).toBeInTheDocument();
    expect(screen.getByText('Kargoya Verildi')).toBeInTheDocument();
    expect(screen.getByText('Teslim Edildi')).toBeInTheDocument();
  });

  it('marks step 1 (index 0) as current for PENDING', () => {
    const { container } = render(<OrderTimeline status="PENDING" />);
    const current = container.querySelector('[aria-current="step"]');
    expect(current).not.toBeNull();
    expect(current?.textContent).toBe('1');
  });

  it('marks step 3 (index 2) as current for CONFIRMED', () => {
    const { container } = render(<OrderTimeline status="CONFIRMED" />);
    const current = container.querySelector('[aria-current="step"]');
    expect(current?.textContent).toBe('3');
  });

  it('renders İptal Edildi banner instead of timeline when CANCELLED', () => {
    render(<OrderTimeline status="CANCELLED" />);
    expect(screen.getByText('İptal Edildi')).toBeInTheDocument();
    // Step labels must NOT appear when cancelled
    expect(screen.queryByText('Sipariş Alındı')).toBeNull();
  });

  it('renders the cancelReason inside the banner when provided', () => {
    render(<OrderTimeline status="CANCELLED" cancelReason="Stok yetersiz" />);
    expect(screen.getByText('İptal Edildi')).toBeInTheDocument();
    expect(screen.getByText(/Stok yetersiz/)).toBeInTheDocument();
  });

  it('renders İptal Edildi banner for STOCK_FAILED', () => {
    render(<OrderTimeline status="STOCK_FAILED" />);
    expect(screen.getByText('İptal Edildi')).toBeInTheDocument();
    expect(screen.queryByText('Sipariş Alındı')).toBeNull();
  });

  it('renders İptal Edildi banner for PAYMENT_FAILED', () => {
    render(<OrderTimeline status="PAYMENT_FAILED" />);
    expect(screen.getByText('İptal Edildi')).toBeInTheDocument();
    expect(screen.queryByText('Sipariş Alındı')).toBeNull();
  });
});
