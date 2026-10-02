import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const indexHtmlPath = resolve(dirname(fileURLToPath(import.meta.url)), '../../index.html');

describe('index.html — self-hosted fonts', () => {
  it('does not load fonts from an external host', () => {
    const html = readFileSync(indexHtmlPath, 'utf-8');
    expect(html).not.toMatch(/googleapis\.com|gstatic\.com/);
  });
});
