import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Search, ShoppingCart, User, LogOut, ChevronDown } from 'lucide-react';
import { ROUTES } from '../../lib/routes';
import { useAuthStore } from '../../store/authStore';
import { useCartItemCount } from '../../hooks/useCart';

export function Header() {
  return (
    <header className="sticky top-0 z-50 bg-[var(--color-surface-header)] border-b border-[var(--color-border)] h-16">
      <div className="mx-auto max-w-7xl h-full flex items-center gap-4 px-4">
        <Logo />
        <SearchBar />
        <AccountCluster />
      </div>
    </header>
  );
}

function Logo() {
  return (
    <Link to={ROUTES.HOME} className="text-2xl font-bold tracking-tight" aria-label="n11 Anasayfa">
      n11
    </Link>
  );
}

function SearchBar() {
  const [q, setQ] = useState('');
  const navigate = useNavigate();
  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const trimmed = q.trim();
    if (!trimmed) return;
    navigate(`${ROUTES.SEARCH}?q=${encodeURIComponent(trimmed)}`);
  }
  return (
    <form onSubmit={onSubmit} role="search" className="flex-1 max-w-2xl">
      <div className="relative">
        <input
          type="search"
          value={q}
          onChange={e => setQ(e.target.value)}
          placeholder="Aradığınız ürün, kategori veya markayı yazınız"
          className="w-full h-10 pl-4 pr-12 rounded border border-[var(--color-border)] focus:outline-2 focus:outline-[#1C1C1E]"
        />
        <button
          type="submit"
          aria-label="Ara"
          className="absolute right-0 top-0 h-10 w-12 flex items-center justify-center text-[#1C1C1E]"
        >
          <Search size={18} />
        </button>
      </div>
    </form>
  );
}

function AccountCluster() {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  const cartCount = useCartItemCount();

  return (
    <div className="flex items-center gap-2">
      {isAuthenticated ? <AccountDropdown /> : <AnonymousLinks />}
      <Link
        to={ROUTES.CART}
        aria-label={`Sepetim${cartCount > 0 ? `, ${cartCount} ürün` : ''}`}
        className="relative inline-flex items-center justify-center min-w-[44px] min-h-[44px] px-3 gap-2"
      >
        <ShoppingCart size={20} />
        <span className="text-sm">Sepetim</span>
        {cartCount > 0 && (
          <span className="absolute -top-1 -right-1 bg-[#1C1C1E] text-white text-xs rounded-full min-w-[20px] h-5 px-1.5 inline-flex items-center justify-center font-bold">
            {cartCount}
          </span>
        )}
      </Link>
    </div>
  );
}

function AnonymousLinks() {
  return (
    <div className="flex items-center gap-1 text-sm">
      <Link to={ROUTES.LOGIN} className="px-3 py-2 hover:underline min-h-[44px] inline-flex items-center">
        Giriş Yap
      </Link>
      <span aria-hidden>/</span>
      <Link to={ROUTES.REGISTER} className="px-3 py-2 hover:underline min-h-[44px] inline-flex items-center">
        Üye Ol
      </Link>
    </div>
  );
}

function AccountDropdown() {
  const [open, setOpen] = useState(false);
  const user = useAuthStore(s => s.user);
  const logout = useAuthStore(s => s.logout);
  const navigate = useNavigate();
  const qc = useQueryClient();

  function handleLogout() {
    logout();
    qc.clear();
    setOpen(false);
    navigate(ROUTES.HOME);
    toast.success('Çıkış yapıldı.');
  }

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="inline-flex items-center gap-1 px-3 py-2 min-h-[44px] hover:underline"
      >
        <User size={18} />
        <span className="text-sm">Hesabım</span>
        <ChevronDown size={14} />
      </button>
      {open && (
        <div role="menu" className="absolute right-0 top-full mt-1 w-56 bg-white border border-[var(--color-border)] rounded shadow-lg py-1 z-50">
          <div className="px-3 py-2 text-xs text-gray-600 border-b border-[var(--color-border)] truncate">
            {user?.email}
          </div>
          <Link to={ROUTES.ACCOUNT} role="menuitem" onClick={() => setOpen(false)}
                className="block px-3 py-2 hover:bg-gray-50 text-sm">Hesabım</Link>
          <Link to={ROUTES.ORDERS} role="menuitem" onClick={() => setOpen(false)}
                className="block px-3 py-2 hover:bg-gray-50 text-sm">Siparişlerim</Link>
          <Link to={ROUTES.ADDRESSES} role="menuitem" onClick={() => setOpen(false)}
                className="block px-3 py-2 hover:bg-gray-50 text-sm">Adreslerim</Link>
          <button type="button" onClick={handleLogout} role="menuitem"
                  className="w-full text-left px-3 py-2 hover:bg-gray-50 text-sm flex items-center gap-2">
            <LogOut size={14} /> Çıkış Yap
          </button>
        </div>
      )}
    </div>
  );
}
