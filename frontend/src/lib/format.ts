const TRY_FORMATTER = new Intl.NumberFormat('tr-TR', {
  style: 'currency',
  currency: 'TRY',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const DATE_FORMATTER = new Intl.DateTimeFormat('tr-TR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
});

export function formatTRY(amount: number): string {
  return TRY_FORMATTER.format(amount);
}

export function formatTRDate(d: Date | string): string {
  const date = typeof d === 'string' ? new Date(d) : d;
  return DATE_FORMATTER.format(date);
}
