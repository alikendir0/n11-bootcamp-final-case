import { pageWindow } from '../../lib/listingParams';

interface Props {
  currentPage: number;  // 1-indexed
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ currentPage, totalPages, onPageChange }: Props) {
  if (totalPages <= 1) return null;
  const window = pageWindow(currentPage, totalPages, 2);
  const atStart = currentPage <= 1;
  const atEnd = currentPage >= totalPages;

  return (
    <nav aria-label="Sayfalama" className="flex items-center justify-center gap-1 my-8">
      <PageButton
        label="Önceki"
        disabled={atStart}
        onClick={() => onPageChange(currentPage - 1)}
      />
      {window.map((p, i) =>
        p === 'ellipsis' ? (
          <span key={`e-${i}`} aria-hidden className="px-2 text-gray-500">
            …
          </span>
        ) : (
          <PageNumber
            key={p}
            page={p}
            current={p === currentPage}
            onClick={() => onPageChange(p)}
          />
        ),
      )}
      <PageButton
        label="Sonraki"
        disabled={atEnd}
        onClick={() => onPageChange(currentPage + 1)}
      />
    </nav>
  );
}

function PageButton({
  label,
  disabled,
  onClick,
}: {
  label: string;
  disabled: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="px-3 h-10 rounded border border-[var(--color-border)] bg-white text-sm hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed min-w-[44px]"
    >
      {label}
    </button>
  );
}

function PageNumber({
  page,
  current,
  onClick,
}: {
  page: number;
  current: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-current={current ? 'page' : undefined}
      className={`min-w-[44px] h-10 rounded text-sm font-bold ${
        current
          ? 'bg-[#1C1C1E] text-white'
          : 'bg-white border border-[var(--color-border)] hover:bg-gray-50'
      }`}
    >
      {page}
    </button>
  );
}
