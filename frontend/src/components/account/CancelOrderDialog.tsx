interface Props {
  open: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  pending: boolean;
}

export function CancelOrderDialog({ open, onConfirm, onCancel, pending }: Props) {
  if (!open) return null;
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="cancel-heading"
      className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center px-4"
    >
      <div className="bg-white rounded-lg p-6 max-w-md w-full">
        <h2 id="cancel-heading" className="text-lg font-bold mb-3">
          Siparişinizi iptal etmek istediğinizden emin misiniz?
        </h2>
        <p className="text-sm text-gray-700 mb-6">
          Bu işlem geri alınamaz. Stoklar serbest bırakılacak ve ödeme iadesi süreci başlatılacaktır.
        </p>
        <div className="flex gap-3 justify-end">
          <button
            type="button"
            onClick={onCancel}
            disabled={pending}
            className="border border-[var(--color-border)] px-6 py-2 rounded font-bold disabled:opacity-50"
          >
            Vazgeç
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={pending}
            className="bg-[#DC2626] text-white px-6 py-2 rounded font-bold disabled:opacity-50"
          >
            {pending ? 'İptal ediliyor...' : 'Evet, İptal Et'}
          </button>
        </div>
      </div>
    </div>
  );
}
