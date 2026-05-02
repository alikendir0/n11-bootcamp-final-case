import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { deleteAddress, fetchAddresses, setDefaultAddress, addressesQueryKey } from '../api/addressApi';
import { AccountSidebar } from '../components/account/AccountSidebar';
import { NewAddressForm } from '../components/checkout/NewAddressForm';
import { AddressCard } from '../components/checkout/AddressCard';
import type { Address } from '../lib/types';
import { ApiError } from '../lib/apiClient';

export default function AddressesPage() {
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Address | null>(null);
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: addressesQueryKey, queryFn: fetchAddresses });
  const addresses = data ?? [];

  const defaultMutation = useMutation({
    mutationFn: setDefaultAddress,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: addressesQueryKey });
      toast.success('Varsayılan adres güncellendi.');
    },
    onError: (err) => {
      const detail = err instanceof ApiError ? err.problem?.detail : undefined;
      toast.error(detail ?? 'Varsayılan adres güncellenemedi.');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteAddress,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: addressesQueryKey });
      toast.success('Adres silindi.');
    },
    onError: (err) => {
      const detail = err instanceof ApiError ? err.problem?.detail : undefined;
      toast.error(detail ?? 'Adres silinemedi.');
    },
  });

  function closeForm() {
    setShowForm(false);
    setEditing(null);
  }

  function startCreate() {
    setEditing(null);
    setShowForm(true);
  }

  function startEdit(address: Address) {
    setEditing(address);
    setShowForm(true);
  }

  function handleDelete(address: Address) {
    if (!window.confirm(`"${address.title}" adresini silmek istiyor musunuz?`)) return;
    deleteMutation.mutate(address.id);
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-6">
      <div className="grid grid-cols-1 lg:grid-cols-[240px_1fr] gap-6">
        <AccountSidebar />
        <div>
          <div className="flex items-center justify-between mb-6">
            <h1 className="text-2xl font-bold">Adreslerim</h1>
            {!showForm && (
              <button
                type="button"
                onClick={startCreate}
                className="bg-[#1C1C1E] text-white px-4 py-2 rounded font-bold text-sm"
              >
                Yeni Adres Ekle
              </button>
            )}
          </div>

          {showForm && (
            <div className="mb-6">
              <NewAddressForm
                address={editing}
                onCreated={closeForm}
                onCancel={closeForm}
              />
            </div>
          )}

          {isLoading ? (
            <p>Yükleniyor...</p>
          ) : addresses.length === 0 && !showForm ? (
            <div className="bg-white border border-[var(--color-border)] rounded p-12 text-center">
              <p className="text-lg font-bold mb-3">Henüz kayıtlı adresiniz yok.</p>
              <button
                type="button"
                onClick={startCreate}
                className="bg-[#1C1C1E] text-white px-6 py-3 rounded font-bold"
              >
                Yeni Adres Ekle
              </button>
            </div>
          ) : (
            <ul className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {addresses.map(addr => (
                <li key={addr.id} className="space-y-2">
                  <AddressCard
                    address={addr}
                    selected={addr.isDefault}
                    onSelect={() => {
                      if (!addr.isDefault) defaultMutation.mutate(addr.id);
                    }}
                  />
                  <div className="flex justify-end gap-2 px-1">
                    <button
                      type="button"
                      onClick={() => startEdit(addr)}
                      className="inline-flex items-center justify-center rounded-full border border-gray-300 bg-white px-4 py-1.5 text-xs font-bold text-gray-800 shadow-sm transition hover:border-[#1C1C1E] hover:bg-gray-50 focus:outline-2 focus:outline-offset-2 focus:outline-[#1C1C1E]"
                    >
                      Düzenle
                    </button>
                    <button
                      type="button"
                      disabled={deleteMutation.isPending}
                      onClick={() => handleDelete(addr)}
                      className="inline-flex items-center justify-center rounded-full border border-red-200 bg-red-50 px-4 py-1.5 text-xs font-bold text-[#B91C1C] shadow-sm transition hover:border-[#DC2626] hover:bg-red-100 focus:outline-2 focus:outline-offset-2 focus:outline-[#DC2626] disabled:opacity-50"
                    >
                      Sil
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
