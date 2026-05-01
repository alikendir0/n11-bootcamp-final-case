import { Link } from 'react-router-dom';

export interface Crumb {
  label: string;
  to?: string; // omit on the final crumb
}

export function Breadcrumbs({ items }: { items: Crumb[] }) {
  return (
    <nav aria-label="Breadcrumb" className="text-sm text-gray-700 mb-4">
      <ol className="flex flex-wrap items-center gap-2">
        {items.map((c, i) => (
          <li key={i} className="flex items-center gap-2">
            {c.to ? (
              <Link to={c.to} className="hover:underline">
                {c.label}
              </Link>
            ) : (
              <span>{c.label}</span>
            )}
            {i < items.length - 1 && <span aria-hidden>›</span>}
          </li>
        ))}
      </ol>
    </nav>
  );
}
