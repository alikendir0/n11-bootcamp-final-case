import { describe, it, expect } from 'vitest';
import {
  SSE_EVENT_NAMES,
  parseSseFrames,
  toolChipCopy,
  isCartMutationTool,
} from './chatEvents';

describe('SSE_EVENT_NAMES', () => {
  it('contains all five backend event names', () => {
    expect(SSE_EVENT_NAMES.DELTA).toBe('delta');
    expect(SSE_EVENT_NAMES.TOOL_CALL).toBe('tool_call');
    expect(SSE_EVENT_NAMES.TOOL_RESULT).toBe('tool_result');
    expect(SSE_EVENT_NAMES.DONE).toBe('done');
    expect(SSE_EVENT_NAMES.ERROR).toBe('error');
  });
});

describe('parseSseFrames', () => {
  it('parses a single named delta event', () => {
    const chunk = 'event: delta\ndata: {"text":"Merhaba","conversationId":"c1"}\n\n';
    const { events, remainder } = parseSseFrames(chunk);
    expect(events).toHaveLength(1);
    expect(events[0]).toEqual({
      type: 'delta',
      text: 'Merhaba',
      conversationId: 'c1',
    });
    expect(remainder).toBe('');
  });

  it('parses multiple events in one chunk', () => {
    const chunk =
      'event: tool_call\ndata: {"name":"search_products","callId":"id1","argsJson":"{}"}\n\n' +
      'event: tool_result\ndata: {"callId":"id1","toolName":"search_products","ok":true,"summary":"found","resultType":"products","data":{"products":[{"id":"p1","name":"Ürün"}]}}\n\n';
    const { events, remainder } = parseSseFrames(chunk);
    expect(events).toHaveLength(2);
    expect(events[0]).toMatchObject({ type: 'tool_call', name: 'search_products' });
    expect(events[1]).toMatchObject({
      type: 'tool_result',
      resultType: 'products',
      data: { products: [{ id: 'p1', name: 'Ürün' }] },
    });
    expect(remainder).toBe('');
  });

  it('buffers incomplete frames and returns remainder', () => {
    const chunk = 'event: delta\ndata: {"text":"Merhaba"}\n\nevent: tool_call\ndata: {"name":"sea';
    const { events, remainder } = parseSseFrames(chunk);
    expect(events).toHaveLength(1);
    expect(remainder).toContain('event: tool_call');
  });

  it('ignores unknown event names', () => {
    const chunk = 'event: unknown\ndata: {"x":1}\n\n';
    const { events } = parseSseFrames(chunk);
    expect(events).toHaveLength(0);
  });

  it('handles error events', () => {
    const chunk = 'event: error\ndata: {"code":"UPSTREAM_LLM_ERROR","messageTr":"Bir hata oluştu"}\n\n';
    const { events } = parseSseFrames(chunk);
    expect(events).toHaveLength(1);
    expect(events[0]).toEqual({
      type: 'error',
      code: 'UPSTREAM_LLM_ERROR',
      messageTr: 'Bir hata oluştu',
    });
  });

  it('handles done events', () => {
    const chunk = 'event: done\ndata: {"conversationId":"c1","finalText":"Tamam"}\n\n';
    const { events } = parseSseFrames(chunk);
    expect(events).toHaveLength(1);
    expect(events[0]).toEqual({
      type: 'done',
      conversationId: 'c1',
      finalText: 'Tamam',
    });
  });

  it('handles frames split across chunks when fed incrementally', () => {
    let remainder = '';
    const chunk1 = 'event: delta\ndata: {"text":"Mer';
    const r1 = parseSseFrames(chunk1, remainder);
    expect(r1.events).toHaveLength(0);
    expect(r1.remainder).toBe(chunk1);

    const chunk2 = 'haba","conversationId":"c1"}\n\n';
    const r2 = parseSseFrames(chunk2, r1.remainder);
    expect(r2.events).toHaveLength(1);
    expect(r2.events[0]).toEqual({
      type: 'delta',
      text: 'Merhaba',
      conversationId: 'c1',
    });
    expect(r2.remainder).toBe('');
  });
});

describe('toolChipCopy', () => {
  it('maps all 10 canonical tools to Turkish', () => {
    expect(toolChipCopy('search_products')).toBe('Ürünler aranıyor...');
    expect(toolChipCopy('get_product')).toBe('Ürün detayı getiriliyor...');
    expect(toolChipCopy('list_categories')).toBe('Kategoriler hazırlanıyor...');
    expect(toolChipCopy('add_to_cart')).toBe('Sepete ekleniyor...');
    expect(toolChipCopy('update_cart_item')).toBe('Sepet güncelleniyor...');
    expect(toolChipCopy('remove_from_cart')).toBe('Sepet güncelleniyor...');
    expect(toolChipCopy('view_cart')).toBe('Sepet kontrol ediliyor...');
    expect(toolChipCopy('create_order')).toBe('Sipariş hazırlanıyor...');
    expect(toolChipCopy('get_payment_link')).toBe('Ödeme bağlantısı hazırlanıyor...');
    expect(toolChipCopy('get_order_status')).toBe('Sipariş durumu kontrol ediliyor...');
  });

  it('returns default for unknown tools', () => {
    expect(toolChipCopy('unknown_tool')).toBe('İşlem hazırlanıyor...');
  });
});

describe('isCartMutationTool', () => {
  it('returns true for cart mutation tools', () => {
    expect(isCartMutationTool('add_to_cart')).toBe(true);
    expect(isCartMutationTool('update_cart_item')).toBe(true);
    expect(isCartMutationTool('remove_from_cart')).toBe(true);
  });

  it('returns false for non-mutation tools', () => {
    expect(isCartMutationTool('search_products')).toBe(false);
    expect(isCartMutationTool('view_cart')).toBe(false);
    expect(isCartMutationTool('create_order')).toBe(false);
    expect(isCartMutationTool('get_payment_link')).toBe(false);
  });
});
