import { getToken, clearToken } from '../lib/tokenStore';
import { ApiError } from '../lib/apiClient';
import { parseSseFrames } from '../lib/chatEvents';
import type { ChatStreamEvent } from '../lib/types';

const API_BASE = import.meta.env.VITE_API_BASE_URL;
const API_PREFIX = '/api/v1';

export interface ChatStreamRequest {
  conversationId: string;
  message: string;
}

export interface ChatStreamOptions {
  signal?: AbortSignal;
}

export async function streamChat(
  req: ChatStreamRequest,
  onEvent: (event: ChatStreamEvent) => void,
  options?: ChatStreamOptions
): Promise<void> {
  const token = getToken();
  const headers = new Headers();
  headers.set('Content-Type', 'application/json');
  headers.set('Accept', 'text/event-stream');
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const url = `${API_BASE}${API_PREFIX}/chat/stream`;
  const res = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify({ conversationId: req.conversationId, message: req.message }),
    signal: options?.signal,
  });

  if (res.status === 401) {
    clearToken();
    const redirectUrl = window.location.pathname + window.location.search;
    window.dispatchEvent(
      new CustomEvent('auth:unauthorized', { detail: { redirectUrl } })
    );
    throw new ApiError(401, { title: 'Unauthorized', detail: 'Oturum süresi doldu.' });
  }

  if (!res.ok) {
    throw new ApiError(res.status, { title: 'STREAM_ERROR', detail: 'Sohbet akışı başlatılamadı.' });
  }

  if (!res.body) {
    throw new ApiError(502, { title: 'STREAM_UNAVAILABLE', detail: 'SSE akışı başlatılamadı.' });
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let remainder = '';

  try {
    while (true) {
      if (options?.signal?.aborted) {
        throw new DOMException('Aborted', 'AbortError');
      }
      const { done, value } = await reader.read();
      if (done) break;
      const chunk = decoder.decode(value, { stream: true });
      const { events, remainder: newRemainder } = parseSseFrames(chunk, remainder);
      remainder = newRemainder;
      for (const event of events) {
        onEvent(event);
      }
    }
    // Process any trailing remainder that might form a complete frame
    if (remainder.trim().length > 0) {
      const { events, remainder: finalRemainder } = parseSseFrames('\n\n', remainder);
      for (const event of events) {
        onEvent(event);
      }
      remainder = finalRemainder;
    }
  } finally {
    reader.cancel().catch(() => {});
  }
}
