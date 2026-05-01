import { Component, type ReactNode } from 'react';

interface Props { children: ReactNode; }
interface State { hasError: boolean; error?: Error; }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo): void {
    console.error('[ErrorBoundary]', error, info);
  }

  handleReload = () => {
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center px-4 bg-[var(--color-body-bg)]">
          <div className="bg-white rounded-lg p-8 max-w-md text-center border border-[var(--color-border)]">
            <h1 className="text-xl font-bold mb-2">Bir hata oluştu</h1>
            <p className="text-sm text-gray-700 mb-6">
              Sayfa yüklenirken beklenmedik bir sorun oldu. Lütfen sayfayı yenileyiniz.
            </p>
            <button
              type="button"
              onClick={this.handleReload}
              className="bg-[#1C1C1E] text-white px-6 py-3 rounded font-bold"
            >
              Sayfayı Yenile
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
