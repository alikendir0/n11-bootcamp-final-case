import { Toaster } from 'sonner';

export function ToastBridge() {
  return (
    <Toaster richColors position="top-right" duration={4000} visibleToasts={3} closeButton />
  );
}
