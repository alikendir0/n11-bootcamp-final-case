import type { Address } from '../../lib/types';

interface Props {
  address: Address;
  selected: boolean;
  onSelect: (id: string) => void;
}

export function AddressCard({ address, selected, onSelect }: Props) {
  const id = `addr-${address.id}`;
  return (
    <label
      htmlFor={id}
      className={`block bg-white rounded p-4 border cursor-pointer transition-colors ${
        selected ? 'border-[#1C1C1E] ring-2 ring-[#1C1C1E]' : 'border-[var(--color-border)] hover:border-gray-400'
      }`}
    >
      <div className="flex items-start gap-3">
        <input
          id={id}
          type="radio"
          name="address"
          value={address.id}
          checked={selected}
          onChange={() => onSelect(address.id)}
          className="mt-1"
        />
        <div className="flex-1">
          <p className="font-bold text-sm">
            {address.title}
            {address.isDefault && (
              <span className="ml-2 text-xs text-[#34A853]">(Varsayılan)</span>
            )}
          </p>
          <p className="text-sm mt-1">
            <span>{address.recipientName}</span>
            {' · '}
            <span>{address.phone}</span>
          </p>
          <p className="text-sm text-gray-700 mt-1">
            <span>{address.mahalle}</span>
            {', '}
            {address.streetLine}
          </p>
          <p className="text-sm text-gray-700">
            <span>{address.ilce}</span>
            {' / '}
            <span>{address.il}</span>
            {' '}
            <span>{address.postalCode}</span>
          </p>
        </div>
      </div>
    </label>
  );
}
