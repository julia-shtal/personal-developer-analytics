import { describe, it, expect } from 'vitest';
import { tokeniseNumbers } from './prose';

describe('tokeniseNumbers', () => {
  it('splits a leading number from trailing text', () => {
    const result = tokeniseNumbers('4.1× faster than last quarter');
    expect(result).toEqual([
      { type: 'number', value: '4.1×' },
      { type: 'text',   value: ' faster than last quarter' },
    ]);
  });

  it('returns a single text token when no numbers present', () => {
    const result = tokeniseNumbers('no numbers here');
    expect(result).toEqual([{ type: 'text', value: 'no numbers here' }]);
  });

  it('handles integer tokens', () => {
    const result = tokeniseNumbers('290 commits');
    expect(result[0]).toEqual({ type: 'number', value: '290' });
    expect(result[1]).toEqual({ type: 'text', value: ' commits' });
  });

  it('handles percentage tokens', () => {
    const result = tokeniseNumbers('84% merge rate');
    expect(result[0]).toEqual({ type: 'number', value: '84%' });
  });

  it('handles hour tokens', () => {
    const result = tokeniseNumbers('3.2h average');
    expect(result[0]).toEqual({ type: 'number', value: '3.2h' });
  });

  it('handles multiple tokens in one string', () => {
    const result = tokeniseNumbers('290 commits and 4.1× faster');
    const numbers = result.filter((t) => t.type === 'number');
    expect(numbers).toHaveLength(2);
    expect(numbers[0].value).toBe('290');
    expect(numbers[1].value).toBe('4.1×');
  });

  it('returns empty-safe result on empty string', () => {
    const result = tokeniseNumbers('');
    expect(result).toHaveLength(1);
    expect(result[0]).toEqual({ type: 'text', value: '' });
  });
});