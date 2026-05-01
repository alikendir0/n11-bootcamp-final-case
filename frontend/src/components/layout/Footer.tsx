import { Link } from 'react-router-dom';

export function Footer() {
  return (
    <footer className="bg-white border-t border-[var(--color-border)] mt-16">
      <div className="mx-auto max-w-7xl px-4 py-12 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-8">
        <FooterCol heading="n11.com">
          <FooterLink to="/hakkimizda">Hakkımızda</FooterLink>
          <FooterLink to="/iletisim">İletişim</FooterLink>
        </FooterCol>
        <FooterCol heading="Yardım">
          <FooterLink to="/yardim">Yardım Merkezi</FooterLink>
          <FooterLink to="/sozlesmeler">Sözleşmeler</FooterLink>
        </FooterCol>
        <FooterCol heading="Müşteri Hizmetleri">
          <FooterLink to="/iade">İade ve Değişim</FooterLink>
          <FooterLink to="/kargo">Kargo Bilgileri</FooterLink>
        </FooterCol>
        <FooterCol heading="Ödeme Yöntemleri">
          <ul className="flex flex-wrap gap-2 mt-2 text-xs">
            <li className="bg-gray-100 border border-[var(--color-border)] rounded px-2 py-1">Visa</li>
            <li className="bg-gray-100 border border-[var(--color-border)] rounded px-2 py-1">MasterCard</li>
            <li className="bg-gray-100 border border-[var(--color-border)] rounded px-2 py-1">Troy</li>
            <li className="bg-gray-100 border border-[var(--color-border)] rounded px-2 py-1">Amex</li>
          </ul>
        </FooterCol>
      </div>
      <div className="border-t border-[var(--color-border)] py-4 text-center text-xs text-gray-500">
        © 2026 n11 — Bootcamp final case demo. Kişisel veriler 6698 sayılı KVKK kapsamında işlenir.
      </div>
    </footer>
  );
}

function FooterCol({ heading, children }: { heading: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="font-bold mb-3 text-sm">{heading}</h3>
      <ul className="space-y-2 text-sm">{children}</ul>
    </div>
  );
}

function FooterLink({ to, children }: { to: string; children: React.ReactNode }) {
  return (
    <li>
      <Link to={to} className="hover:underline text-gray-700">
        {children}
      </Link>
    </li>
  );
}
