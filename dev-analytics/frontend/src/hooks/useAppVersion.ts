import { useQuery } from '@tanstack/react-query';
import axios from 'axios';

interface ActuatorInfo {
  build?: { version?: string };
}

/**
 * The running build's version, formatted for display, or null until it resolves.
 *
 * Read from /actuator/info rather than a constant so the SPA never carries a second
 * copy of the number the Maven build already owns.
 */
export function useAppVersion(): string | null {
  const { data } = useQuery({
    queryKey: ['actuator-info'],
    queryFn: () => axios.get('/actuator/info').then((r) => r.data as ActuatorInfo),
    staleTime: Infinity,
    retry: false,
  });

  const version = data?.build?.version;
  return version ? `v ${version}` : null;
}
