import { Loader2, Check, X } from 'lucide-react';
import { toolChipCopy, toolCompletedCopy } from '../../lib/chatEvents';

interface ToolStatusChipProps {
  toolName?: string | undefined;
  status: 'pending' | 'success' | 'failure';
}

export function ToolStatusChip({ toolName, status }: ToolStatusChipProps) {
  const label = status === 'pending'
    ? toolChipCopy(toolName ?? '')
    : status === 'success'
      ? toolCompletedCopy(toolName ?? '')
      : 'İşlem tamamlanamadı.';

  return (
    <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-[#E5E7EB] text-sm max-w-full">
      {status === 'pending' && (
        <Loader2 size={14} className="animate-spin shrink-0" aria-hidden="true" />
      )}
      {status === 'success' && (
        <Check size={14} className="text-[#34A853] shrink-0" aria-hidden="true" />
      )}
      {status === 'failure' && (
        <X size={14} className="text-[#DC2626] shrink-0" aria-hidden="true" />
      )}
      <span className="truncate">{label}</span>
    </div>
  );
}

