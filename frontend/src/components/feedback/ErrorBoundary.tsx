import { Component, type ReactNode } from 'react';

export class ErrorBoundary extends Component<{ children: ReactNode }, { hasError: boolean }> {
  state = { hasError: false };
  static getDerivedStateFromError() { return { hasError: true }; }
  componentDidCatch(error: Error) { console.error(error); }
  render() {
    if (this.state.hasError) {
      return <div className="p-8 text-center"><h1>Bir hata oluştu</h1></div>;
    }
    return this.props.children;
  }
}
