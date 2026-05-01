import type React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { createAddress, addressesQueryKey, type AddressInput } from '../../api/addressApi';
import type { Address } from '../../lib/types';
import { ApiError } from '../../lib/apiClient';

const schema = z.object({
  title: z.string().min(1).max(50),
  recipientName: z.string().min(2).max(120),
  phone: z.string().regex(/^[0-9 +()\-]{10,20}$/, { message: 'Geçerli bir telefon numarası giriniz.' }),
  il: z.string().min(1).max(50),
  ilce: z.string().min(1).max(80),
  mahalle: z.string().min(1).max(120),
  streetLine: z.string().min(1).max(255),
  postalCode: z.string().regex(/^\d{5}$/, { message: 'Posta kodu 5 haneli olmalıdır.' }),
});

type Values = z.infer<typeof schema>;

export function NewAddressForm({
  onCreated,
  onCancel,
}: {
  onCreated: (a: Address) => void;
  onCancel: () => void;
}) {
  const qc = useQueryClient();
  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { title: 'Ev' },
  });

  const mutation = useMutation({
    mutationFn: (values: Values) => createAddress(values as AddressInput),
    onSuccess: (created) => {
      qc.invalidateQueries({ queryKey: addressesQueryKey });
      toast.success('Adres eklendi.');
      reset();
      onCreated(created);
    },
    onError: (err) => {
      const detail = err instanceof ApiError ? err.problem?.detail : undefined;
      toast.error(detail ?? 'Adres eklenemedi. Lütfen tekrar deneyiniz.');
    },
  });

  return (
    <form
      onSubmit={handleSubmit(v => mutation.mutate(v))}
      className="bg-white border border-[var(--color-border)] rounded p-6 space-y-4"
    >
      <h3 className="font-bold text-sm">Yeni Adres Ekle</h3>
      <Field id="title" label="Adres Başlığı" error={errors.title?.message} {...register('title')} />
      <Field id="recipientName" label="Ad Soyad" error={errors.recipientName?.message} {...register('recipientName')} />
      <Field id="phone" label="Telefon" type="tel" error={errors.phone?.message} {...register('phone')} />
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Field id="il" label="İl" error={errors.il?.message} {...register('il')} />
        <Field id="ilce" label="İlçe" error={errors.ilce?.message} {...register('ilce')} />
      </div>
      <Field id="mahalle" label="Mahalle" error={errors.mahalle?.message} {...register('mahalle')} />
      <Field id="streetLine" label="Sokak / Cadde / No / Daire" error={errors.streetLine?.message} {...register('streetLine')} />
      <Field
        id="postalCode"
        label="Posta Kodu"
        maxLength={5}
        error={errors.postalCode?.message}
        {...register('postalCode')}
      />
      <div className="flex gap-3 pt-2">
        <button
          type="submit"
          disabled={mutation.isPending}
          className="bg-[#1C1C1E] text-white px-6 py-2 rounded font-bold disabled:opacity-50"
        >
          {mutation.isPending ? 'Kaydediliyor...' : 'Adresi Kaydet'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="border border-[var(--color-border)] px-6 py-2 rounded"
        >
          Vazgeç
        </button>
      </div>
    </form>
  );
}

// Inline field component — keeps JSX flat.
type FieldProps = {
  id: string;
  label: string;
  error?: string | undefined;
  type?: string | undefined;
  maxLength?: number | undefined;
  name?: string | undefined;
  onChange?: React.ChangeEventHandler<HTMLInputElement> | undefined;
  onBlur?: React.FocusEventHandler<HTMLInputElement> | undefined;
  ref?: React.Ref<HTMLInputElement> | undefined;
};

const Field = ({ id, label, error, type = 'text', maxLength, ...rest }: FieldProps) => (
  <div>
    <label htmlFor={id} className="block text-sm font-bold mb-1">
      {label}
    </label>
    <input
      id={id}
      type={type}
      maxLength={maxLength}
      {...rest}
      className="w-full h-10 px-3 border border-[var(--color-border)] rounded focus:outline-2 focus:outline-[#1C1C1E]"
    />
    {error && (
      <p role="alert" className="text-xs text-[#DC2626] mt-1">
        {error}
      </p>
    )}
  </div>
);
