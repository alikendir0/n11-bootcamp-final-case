import { apiFetch } from '../lib/apiClient';
import type { Address } from '../lib/types';

export interface AddressInput {
  title: string;
  recipientName: string;
  phone: string;
  il: string;
  ilce: string;
  mahalle: string;
  streetLine: string;
  postalCode: string;
  isDefault?: boolean;
}

export function fetchAddresses(): Promise<Address[]> {
  return apiFetch<Address[]>('/identity/addresses');
}

export function createAddress(input: AddressInput): Promise<Address> {
  return apiFetch<Address>('/identity/addresses', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export const addressesQueryKey = ['addresses'] as const;
