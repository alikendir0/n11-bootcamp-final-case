import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { streamChat, type ChatStreamRequest } from './chatApi';
import { ApiError } from '../lib/apiClient';
import { getToken, clearToken } from '../lib/tokenStore';
import type { ChatStreamEvent } from '../lib/types';

vi.mock('../lib/tokenStore', () => ({
  getToken: vi.fn(),
  clearToken: vi.fn(),
}));

const mockedGetToken = vi.mocked(getToken);
const mockedClearToken = vi.mocked(clearToken);

describe('streamChat', () => {
  let originalFetch: typeof fetch;

  beforeEach(() => {
    originalFetch = globalThis.fetch;
    mockedGetToken.mockReturnValue(null);
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  function mockStreamResponse(chunks: Uint8Array[]) {
    let index = 0;
    const mockReader = {
      read: vi.fn().mockImplementation(() => {
        if (index < chunks.length) {
          return Promise.resolve({ done: false, value: chunks[index++] });
        }
        return Promise.resolve({ done: true });
      }),
      cancel: vi.fn().mockResolvedValue(undefined),
    };
    const mockResponse = {
      ok: true,
      status: 200,
      body: { getReader: () => mockReader },
      headers: new Headers(),
    };
    globalThis.fetch = vi.fn().mockResolvedValue(mockResponse as unknown as Response);
    return mockReader;
  }

  it('sends POST with Accept text/event-stream and JSON body', async () => {
    mockStreamResponse([
      new TextEncoder().encode('event: done\ndata: {"conversationId":"c1","finalText":"Tamam"}\n\n'),
    ]);

    const events: ChatStreamEvent[] = [];
    const req: ChatStreamRequest = { conversationId: 'c1', message: 'Merhaba' };
    await streamChat(req, (e) => events.push(e));

    const fetchCall = vi.mocked(globalThis.fetch).mock.calls[0];
    const [url, init] = fetchCall;
    expect(url).toContain('/api/v1/chat/stream');
    expect((init as RequestInit).method).toBe('POST');
    const headers = new Headers((init as RequestInit).headers);
    expect(headers.get('Accept')).toBe('text/event-stream');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({ conversationId: 'c1', message: 'Merhaba' });
  });

  it('includes Authorization header when token exists', async () => {
    mockedGetToken.mockReturnValue('test-token');
    mockStreamResponse([
      new TextEncoder().encode('event: done\ndata: {}\n\n'),
    ]);

    await streamChat({ conversationId: 'c1', message: 'x' }, () => {});

    const fetchCall = vi.mocked(globalThis.fetch).mock.calls[0];
    const init = fetchCall[1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get('Authorization')).toBe('Bearer test-token');
  });

  it('dispatches auth:unauthorized on 401 and clears token', async () => {
    const eventSpy = vi.fn();
    window.addEventListener('auth:unauthorized', eventSpy);

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      body: null,
      headers: new Headers(),
    } as unknown as Response);

    await expect(
      streamChat({ conversationId: 'c1', message: 'x' }, () => {})
    ).rejects.toBeInstanceOf(ApiError);

    expect(mockedClearToken).toHaveBeenCalled();
    expect(eventSpy).toHaveBeenCalled();

    window.removeEventListener('auth:unauthorized', eventSpy);
  });

  it('throws ApiError when response.body is null', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: null,
      headers: new Headers(),
    } as unknown as Response);

    await expect(
      streamChat({ conversationId: 'c1', message: 'x' }, () => {})
    ).rejects.toBeInstanceOf(ApiError);
  });

  it('streams multiple events from chunked response', async () => {
    mockStreamResponse([
      new TextEncoder().encode('event: delta\ndata: {"text":"Mer","conversationId":"c1"}\n\n'),
      new TextEncoder().encode('event: delta\ndata: {"text":"haba","conversationId":"c1"}\n\nevent: done\ndata: {"conversationId":"c1","finalText":"Merhaba"}\n\n'),
    ]);

    const events: ChatStreamEvent[] = [];
    await streamChat({ conversationId: 'c1', message: 'Merhaba' }, (e) => events.push(e));

    expect(events).toHaveLength(3);
    expect(events[0]).toMatchObject({ type: 'delta', text: 'Mer' });
    expect(events[1]).toMatchObject({ type: 'delta', text: 'haba' });
    expect(events[2]).toMatchObject({ type: 'done', conversationId: 'c1' });
  });

  it('respects AbortSignal', async () => {
    const mockReader = {
      read: vi.fn().mockImplementation(() => new Promise(() => {})),
      cancel: vi.fn().mockResolvedValue(undefined),
    };
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: { getReader: () => mockReader },
      headers: new Headers(),
    } as unknown as Response);

    const controller = new AbortController();
    const promise = streamChat({ conversationId: 'c1', message: 'x' }, () => {}, { signal: controller.signal });
    controller.abort();

    await expect(promise).rejects.toThrow();
  });
});
