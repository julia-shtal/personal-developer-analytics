import type { MetricsSummaryDto, InsightDto } from '@/types/ai';

const KIND_SYM: Record<InsightDto['kind'], string> = {
  positive: '+',
  risk: '!',
  note: '~',
};

const KIND_HTML_COLOR: Record<InsightDto['kind'], string> = {
  positive: '#059669',
  risk: '#dc2626',
  note: '#d97706',
};

/** Plain-text rendering — used for clipboard copy and the .txt download. */
export function summaryToText(s: MetricsSummaryDto): string {
  const lines: string[] = [];
  if (s.headline) lines.push(s.headline);
  if (s.overview) lines.push('\nOverview\n' + s.overview);
  if (s.insights.length) {
    lines.push(
      '\nKey Insights\n' +
        s.insights
          .map((i) => {
            const line = `• ${i.text}`;
            return i.explanation ? `${line}\n  ${i.explanation}` : line;
          })
          .join('\n'),
    );
  }
  if (s.recommendations.length) {
    lines.push('\nRecommendations\n' + s.recommendations.map((r) => `• ${r}`).join('\n'));
  }
  return lines.join('');
}

/** GitHub-flavoured Markdown — headings, insight kind symbols, mono-formatted metric tags. */
export function summaryToMarkdown(s: MetricsSummaryDto): string {
  const parts: string[] = [];
  if (s.headline) parts.push(`# ${s.headline}`);
  parts.push(`_Period: ${s.from} → ${s.to}_`);
  if (s.overview) parts.push(`## Overview\n\n${s.overview}`);
  if (s.insights.length) {
    const items = s.insights.map((i) => {
      const tag = i.metric ? ` \`${i.metric}\`` : '';
      const line = `- ${KIND_SYM[i.kind]} ${i.text}${tag}`;
      return i.explanation ? `${line}\n  > ${i.explanation}` : line;
    });
    parts.push(`## Key Insights\n\n${items.join('\n')}`);
  }
  if (s.recommendations.length) {
    parts.push(`## Recommendations\n\n${s.recommendations.map((r) => `- ${r}`).join('\n')}`);
  }
  return parts.join('\n\n') + '\n';
}

// Escapes element-text / quoted-constant contexts. Do NOT use for unquoted HTML attribute values.
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/** Standalone, print-friendly HTML document for browser viewing or Save-as-PDF. */
export function summaryToHtml(s: MetricsSummaryDto): string {
  const insights = s.insights
    .map(
      (i) =>
        `<li><span class="sym" style="color:${KIND_HTML_COLOR[i.kind]}">${KIND_SYM[i.kind]}</span> ${escapeHtml(
          i.text,
        )}${i.metric ? ` <code>${escapeHtml(i.metric)}</code>` : ''}${i.explanation ? `<br><small style="color:#666">${escapeHtml(i.explanation)}</small>` : ''}</li>`,
    )
    .join('\n');
  const recs = s.recommendations.map((r) => `<li>${escapeHtml(r)}</li>`).join('\n');
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<title>${escapeHtml(s.headline || 'AI Summary')}</title>
<style>
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 720px; margin: 40px auto; padding: 0 16px; color: #1a1a2e; line-height: 1.6; }
  h1 { font-size: 24px; margin-bottom: 4px; }
  .period { color: #6b7280; font-size: 13px; margin-bottom: 24px; }
  h2 { font-size: 15px; text-transform: uppercase; letter-spacing: 0.06em; color: #6b7280; margin-top: 28px; }
  ul { padding-left: 18px; }
  li { margin: 6px 0; }
  .sym { font-family: monospace; font-weight: 700; margin-right: 4px; }
  code { background: #f3f4f6; padding: 1px 5px; border-radius: 4px; font-size: 12px; }
  footer { margin-top: 32px; padding-top: 12px; border-top: 1px solid #e5e7eb; color: #9ca3af; font-size: 12px; }
  @media print { body { margin: 0; } }
</style>
</head>
<body>
  <h1>${escapeHtml(s.headline || 'AI Summary')}</h1>
  <div class="period">Period: ${escapeHtml(s.from)} → ${escapeHtml(s.to)}</div>
  ${s.overview ? `<h2>Overview</h2><p>${escapeHtml(s.overview)}</p>` : ''}
  ${insights ? `<h2>Key Insights</h2><ul>${insights}</ul>` : ''}
  ${recs ? `<h2>Recommendations</h2><ul>${recs}</ul>` : ''}
  <footer>Generated locally from metrics only · ${escapeHtml(s.modelName)} via Ollama</footer>
</body>
</html>`;
}

/** Trigger a client-side download of a text payload via a temporary anchor. */
export function downloadFile(filename: string, content: string, mimeType: string): void {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
