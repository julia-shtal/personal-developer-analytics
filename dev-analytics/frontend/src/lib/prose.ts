export type ProseToken = { type: 'text' | 'number'; value: string };

/**
 * Splits text into alternating plain-string and numeric-token segments.
 * Matches: integers, decimals, percentages, unit suffixes (×, %, h, d, /wk).
 */
export function tokeniseNumbers(text: string): ProseToken[] {
  const pattern = /\d[\d,.]*(×|%|h|d|\/wk)?/g;
  const tokens: ProseToken[] = [];
  let last = 0;
  let match: RegExpExecArray | null;

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) {
      tokens.push({ type: 'text', value: text.slice(last, match.index) });
    }
    tokens.push({ type: 'number', value: match[0] });
    last = match.index + match[0].length;
  }

  if (last < text.length) {
    tokens.push({ type: 'text', value: text.slice(last) });
  }

  if (tokens.length === 0) {
    tokens.push({ type: 'text', value: text });
  }

  return tokens;
}
