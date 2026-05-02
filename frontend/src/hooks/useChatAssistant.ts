import { useCallback, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { streamChat } from '../api/chatApi';
import { isCartMutationTool } from '../lib/chatEvents';
import { cartQueryKey } from './useCart';
import { ROUTES } from '../lib/routes';
import type { ChatStreamEvent, ChatTranscriptItem } from '../lib/types';

const CONVERSATION_ID_KEY = 'n11.chat.conversationId';

function getOrCreateConversationId(): string {
  try {
    const existing = localStorage.getItem(CONVERSATION_ID_KEY);
    if (existing) return existing;
    const fresh = crypto.randomUUID();
    localStorage.setItem(CONVERSATION_ID_KEY, fresh);
    return fresh;
  } catch {
    return crypto.randomUUID();
  }
}

function nowIso(): string {
  return new Date().toISOString();
}

export function useChatAssistant() {
  const queryClient = useQueryClient();
  const [conversationId] = useState<string>(() => getOrCreateConversationId());
  const [messages, setMessages] = useState<ChatTranscriptItem[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const pendingCallIds = useRef<Map<string, string>>(new Map());
  const lastUserMessage = useRef<string>('');

  const handleEvent = useCallback(
    (event: ChatStreamEvent, assistantId: string): ChatTranscriptItem | null => {
      setMessages((prev) => {
        const idx = prev.findIndex((m) => m.id === assistantId);
        if (idx === -1) return prev;
        const assistant = prev[idx];
        const events = [...(assistant.events ?? []), event];

        if (event.type === 'tool_call') {
          pendingCallIds.current.set(event.callId, event.name);
        }

        if (event.type === 'tool_result') {
          const toolName = event.toolName ?? pendingCallIds.current.get(event.callId) ?? '';
          if (event.ok && isCartMutationTool(toolName)) {
            queryClient.invalidateQueries({ queryKey: cartQueryKey });
            if (toolName === 'add_to_cart') {
              toast.success('Ürün sepete eklendi.');
            } else {
              toast.success('Sepet güncellendi.');
            }
          }
          if (!event.ok && event.summary?.includes('AUTH_REQUIRED')) {
            const redirectUrl = encodeURIComponent(
              window.location.pathname + window.location.search
            );
            const authItem: ChatTranscriptItem = {
              id: `auth-${nowIso()}`,
              role: 'assistant',
              text: `Sepete eklemek ve sipariş işlemleri için giriş yapmanız gerekiyor.`,
            };
            // Insert auth item after current assistant
            const next = [...prev];
            next.splice(idx + 1, 0, authItem);
            return next;
          }
        }

        if (event.type === 'delta') {
          const updated: ChatTranscriptItem = {
            ...assistant,
            events,
            text: (assistant.text ?? '') + event.text,
          };
          return [...prev.slice(0, idx), updated, ...prev.slice(idx + 1)];
        }

        const updated: ChatTranscriptItem = { ...assistant, events };
        return [...prev.slice(0, idx), updated, ...prev.slice(idx + 1)];
      });
      return null;
    },
    [queryClient]
  );

  const sendMessage = useCallback(
    async (message: string) => {
      if (isStreaming || !message.trim()) return;
      lastUserMessage.current = message.trim();

      const userItem: ChatTranscriptItem = {
        id: `u-${nowIso()}`,
        role: 'user',
        text: message.trim(),
      };
      const assistantItem: ChatTranscriptItem = {
        id: `a-${nowIso()}`,
        role: 'assistant',
        text: '',
        events: [],
      };

      setMessages((prev) => [...prev, userItem, assistantItem]);
      setIsStreaming(true);

      try {
        await streamChat(
          { conversationId, message: message.trim() },
          (event) => handleEvent(event, assistantItem.id)
        );
      } catch {
        setMessages((prev) => {
          const idx = prev.findIndex((m) => m.id === assistantItem.id);
          if (idx === -1) return prev;
          const updated: ChatTranscriptItem = {
            ...prev[idx],
            text: 'Yanıt alınamadı. Lütfen tekrar deneyiniz.',
          };
          return [...prev.slice(0, idx), updated, ...prev.slice(idx + 1)];
        });
      } finally {
        setIsStreaming(false);
      }
    },
    [conversationId, isStreaming, handleEvent]
  );

  const retryLastMessage = useCallback(async () => {
    const msg = lastUserMessage.current;
    if (!msg) return;
    await sendMessage(msg);
  }, [sendMessage]);

  const clearLocalTranscript = useCallback(() => {
    setMessages([]);
    lastUserMessage.current = '';
  }, []);

  return {
    conversationId,
    messages,
    isStreaming,
    sendMessage,
    retryLastMessage,
    clearLocalTranscript,
  };
}
