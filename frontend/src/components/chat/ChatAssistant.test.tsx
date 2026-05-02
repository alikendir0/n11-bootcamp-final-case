import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ChatAssistant } from './ChatAssistant';
import type { ChatTranscriptItem } from '../../lib/types';

const mockSendMessage = vi.fn();
const mockRetryLastMessage = vi.fn();

vi.mock('../../hooks/useChatAssistant', () => ({
  useChatAssistant: vi.fn(() => ({
    messages: [] as ChatTranscriptItem[],
    isStreaming: false,
    sendMessage: mockSendMessage,
    retryLastMessage: mockRetryLastMessage,
    clearLocalTranscript: vi.fn(),
    conversationId: 'test-conv',
  })),
}));

import { useChatAssistant } from '../../hooks/useChatAssistant';

const mockedUseChatAssistant = vi.mocked(useChatAssistant);

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe('ChatAssistant', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedUseChatAssistant.mockReturnValue({
      messages: [],
      isStreaming: false,
      sendMessage: mockSendMessage,
      retryLastMessage: mockRetryLastMessage,
      clearLocalTranscript: vi.fn(),
      conversationId: 'test-conv',
    });
  });

  it('renders floating trigger with correct aria-label', () => {
    render(<ChatAssistant />, { wrapper });
    const trigger = screen.getByRole('button', {
      name: /Yapay Zeka Alışveriş Asistanını Aç/i,
    });
    expect(trigger).toBeInTheDocument();
  });

  it('opens drawer when trigger is clicked', () => {
    render(<ChatAssistant />, { wrapper });
    const trigger = screen.getByRole('button', {
      name: /Yapay Zeka Alışveriş Asistanını Aç/i,
    });
    fireEvent.click(trigger);
    expect(
      screen.getByRole('dialog', { name: /Yapay Zeka Alışveriş Asistanı/i })
    ).toBeInTheDocument();
  });

  it('closes drawer when close button is clicked', () => {
    render(<ChatAssistant />, { wrapper });
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
    render(<ChatAssistant />, { wrapper });
    const trigger = screen.getByRole('button', {
      name: /Yapay Zeka Alışveriş Asistanını Aç/i,
    });
    fireEvent.click(trigger);
    const panel = screen.getByRole('dialog').querySelector('.w-screen');
    expect(panel).toBeTruthy();
    expect(panel!.className).toContain('md:w-[420px]');
  });

  it('renders empty state with Turkish heading and suggested prompts', () => {
    render(<ChatAssistant />, { wrapper });
    fireEvent.click(screen.getByRole('button', { name: /Yapay Zeka Alışveriş Asistanını Aç/i }));
    expect(screen.getByText(/Merhaba, size nasıl yardımcı olabilirim/)).toBeInTheDocument();
    expect(screen.getByText(/MacBook ara/)).toBeInTheDocument();
    expect(screen.getByText(/Sepetimi göster/)).toBeInTheDocument();
    expect(screen.getByText(/Sipariş durumumu kontrol et/)).toBeInTheDocument();
  });

  it('renders transcript with log role and aria-live polite', () => {
    render(<ChatAssistant />, { wrapper });
    fireEvent.click(screen.getByRole('button', { name: /Yapay Zeka Alışveriş Asistanını Aç/i }));
    const transcript = screen.getByRole('log');
    expect(transcript).toHaveAttribute('aria-live', 'polite');
    expect(transcript).toHaveAttribute('aria-relevant', 'additions text');
  });

  it('shows streaming indicator while isStreaming is true', () => {
    mockedUseChatAssistant.mockReturnValue({
      messages: [{ id: 'a1', role: 'assistant', text: '' }],
      isStreaming: true,
      sendMessage: mockSendMessage,
      retryLastMessage: mockRetryLastMessage,
      clearLocalTranscript: vi.fn(),
      conversationId: 'test-conv',
    });
    render(<ChatAssistant />, { wrapper });
    fireEvent.click(screen.getByRole('button', { name: /Yapay Zeka Alışveriş Asistanını Aç/i }));
    expect(screen.getByText(/Yanıt yazılıyor/)).toBeInTheDocument();
  });

  it('shows Gönderiliyor... button label while streaming', () => {
    mockedUseChatAssistant.mockReturnValue({
      messages: [],
      isStreaming: true,
      sendMessage: mockSendMessage,
      retryLastMessage: mockRetryLastMessage,
      clearLocalTranscript: vi.fn(),
      conversationId: 'test-conv',
    });
    render(<ChatAssistant />, { wrapper });
    fireEvent.click(screen.getByRole('button', { name: /Yapay Zeka Alışveriş Asistanını Aç/i }));
    expect(screen.getByText(/Gönderiliyor/)).toBeInTheDocument();
  });

  it('shows retry card with Tekrar dene button on error message', () => {
    mockedUseChatAssistant.mockReturnValue({
      messages: [
        { id: 'u1', role: 'user', text: 'Merhaba' },
        { id: 'a1', role: 'assistant', text: 'Yanıt alınamadı. Lütfen tekrar deneyiniz.' },
      ],
      isStreaming: false,
      sendMessage: mockSendMessage,
      retryLastMessage: mockRetryLastMessage,
      clearLocalTranscript: vi.fn(),
      conversationId: 'test-conv',
    });
    render(<ChatAssistant />, { wrapper });
    fireEvent.click(screen.getByRole('button', { name: /Yapay Zeka Alışveriş Asistanını Aç/i }));
    expect(screen.getByText(/Yanıt alınamadı. Lütfen tekrar deneyiniz./)).toBeInTheDocument();
    const retryBtn = screen.getByRole('button', { name: /Tekrar dene/i });
    expect(retryBtn).toBeInTheDocument();
    fireEvent.click(retryBtn);
    expect(mockRetryLastMessage).toHaveBeenCalledTimes(1);
  });

  it('calls sendMessage when composer is submitted', () => {
    render(<ChatAssistant />, { wrapper });
    fireEvent.click(screen.getByRole('button', { name: /Yapay Zeka Alışveriş Asistanını Aç/i }));
    const input = screen.getByPlaceholderText(/Asistana ürün, sepet veya sipariş hakkında yazın/);
    fireEvent.change(input, { target: { value: 'MacBook ara' } });
    const sendBtn = screen.getByRole('button', { name: /Mesaj Gönder/i });
    fireEvent.click(sendBtn);
    expect(mockSendMessage).toHaveBeenCalledWith('MacBook ara');
  });

  it('renders compact product cards from tool_result data', () => {
    mockedUseChatAssistant.mockReturnValue({
      messages: [
        {
          id: 'a1',
          role: 'assistant',
          text: 'İşte aradığınız ürünler:',
          events: [
            {
              type: 'tool_result',
              callId: 'tc1',
              ok: true,
              summary: 'Ürünler bulundu',
              resultType: 'products',
              data: {
                products: [
                  { id: 'p1', name: 'MacBook Air', priceGross: 49999, stockQty: 5, imageUrl: '', categoryLabel: 'Elektronik' },
                ],
              },
            } as unknown as ChatTranscriptItem['events'],
          ],
        },
      ],
      isStreaming: false,
      sendMessage: mockSendMessage,
      retryLastMessage: mockRetryLastMessage,
      clearLocalTranscript: vi.fn(),
      conversationId: 'test-conv',
    });
    render(<ChatAssistant />, { wrapper });
    fireEvent.click(screen.getByRole('button', { name: /Yapay Zeka Alışveriş Asistanını Aç/i }));
    expect(screen.getByText(/MacBook Air/)).toBeInTheDocument();
    expect(screen.getByText(/Ürünü Gör/)).toBeInTheDocument();
    expect(screen.getByText(/Sepete Ekle/)).toBeInTheDocument();
  });

  it('renders cart handoff card from tool_result', () => {
    mockedUseChatAssistant.mockReturnValue({
      messages: [
        {
          id: 'a1',
          role: 'assistant',
          text: 'Sepetiniz güncellendi.',
          events: [
            {
              type: 'tool_result',
              callId: 'tc2',
              ok: true,
              summary: 'Sepet güncellendi',
              resultType: 'cart',
              data: { cart: { itemCount: 3, totalAmount: 1299.9 } },
            } as unknown as ChatTranscriptItem['events'],
          ],
        },
      ],
      isStreaming: false,
      sendMessage: mockSendMessage,
      retryLastMessage: mockRetryLastMessage,
      clearLocalTranscript: vi.fn(),
      conversationId: 'test-conv',
    });
    render(<ChatAssistant />, { wrapper });
    fireEvent.click(screen.getByRole('button', { name: /Yapay Zeka Alışveriş Asistanını Aç/i }));
    expect(screen.getByText(/Sepete Git/)).toBeInTheDocument();
  });

  it('renders payment handoff card from tool_result', () => {
    mockedUseChatAssistant.mockReturnValue({
      messages: [
        {
          id: 'a1',
          role: 'assistant',
          text: 'Siparişiniz hazır.',
          events: [
            {
              type: 'tool_result',
              callId: 'tc3',
              ok: true,
              summary: 'Ödeme bağlantısı hazır',
              resultType: 'payment',
              data: { paymentPageUrl: 'https://iyzico.example.com/pay/123', order: { orderId: 'o1', status: 'PENDING' } },
            } as unknown as ChatTranscriptItem['events'],
          ],
        },
      ],
      isStreaming: false,
      sendMessage: mockSendMessage,
      retryLastMessage: mockRetryLastMessage,
      clearLocalTranscript: vi.fn(),
      conversationId: 'test-conv',
    });
    render(<ChatAssistant />, { wrapper });
    fireEvent.click(screen.getByRole('button', { name: /Yapay Zeka Alışveriş Asistanını Aç/i }));
    expect(screen.getByText(/Ödemeye Devam Et/)).toBeInTheDocument();
  });
});
