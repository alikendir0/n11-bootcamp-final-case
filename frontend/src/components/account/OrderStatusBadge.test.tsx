import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { OrderStatusBadge } from './OrderStatusBadge';

describe('OrderStatusBadge', () => {
  it('renders Onay Bekliyor for PENDING', () => {
    render(<OrderStatusBadge status="PENDING" />);
    expect(screen.getByText('Onay Bekliyor')).toBeInTheDocument();
  });

  it('renders Onaylandı for CONFIRMED', () => {
    render(<OrderStatusBadge status="CONFIRMED" />);
    expect(screen.getByText('Onaylandı')).toBeInTheDocument();
  });

  it('renders İptal Edildi for CANCELLED', () => {
    render(<OrderStatusBadge status="CANCELLED" />);
    expect(screen.getByText('İptal Edildi')).toBeInTheDocument();
  });

  it('renders Hazırlanıyor for PAID', () => {
    render(<OrderStatusBadge status="PAID" />);
    expect(screen.getByText('Hazırlanıyor')).toBeInTheDocument();
  });

  it('renders Stok Yetersiz for STOCK_FAILED', () => {
    render(<OrderStatusBadge status="STOCK_FAILED" />);
    expect(screen.getByText('Stok Yetersiz')).toBeInTheDocument();
  });

  it('renders Ödeme Başarısız for PAYMENT_FAILED', () => {
    render(<OrderStatusBadge status="PAYMENT_FAILED" />);
    expect(screen.getByText('Ödeme Başarısız')).toBeInTheDocument();
  });

  it('renders Onay Bekliyor for STOCK_RESERVED', () => {
    render(<OrderStatusBadge status="STOCK_RESERVED" />);
    expect(screen.getByText('Onay Bekliyor')).toBeInTheDocument();
  });
});
