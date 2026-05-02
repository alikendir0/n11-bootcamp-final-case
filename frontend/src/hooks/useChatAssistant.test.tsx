import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useChatAssistant } from './useChatAssistant';
import { streamChat } from '../api/chatApi';
import { toast } from 'sonner';
import type { ChatStreamEvent } from '../lib/types';

vi.mock('../api/chatApi', () => ({
  streamChat: vi.fn(),
}));

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

const mockedStreamChat = vi.mocked(streamChat);
const mockedToastSuccess = vi.mocked(toast.success);

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe('useChatAssistant', () => {
  beforeEach(() => {
    localStorage.clear();
    mockedStreamChat.mockReset();
    mockedToastSuccess.mockClear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('creates a conversationId on first use and reuses it', () => {
    const { result: r1 } = renderHook(() => useChatAssistant(), { wrapper });
    const id1 = r1.current.conversationId;
    expect(id1).toBeTruthy();
    expect(localStorage.getItem('n11.chat.conversationId')).toBe(id1);

    const { result: r2 } = renderHook(() => useChatAssistant(), { wrapper });
    expect(r2.current.conversationId).toBe(id1);
  });

  it('appends user message and streams assistant events', async () => {
    mockedStreamChat.mockImplementation(async (_req, onEvent) => {
      onEvent({ type: 'delta', text: 'Mer', conversationId: 'c1' });
      onEvent({ type: 'delta', text: 'haba', conversationId: 'c1' });
      onEvent({ type: 'done', conversationId: 'c1', finalText: 'Merhaba' });
    });

    const { result } = renderHook(() => useChatAssistant(), { wrapper });

    await act(async () => {
      await result.current.sendMessage('Merhaba');
    });

    expect(result.current.messages).toHaveLength(2);
    expect(result.current.messages[0].role).toBe('user');
    expect(result.current.messages[0].text).toBe('Merhaba');
    expect(result.current.messages[1].role).toBe('assistant');
  });

  it('disables duplicate sends while streaming', async () => {
    let resolveStream: (() => void) | null = null;
    mockedStreamChat.mockImplementation(() => new Promise((resolve) => {
      resolveStream = resolve;
    }));

    const { result } = renderHook(() => useChatAssistant(), { wrapper });

    act(() => {
      result.current.sendMessage('Merhaba');
    });

    expect(result.current.isStreaming).toBe(true);

    // Try to send again while streaming
    await act(async () => {
      await result.current.sendMessage('Ikinci');
    });

    // Should still only have one user message
    expect(result.current.messages.filter(m => m.role === 'user')).toHaveLength(1);

    await act(async () => {
      resolveStream && resolveStream();
    });
  });

  it('shows error state on stream failure', async () => {
    mockedStreamChat.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useChatAssistant(), { wrapper });

    await act(async () => {
      await result.current.sendMessage('Merhaba');
    });

    const assistantMessages = result.current.messages.filter(m => m.role === 'assistant');
    expect(assistantMessages.length).toBeGreaterThan(0);
    const lastAssistant = assistantMessages[assistantMessages.length - 1];
    expect(lastAssistant.text).toContain('Yanıt alınamadı');
  });

  it('invalidates cart and shows toast on add_to_cart tool success', async () => {
    const invalidateQueries = vi.fn();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    queryClient.invalidateQueries = invalidateQueries;

    const customWrapper = ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );

    mockedStreamChat.mockImplementation(async (_req, onEvent) => {
      onEvent({ type: 'tool_call', name: 'add_to_cart', callId: 'tc1', argsJson: '{}' });
      onEvent({ type: 'tool_result', callId: 'tc1', toolName: 'add_to_cart', ok: true, summary: 'Eklendi', resultType: 'cart', data: { cart: { itemCount: 1 } } });
      onEvent({ type: 'done', conversationId: 'c1', finalText: 'Tamam' });
    });

    const { result } = renderHook(() => useChatAssistant(), { wrapper: customWrapper });

    await act(async () => {
      await result.current.sendMessage('Sepete ekle');
    });

    await waitFor(() => {
      expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['cart'] });
    });
    expect(mockedToastSuccess).toHaveBeenCalledWith('Ürün sepete eklendi.');
  });

  it('shows Sepet güncellendi toast on update/remove success', async () => {
    mockedStreamChat.mockImplementation(async (_req, onEvent) => {
      onEvent({ type: 'tool_call', name: 'update_cart_item', callId: 'tc2', argsJson: '{}' });
      onEvent({ type: 'tool_result', callId: 'tc2', toolName: 'update_cart_item', ok: true, summary: 'Güncellendi', resultType: 'cart', data: { cart: { itemCount: 2 } } });
      onEvent({ type: 'done', conversationId: 'c1', finalText: 'Tamam' });
    });

    const { result } = renderHook(() => useChatAssistant(), { wrapper });

    await act(async () => {
      await result.current.sendMessage('Güncelle');
    });

    expect(mockedToastSuccess).toHaveBeenCalledWith('Sepet güncellendi.');
  });

  it('exposes auth-required inline message for guest tool failures', async () => {
    mockedStreamChat.mockImplementation(async (_req, onEvent) => {
      onEvent({ type: 'tool_call', name: 'add_to_cart', callId: 'tc3', argsJson: '{}' });
      onEvent({ type: 'tool_result', callId: 'tc3', toolName: 'add_to_cart', ok: false, summary: 'AUTH_REQUIRED: Giriş gerekli', resultType: 'generic' });
      onEvent({ type: 'done', conversationId: 'c1', finalText: 'Giriş gerekli' });
    });

    const { result } = renderHook(() => useChatAssistant(), { wrapper });

    await act(async () => {
      await result.current.sendMessage('Ekle');
    });

    const assistantMsgs = result.current.messages.filter(m => m.role === 'assistant');
    const hasAuthMsg = assistantMsgs.some(m =>
      m.text?.includes('Sepete eklemek ve sipariş işlemleri için giriş yapmanız gerekiyor.')
    );
    expect(hasAuthMsg).toBe(true);
  });

  it('retryLastMessage re-sends the last user message', async () => {
    mockedStreamChat.mockImplementation(async (_req, onEvent) => {
      onEvent({ type: 'done', conversationId: 'c1', finalText: 'Tamam' });
    });

    const { result } = renderHook(() => useChatAssistant(), { wrapper });

    await act(async () => {
      await result.current.sendMessage('Soru');
    });

    expect(result.current.messages.filter(m => m.role === 'user')).toHaveLength(1);

    await act(async () => {
      await result.current.retryLastMessage();
    });

    expect(result.current.messages.filter(m => m.role === 'user')).toHaveLength(2);
    expect(mockedStreamChat).toHaveBeenCalledTimes(2);
  });

  it('clearLocalTranscript removes all messages', async () => {
    mockedStreamChat.mockImplementation(async (_req, onEvent) => {
      onEvent({ type: 'done', conversationId: 'c1', finalText: 'Tamam' });
    });

    const { result } = renderHook(() => useChatAssistant(), { wrapper });

    await act(async () => {
      await result.current.sendMessage('Soru');
    });

    expect(result.current.messages.length).toBeGreaterThan(0);

    act(() => {
      result.current.clearLocalTranscript();
    });

    expect(result.current.messages).toHaveLength(0);
  });
});
