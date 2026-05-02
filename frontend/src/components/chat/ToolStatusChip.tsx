import { Loader2 } from 'lucide-react';
import { toolChipCopy } from '../../lib/chatEvents';

interface ToolStatusChipProps {
  toolName?: string | undefined;
  status: 'pending' | 'success' | 'failure';
}

export function ToolStatusChip({ toolName, status }: ToolStatusChipProps) {
  const label = status === 'pending'
    ? toolChipCopy(toolName ?? '')
    : status === 'success'
      ? 'İşlem tamamlandı.'
      : 'İşlem tamamlanamadı.';

  return (
    <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-[#E5E7EB] text-sm max-w-full">
      {status === 'pending' && (
        <Loader2 size={14} className="animate-spin shrink-0" aria-hidden="true" />
      )}
      {status === 'success' && (
        <span className="w-2 h-2 rounded-full bg-[#34A853] shrink-0" aria-hidden="true" />
      )}
      {status === 'failure' && (
        <span className="w-2 h-2 rounded-full bg-[#DC2626] shrink-0" aria-hidden="true" />
      )}
      <span className="truncate">{label}</span>
    </div>
  );
}
