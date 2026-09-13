import { describe, it, expect } from 'vitest';
import { createQueryClient, shouldRetry } from './queryClient';

describe('createQueryClient', () => {
  it('does not refetch on window focus', () => {
    // The dashboard mounts ~22 queries. Refetching them all on every focus event
    // exhausts the server's per-user budget, and the failures read as missing data.
    expect(createQueryClient().getDefaultOptions().queries?.refetchOnWindowFocus).toBe(false);
  });

  it('keeps the two-minute stale time', () => {
    expect(createQueryClient().getDefaultOptions().queries?.staleTime).toBe(1000 * 60 * 2);
  });
});

describe('shouldRetry', () => {
  it('does not retry a request the server rejected as not found', () => {
    expect(shouldRetry(0, { response: { status: 404 } })).toBe(false);
  });

  it('does not retry a rate-limited request', () => {
    // Retrying a 429 deepens the shortage it reports.
    expect(shouldRetry(0, { response: { status: 429 } })).toBe(false);
  });

  it('retries a server error once', () => {
    expect(shouldRetry(0, { response: { status: 500 } })).toBe(true);
    expect(shouldRetry(1, { response: { status: 500 } })).toBe(false);
  });

  it('retries a network failure once', () => {
    expect(shouldRetry(0, new Error('network down'))).toBe(true);
    expect(shouldRetry(1, new Error('network down'))).toBe(false);
  });
});
