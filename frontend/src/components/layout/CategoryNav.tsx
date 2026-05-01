import { NavLink } from 'react-router-dom';
import { CATEGORY_SLUGS, CATEGORY_LABELS } from '../../lib/categories';

export function CategoryNav() {
  return (
    <nav aria-label="Kategoriler" className="bg-white border-b border-[var(--color-border)]">
      <div className="mx-auto max-w-7xl px-4 overflow-x-auto">
        <ul className="flex gap-6 h-12 items-stretch">
          {CATEGORY_SLUGS.map(slug => (
            <li key={slug} className="flex">
              <NavLink
                to={`/${slug}`}
                className={({ isActive }) =>
                  `inline-flex items-center text-sm whitespace-nowrap border-b-2 ${
                    isActive
                      ? 'border-[#1C1C1E] font-bold text-[#1C1C1E]'
                      : 'border-transparent text-gray-700 hover:text-[#1C1C1E]'
                  }`
                }
              >
                {CATEGORY_LABELS[slug]}
              </NavLink>
            </li>
          ))}
        </ul>
      </div>
    </nav>
  );
}
