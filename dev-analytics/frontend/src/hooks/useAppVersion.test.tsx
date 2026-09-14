import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import axios from 'axios';
import type { ReactNode } from 'react';
import { useAppVersion } from './useAppVersion';

vi.mock('axios');

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe('useAppVersion', () => {
  beforeEach(() => vi.clearAllMocks());

  it('formats the build version reported by the actuator', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { build: { version: '1.0.0' } } });

    const { result } = renderHook(() => useAppVersion(), { wrapper });

    await waitFor(() => expect(result.current).toBe('v 1.0.0'));
    expect(axios.get).toHaveBeenCalledWith('/actuator/info');
  });

  it('returns null until the request resolves', () => {
    vi.mocked(axios.get).mockReturnValue(new Promise(() => {}));

    const { result } = renderHook(() => useAppVersion(), { wrapper });

    expect(result.current).toBeNull();
  });

  it('returns null when the actuator omits build info', async () => {
    // An info endpoint with no build contributor must not render "v undefined".
    vi.mocked(axios.get).mockResolvedValue({ data: {} });

    const { result } = renderHook(() => useAppVersion(), { wrapper });

    await waitFor(() => expect(axios.get).toHaveBeenCalled());
    expect(result.current).toBeNull();
  });
});
