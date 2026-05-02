import type { ChatTranscriptItem, ChatStreamEvent } from '../../lib/types';
import { ToolStatusChip } from './ToolStatusChip';

interface ChatTranscriptProps {
  messages: ChatTranscriptItem[];
  isStreaming: boolean;
  onRetry: () => void;
}

const SUGGESTED_PROMPTS = ['MacBook ara', 'Sepetimi göster', 'Sipariş durumumu kontrol et'];

function getLastToolStatus(events: ChatStreamEvent[] | undefined): { toolName?: string; status: 'pending' | 'success' | 'failure' } | null {
  if (!events) return null;
  const lastToolCall = events.filter(e => e.type === 'tool_call').pop();
  const lastToolResult = events.filter(e => e.type === 'tool_result').pop();

  if (lastToolResult) {
    return { toolName: lastToolResult.toolName, status: lastToolResult.ok ? 'success' : 'failure' };
  }
  if (lastToolCall) {
    return { toolName: lastToolCall.name, status: 'pending' };
  }
  return null;
}

export function ChatTranscript({ messages, isStreaming, onRetry }: ChatTranscriptProps) {
  const isEmpty = messages.length === 0;

  return (
    <div
      role="log"
      aria-live="polite"
      aria-relevant="additions text"
      className="flex-1 overflow-y-auto p-4 pb-16 space-y-4"
    >
      {isEmpty && (
        <div className="space-y-4">
          <h3 className="text-lg font-bold">Merhaba, size nasıl yardımcı olabilirim?</h3>
          <p className="text-sm text-gray-600">
            Ürün arayabilir, sepetinizi düzenleyebilir veya sipariş durumunuzu öğrenebilirsiniz.
          </p>
          <div className="flex flex-wrap gap-2">
            {SUGGESTED_PROMPTS.map(prompt => (
              <span
                key={prompt}
                className="px-3 py-1.5 rounded-full border border-[#E5E7EB] text-sm text-gray-700 bg-gray-50"
              >
                {prompt}
              </span>
            ))}
          </div>
        </div>
      )}

      {messages.map((msg) => {
        if (msg.role === 'user') {
          return (
            <div key={msg.id} className="flex justify-end">
              <div className="bg-[#1C1C1E] text-white px-4 py-2.5 rounded-2xl rounded-tr-sm max-w-[85%]">
                <p className="text-sm">{msg.text}</p>
              </div>
            </div>
          );
        }

        // Assistant or tool messages
        const toolStatus = getLastToolStatus(msg.events);
        const isError = msg.text?.includes('Yanıt alınamadı');

        return (
          <div key={msg.id} className="flex justify-start">
            <div className="max-w-[85%] space-y-2">
              {toolStatus && (
                <ToolStatusChip toolName={toolStatus.toolName} status={toolStatus.status} />
              )}
              {msg.text && (
                <div className={`px-4 py-2.5 rounded-2xl rounded-tl-sm border ${isError ? 'bg-red-50 border-red-200' : 'bg-white border-[#E5E7EB]'}`}>
                  <p className="text-sm whitespace-pre-wrap">{msg.text}</p>
                </div>
              )}
              {isError && (
                <button
                  type="button"
                  onClick={onRetry}
                  className="text-sm text-[#1C1C1E] underline font-medium"
                >
                  Tekrar dene
                </button>
              )}
              {msg.ctaUrl && (
                <a
                  href={msg.ctaUrl}
                  className="inline-block text-sm bg-[#1C1C1E] text-white px-4 py-2 rounded font-medium"
                >
                  Giriş Yap
                </a>
              )}
            </div>
          </div>
        );
      })}

      {isStreaming && messages.length > 0 && messages[messages.length - 1]!.role === 'assistant' && (
        <div className="flex justify-start">
          <span className="text-xs text-gray-500">Yanıt yazılıyor...</span>
        </div>
      )}
    </div>
  );
}
