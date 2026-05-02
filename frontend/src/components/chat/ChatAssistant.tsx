import { useState } from 'react';
import { MessageCircle } from 'lucide-react';
import { ChatDrawer } from './ChatDrawer';

export function ChatAssistant() {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="Yapay Zeka Alışveriş Asistanını Aç"
        className="fixed right-4 bottom-4 md:right-6 md:bottom-6 h-14 w-14 rounded-full bg-[#1C1C1E] text-white flex items-center justify-center shadow-lg z-50"
      >
        <MessageCircle size={24} />
      </button>
      {open && <ChatDrawer onClose={() => setOpen(false)} />}
    </>
  );
}
