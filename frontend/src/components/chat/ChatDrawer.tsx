import { X } from 'lucide-react';

interface ChatDrawerProps {
  onClose: () => void;
}

export function ChatDrawer({ onClose }: ChatDrawerProps) {
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
        {/* Body placeholder */}
        <div className="flex-1 overflow-y-auto p-4">
          <p className="text-gray-500">Chat content will appear here.</p>
        </div>
      </div>
    </div>
  );
}
