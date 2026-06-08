import { describe, it, expect, vi } from 'vitest';
import { summaryToText, summaryToMarkdown, summaryToHtml, downloadFile } from './export';
import type { MetricsSummaryDto } from '@/types/ai';

const SAMPLE: MetricsSummaryDto = {
  from: '2026-06-01',
  to: '2026-06-07',
  scope: 'PERSONAL',
  headline: 'Strong delivery week',
  overview: 'You shipped 290 commits across 3 repositories.',
  insights: [
    { kind: 'positive', text: 'Merge rate climbed to 84%.', metric: 'merge-rate' },
    { kind: 'risk',     text: 'After-hours work rose sharply.', metric: 'after-hours' },
    { kind: 'note',     text: 'PR size held steady.', metric: '' },
  ],
  recommendations: ['Protect focus time on Mondays.', 'Review large PRs earlier.'],
  rawModelOutput: '{}',
  modelName: 'llama3.2',
  generatedAt: '2026-06-08T09:00:00Z',
};

describe('summaryToText', () => {
  it('includes headline, overview, insights and recommendations', () => {
    const out = summaryToText(SAMPLE);
    expect(out).toContain('Strong delivery week');
    expect(out).toContain('Overview');
    expect(out).toContain('Merge rate climbed to 84%.');
    expect(out).toContain('Protect focus time on Mondays.');
  });
});

describe('summaryToMarkdown', () => {
  it('renders headings and preserves insight kind symbols', () => {
    const md = summaryToMarkdown(SAMPLE);
    expect(md).toContain('# Strong delivery week');
    expect(md).toContain('## Overview');
    expect(md).toContain('## Key Insights');
    expect(md).toContain('+ Merge rate climbed to 84%.');
    expect(md).toContain('! After-hours work rose sharply.');
    expect(md).toContain('~ PR size held steady.');
    expect(md).toContain('## Recommendations');
    expect(md).toContain('- Protect focus time on Mondays.');
  });

  it('matches snapshot', () => {
    expect(summaryToMarkdown(SAMPLE)).toMatchSnapshot();
  });
});

describe('summaryToHtml', () => {
  it('is a standalone document containing the headline and an inline style block', () => {
    const html = summaryToHtml(SAMPLE);
    expect(html).toContain('<!DOCTYPE html>');
    expect(html).toContain('<style>');
    expect(html).toContain('Strong delivery week');
    expect(html).toContain('Merge rate climbed to 84%.');
  });

  it('matches snapshot', () => {
    expect(summaryToHtml(SAMPLE)).toMatchSnapshot();
  });
});

describe('summaryToHtml escaping', () => {
  it('escapes hostile HTML in LLM-sourced fields', () => {
    const hostile = {
      from: '2026-06-01', to: '2026-06-07', scope: 'PERSONAL',
      headline: '<script>alert(1)</script>',
      overview: 'a < b & c > d',
      insights: [{ kind: 'risk', text: '<img src=x onerror=1>', metric: '<b>' }],
      recommendations: ['<iframe>'],
      rawModelOutput: '{}', modelName: 'llama3.2',
    } as import('@/types/ai').MetricsSummaryDto;
    const html = summaryToHtml(hostile);
    expect(html).not.toContain('<script>alert(1)</script>');
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
    expect(html).toContain('a &lt; b &amp; c &gt; d');
    expect(html).not.toContain('<img src=x onerror=1>');
    expect(html).not.toContain('<iframe>');
  });
});

describe('downloadFile', () => {
  it('creates an anchor with the given filename and revokes the object URL', () => {
    if (!URL.createObjectURL) URL.createObjectURL = () => '';
    if (!URL.revokeObjectURL) URL.revokeObjectURL = () => {};
    const createSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:fake');
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    downloadFile('ai-summary-2026-06-07.md', '# hi', 'text/markdown');

    expect(createSpy).toHaveBeenCalledOnce();
    expect(clickSpy).toHaveBeenCalledOnce();
    expect(revokeSpy).toHaveBeenCalledWith('blob:fake');

    createSpy.mockRestore();
    revokeSpy.mockRestore();
    clickSpy.mockRestore();
  });
});