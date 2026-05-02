import { useState } from 'react';
import { X, Send } from 'lucide-react';
import type { ChatTranscriptItem } from '../../lib/types';
import { ChatTranscript } from './ChatTranscript';

interface ChatDrawerProps {
  messages: ChatTranscriptItem[];
  isStreaming: boolean;
  sendMessage: (msg: string) => void;
  retryLastMessage: () => void;
  onClose: () => void;
}

export function ChatDrawer({ messages, isStreaming, sendMessage, retryLastMessage, onClose }: ChatDrawerProps) {
  const [input, setInput] = useState('');

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!input.trim() || isStreaming) return;
    sendMessage(input.trim());
    setInput('');
  }

  return (
    <div className="fixed inset-0 z-50 flex justify-end" role="dialog" aria-modal="true" aria-label="Yapay Zeka Alışveriş Asistanı">
      {/* Scrim */}
      <div
        className="absolute inset-0 bg-black/15"
        onClick={onClose}
        aria-hidden="true"
      />
      {/* Drawer panel */}
      <div className="relative w-screen md:w-[420px] h-[100dvh] bg-white shadow-xl flex flex-col border-l border-[#E5E7EB]">
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-[#E5E7EB]">
          <div>
            <h2 className="text-lg font-bold text-[#1C1C1E]">Yapay Zeka Alışveriş Asistanı</h2>
            <p className="text-sm text-gray-600">Ürün bulma, sepet ve sipariş işlemlerinde yardımcı olur.</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Asistanı Kapat"
            className="p-2 rounded hover:bg-gray-100"
          >
            <X size={20} />
          </button>
        </div>

        {/* Transcript */}
        <ChatTranscript messages={messages} isStreaming={isStreaming} onRetry={retryLastMessage} />

        {/* Composer */}
        <form
          onSubmit={handleSubmit}
          className="absolute bottom-0 left-0 right-0 px-4 py-3 bg-white border-t border-[#E5E7EB] flex items-end gap-2"
        >
          <input
            type="text"
            value={input}
            onChange={e => setInput(e.target.value)}
            placeholder="Asistana ürün, sepet veya sipariş hakkında yazın..."
            disabled={isStreaming}
            className="flex-1 min-h-[44px] px-3 py-2 rounded border border-[#E5E7EB] focus:outline-2 focus:outline-[#1C1C1E] text-sm disabled:bg-gray-100"
          />
          <button
            type="submit"
            disabled={isStreaming || !input.trim()}
            aria-label={isStreaming ? 'Gönderiliyor...' : 'Mesaj Gönder'}
            className="min-h-[44px] px-4 py-2 bg-[#1C1C1E] text-white rounded text-sm font-medium disabled:opacity-50 flex items-center gap-1"
          >
            <Send size={16} />
            <span className="hidden sm:inline">{isStreaming ? 'Gönderiliyor...' : 'Mesaj Gönder'}</span>
          </button>
        </form>
      </div>
    </div>
  );
}
