import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ChatAssistant } from './ChatAssistant';

describe('ChatAssistant', () => {
  it('renders floating trigger with correct aria-label', () => {
    render(<ChatAssistant />);
    const trigger = screen.getByRole('button', {
      name: /Yapay Zeka Alışveriş Asistanını Aç/i,
    });
    expect(trigger).toBeInTheDocument();
  });

  it('opens drawer when trigger is clicked', () => {
    render(<ChatAssistant />);
    const trigger = screen.getByRole('button', {
      name: /Yapay Zeka Alışveriş Asistanını Aç/i,
    });
    fireEvent.click(trigger);
    expect(
      screen.getByRole('dialog', { name: /Yapay Zeka Alışveriş Asistanı/i })
    ).toBeInTheDocument();
  });

  it('closes drawer when close button is clicked', () => {
    render(<ChatAssistant />);
    const trigger = screen.getByRole('button', {
      name: /Yapay Zeka Alışveriş Asistanını Aç/i,
    });
    fireEvent.click(trigger);
    const closeBtn = screen.getByRole('button', { name: /Asistanı Kapat/i });
    fireEvent.click(closeBtn);
    expect(
      screen.queryByRole('dialog', { name: /Yapay Zeka Alışveriş Asistanı/i })
    ).not.toBeInTheDocument();
  });

  it('renders drawer with 420px width class on desktop', () => {
    render(<ChatAssistant />);
    const trigger = screen.getByRole('button', {
      name: /Yapay Zeka Alışveriş Asistanını Aç/i,
    });
    fireEvent.click(trigger);
    const panel = screen.getByRole('dialog').querySelector('.w-screen');
    expect(panel).toBeTruthy();
    expect(panel!.className).toContain('md:w-[420px]');
  });
});
