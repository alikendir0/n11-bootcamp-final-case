import { Outlet } from 'react-router-dom';
import { Header } from './Header';
import { CategoryNav } from './CategoryNav';
import { Footer } from './Footer';
import { ToastBridge } from '../feedback/ToastBridge';
import { AuthEventBridge } from '../feedback/AuthEventBridge';

export function Layout() {
  return (
    <div className="min-h-screen flex flex-col">
      <Header />
      <CategoryNav />
      <main className="flex-1">
        <Outlet />
      </main>
      <Footer />
      <ToastBridge />
      <AuthEventBridge />
    </div>
  );
}
