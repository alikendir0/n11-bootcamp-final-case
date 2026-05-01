import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchAddresses, addressesQueryKey } from '../api/addressApi';
import { AccountSidebar } from '../components/account/AccountSidebar';
import { NewAddressForm } from '../components/checkout/NewAddressForm';
import { AddressCard } from '../components/checkout/AddressCard';

export default function AddressesPage() {
  const [showForm, setShowForm] = useState(false);
  const { data, isLoading } = useQuery({ queryKey: addressesQueryKey, queryFn: fetchAddresses });
  const addresses = data ?? [];

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
                onClick={() => setShowForm(true)}
                className="bg-[#1C1C1E] text-white px-4 py-2 rounded font-bold text-sm"
              >
                Yeni Adres Ekle
              </button>
            )}
          </div>

          {showForm && (
            <div className="mb-6">
              <NewAddressForm
                onCreated={() => setShowForm(false)}
                onCancel={() => setShowForm(false)}
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
                onClick={() => setShowForm(true)}
                className="bg-[#1C1C1E] text-white px-6 py-3 rounded font-bold"
              >
                Yeni Adres Ekle
              </button>
            </div>
          ) : (
            <ul className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {addresses.map(addr => (
                <li key={addr.id}>
                  <AddressCard address={addr} selected={false} onSelect={() => {}} />
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
