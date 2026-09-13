import { QueryClient } from '@tanstack/react-query';

/**
 * Retry policy. A 4xx will answer the same way however often it is asked, and retrying
 * a 429 deepens the shortage it reports, so only transport and server faults are retried.
 */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  const status = (error as { response?: { status?: number } })?.response?.status;
  if (status != null && status >= 400 && status < 500) return false;
  return failureCount < 1;
}

/**
 * The app's shared query defaults, exported as a factory so the policy can be asserted
 * in tests rather than assumed.
 *
 * <p>refetchOnWindowFocus is off: the dashboard mounts around twenty queries, and
 * refiring them on every focus event exceeds the server's per-user rate limit, which
 * surfaces to the reader as charts that empty themselves when they switch tabs.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 1000 * 60 * 2,
        retry: shouldRetry,
        refetchOnWindowFocus: false,
      },
    },
  });
}
