import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchAddresses, addressesQueryKey } from '../api/addressApi';
import { useCheckoutStore } from '../store/checkoutStore';
import { ROUTES } from '../lib/routes';
import { CheckoutStepper } from '../components/checkout/CheckoutStepper';
import { AddressCard } from '../components/checkout/AddressCard';
import { NewAddressForm } from '../components/checkout/NewAddressForm';
import type { Address } from '../lib/types';

export default function CheckoutAddressPage() {
  const navigate = useNavigate();
  const setAddress = useCheckoutStore(s => s.setAddress);
  const currentAddressId = useCheckoutStore(s => s.addressId);

  const { data: addresses, isLoading, isError } = useQuery({
    queryKey: addressesQueryKey,
    queryFn: fetchAddresses,
  });

  const [selectedId, setSelectedId] = useState<string | null>(currentAddressId);
  const [showForm, setShowForm] = useState(false);

  // Auto-select default (or first) when addresses arrive and nothing is selected
  useEffect(() => {
    if (!addresses || selectedId) return;
    const defaultAddr = addresses.find(a => a.isDefault) ?? addresses[0];
    if (defaultAddr) setSelectedId(defaultAddr.id);
  }, [addresses, selectedId]);

  // Auto-show inline form if user has no addresses
  useEffect(() => {
    if (addresses && addresses.length === 0) setShowForm(true);
  }, [addresses]);

  function handleNewAddressCreated(a: Address) {
    setSelectedId(a.id);
    setShowForm(false);
  }

  function handleContinue() {
    if (!selectedId) return;
    setAddress(selectedId);
    navigate(ROUTES.CHECKOUT_PAYMENT);
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-6">
      <CheckoutStepper active="adres" />
      <h1 className="text-xl font-bold mb-6">Teslimat Adresi</h1>

      {isLoading && <p>Yükleniyor...</p>}
      {isError && <p className="text-[#DC2626]">Adresler yüklenemedi.</p>}

      {addresses && addresses.length > 0 && (
        <div className="space-y-3 mb-6">
          {addresses.map(addr => (
            <AddressCard
              key={addr.id}
              address={addr}
              selected={addr.id === selectedId}
              onSelect={setSelectedId}
            />
          ))}
        </div>
      )}

      {!showForm && addresses && addresses.length > 0 && (
        <button
          type="button"
          onClick={() => setShowForm(true)}
          className="text-sm font-bold text-[#1C1C1E] hover:underline mb-6"
        >
          + Yeni adres ekle
        </button>
      )}

      {showForm && (
        <div className="mb-6">
          <NewAddressForm
            onCreated={handleNewAddressCreated}
            onCancel={() => setShowForm(false)}
          />
        </div>
      )}

      <button
        type="button"
        disabled={!selectedId}
        onClick={handleContinue}
        className="w-full bg-[#1C1C1E] text-white py-3 rounded font-bold disabled:opacity-50"
      >
        Devam Et
      </button>
    </div>
  );
}
