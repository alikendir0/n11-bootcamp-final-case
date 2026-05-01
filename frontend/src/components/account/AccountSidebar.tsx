import { NavLink } from 'react-router-dom';
import { ROUTES } from '../../lib/routes';

const ITEMS = [
  { to: ROUTES.ACCOUNT, label: 'Hesabım' },
  { to: ROUTES.ORDERS, label: 'Siparişlerim' },
  { to: ROUTES.ADDRESSES, label: 'Adreslerim' },
];

export function AccountSidebar() {
  return (
    <nav aria-label="Hesap menüsü" className="bg-white border border-[var(--color-border)] rounded p-4">
      <ul className="space-y-1">
        {ITEMS.map(item => (
          <li key={item.to}>
            <NavLink
              to={item.to}
              end
              className={({ isActive }) =>
                `block px-3 py-2 rounded text-sm ${
                  isActive
                    ? 'bg-gray-100 font-bold text-[#1C1C1E]'
                    : 'hover:bg-gray-50 text-gray-700'
                }`
              }
            >
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
