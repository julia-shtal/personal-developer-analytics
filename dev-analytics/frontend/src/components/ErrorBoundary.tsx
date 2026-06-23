import { Component, type ReactNode, type ErrorInfo } from 'react';

interface Props {
  children: ReactNode;
  /** Optional custom fallback — defaults to the built-in "Something went wrong" screen. */
  fallback?: ReactNode;
}

interface State {
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[ErrorBoundary]', error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      if (this.props.fallback) return this.props.fallback;

      return (
        <div style={{
          display: 'flex', flexDirection: 'column',
          alignItems: 'center', justifyContent: 'center',
          minHeight: '100vh', padding: '40px 24px', textAlign: 'center',
          background: 'var(--bg)', color: 'var(--fg)',
          fontFamily: 'var(--font-sans)',
        }}>
          <p style={{ fontSize: 32, marginBottom: 12 }}>⚠</p>
          <h2 style={{ fontSize: 20, fontWeight: 600, marginBottom: 8 }}>
            Something went wrong
          </h2>
          <p style={{ fontSize: 13, color: 'var(--fg-3)', marginBottom: 24, maxWidth: 420 }}>
            {this.state.error.message}
          </p>
          <button
            className="btn btn-sm btn-accent"
            onClick={() => window.location.reload()}
            aria-label="Reload the page"
          >
            Reload
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}