import type { ChatStreamEvent } from './types';

export const SSE_EVENT_NAMES = {
  DELTA: 'delta',
  TOOL_CALL: 'tool_call',
  TOOL_RESULT: 'tool_result',
  DONE: 'done',
  ERROR: 'error',
} as const;

const KNOWN_EVENTS = new Set<string>(Object.values(SSE_EVENT_NAMES));

/**
 * Parse complete SSE frames from a buffer string.
 * Returns parsed events and any incomplete trailing data as `remainder`.
 * For incremental stream parsing, pass the previous remainder as the second argument.
 */
export function parseSseFrames(
  chunk: string,
  previousRemainder = ''
): { events: ChatStreamEvent[]; remainder: string } {
  const buffer = previousRemainder + chunk;
  const events: ChatStreamEvent[] = [];

  // SSE frames are separated by double-newline.
  // We split conservatively to preserve incomplete trailing data.
  let start = 0;
  while (true) {
    const end = buffer.indexOf('\n\n', start);
    if (end === -1) break;
    const frame = buffer.slice(start, end);
    start = end + 2;
    const parsed = parseFrame(frame);
    if (parsed) events.push(parsed);
  }

  const remainder = buffer.slice(start);
  return { events, remainder };
}

function parseFrame(frame: string): ChatStreamEvent | null {
  const lines = frame.split('\n');
  let eventName = '';
  let dataLine = '';

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLine = line.slice(5).trim();
    }
  }

  if (!eventName || !dataLine || !KNOWN_EVENTS.has(eventName)) {
    return null;
  }

  try {
    const payload = JSON.parse(dataLine);
    switch (eventName) {
      case SSE_EVENT_NAMES.DELTA:
        return {
          type: 'delta',
          text: String(payload.text ?? ''),
          conversationId: String(payload.conversationId ?? ''),
        };
      case SSE_EVENT_NAMES.TOOL_CALL:
        return {
          type: 'tool_call',
          name: String(payload.name ?? ''),
          callId: String(payload.callId ?? ''),
          argsJson: String(payload.argsJson ?? '{}'),
        };
      case SSE_EVENT_NAMES.TOOL_RESULT:
        return {
          type: 'tool_result',
          callId: String(payload.callId ?? ''),
          toolName: payload.toolName ? String(payload.toolName) : undefined,
          ok: Boolean(payload.ok),
          summary: String(payload.summary ?? ''),
          resultType: isValidResultType(payload.resultType) ? payload.resultType : 'generic',
          data: payload.data ?? undefined,
        };
      case SSE_EVENT_NAMES.DONE:
        return {
          type: 'done',
          conversationId: String(payload.conversationId ?? ''),
          finalText: String(payload.finalText ?? ''),
        };
      case SSE_EVENT_NAMES.ERROR:
        return {
          type: 'error',
          code: String(payload.code ?? 'UNKNOWN'),
          messageTr: String(payload.messageTr ?? 'Bir hata oluştu.'),
        };
      default:
        return null;
    }
  } catch {
    return null;
  }
}

function isValidResultType(
  v: unknown
): v is 'products' | 'product' | 'cart' | 'order' | 'payment' | 'generic' {
  return (
    typeof v === 'string' &&
    ['products', 'product', 'cart', 'order', 'payment', 'generic'].includes(v)
  );
}

const TOOL_CHIP_MAP: Record<string, string> = {
  search_products: 'Ürünler aranıyor...',
  get_product: 'Ürün detayı getiriliyor...',
  list_categories: 'Kategoriler hazırlanıyor...',
  add_to_cart: 'Sepete ekleniyor...',
  update_cart_item: 'Sepet güncelleniyor...',
  remove_from_cart: 'Sepet güncelleniyor...',
  view_cart: 'Sepet kontrol ediliyor...',
  create_order: 'Sipariş hazırlanıyor...',
  get_payment_link: 'Ödeme bağlantısı hazırlanıyor...',
  get_order_status: 'Sipariş durumu kontrol ediliyor...',
};

export function toolChipCopy(toolName: string): string {
  return TOOL_CHIP_MAP[toolName] ?? 'İşlem hazırlanıyor...';
}

export function isCartMutationTool(toolName: string): boolean {
  return toolName === 'add_to_cart' || toolName === 'update_cart_item' || toolName === 'remove_from_cart';
}
