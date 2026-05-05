import { useState, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Sparkles, RefreshCw, Shield, AlertCircle, Copy, Check } from 'lucide-react';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { aiApi } from '@/api/ai';
import type { MetricsSummaryDto } from '@/types/ai';
import type { DateRange } from '@/types';

interface Props {
  range: DateRange;
  onSummaryGenerated?: (summary: MetricsSummaryDto) => void;
}

function timeAgo(date: Date): string {
  const diffMs = Date.now() - date.getTime();
  const diffMin = Math.floor(diffMs / 60_000);
  if (diffMin < 1) return 'just now';
  if (diffMin < 60) return `${diffMin} min ago`;
  const diffH = Math.floor(diffMin / 60);
  return `${diffH}h ago`;
}

function summaryToText(s: MetricsSummaryDto): string {
  const lines: string[] = [];
  if (s.overview) lines.push('Overview\n' + s.overview);
  if (s.insights.length) lines.push('Insights\n' + s.insights.map((i) => `• ${i}`).join('\n'));
  if (s.recommendations.length) lines.push('Recommendations\n' + s.recommendations.map((r) => `• ${r}`).join('\n'));
  return lines.join('\n\n');
}

export function AiSummaryCard({ range, onSummaryGenerated }: Props) {
  const qc = useQueryClient();
  const cacheKey = ['ai-summary-personal', range.from, range.to];

  const [summary, setSummary] = useState<MetricsSummaryDto | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [generatedAt, setGeneratedAt] = useState<Date | null>(null);
  const [generatedForRange, setGeneratedForRange] = useState<DateRange | null>(null);
  const [copied, setCopied] = useState(false);

  // Restore from React Query cache when range changes or on first mount
  useEffect(() => {
    const cached = qc.getQueryData<{ summary: MetricsSummaryDto; generatedAt: number; range: DateRange }>(cacheKey);
    if (cached) {
      setSummary(cached.summary);
      setGeneratedAt(new Date(cached.generatedAt));
      setGeneratedForRange(cached.range);
      onSummaryGenerated?.(cached.summary);
    } else {
      setSummary(null);
      setGeneratedAt(null);
      setGeneratedForRange(null);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [range.from, range.to]);

  const isOutdated =
    generatedForRange !== null &&
    (generatedForRange.from !== range.from || generatedForRange.to !== range.to);

  async function generate() {
    setIsLoading(true);
    setError(null);
    try {
      const result = await aiApi.generateSummary(range.from, range.to);
      const now = Date.now();
      setSummary(result);
      setGeneratedForRange(range);
      setGeneratedAt(new Date(now));
      qc.setQueryData(cacheKey, { summary: result, generatedAt: now, range });
      onSummaryGenerated?.(result);
    } catch {
      setError('AI summary is temporarily unavailable. Metrics remain accessible.');
    } finally {
      setIsLoading(false);
    }
  }

  async function copyToClipboard() {
    if (!summary) return;
    await navigator.clipboard.writeText(summaryToText(summary));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  return (
    <Card>
      <CardHeader className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-violet-500" />
            <h2 className="text-sm font-semibold text-gray-900">AI Summary</h2>
          </div>
          <p className="text-xs text-gray-400 mt-0.5">Automatic overview for the selected period</p>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0 ml-4">
          {isLoading && (
            <span className="animate-pulse bg-violet-50 text-violet-600 text-xs px-2 py-0.5 rounded-full font-medium">
              Generating...
            </span>
          )}
          {summary && !isLoading && (
            <span
              className={
                isOutdated
                  ? 'bg-amber-50 text-amber-600 text-xs px-2 py-0.5 rounded-full font-medium'
                  : 'bg-emerald-50 text-emerald-600 text-xs px-2 py-0.5 rounded-full font-medium'
              }
            >
              {isOutdated ? 'Outdated' : 'Fresh'}
            </span>
          )}
          {summary && !isLoading && (
            <button
              onClick={copyToClipboard}
              className="p-1.5 rounded-md hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors"
              title="Copy to clipboard"
            >
              {copied ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3.5 w-3.5" />}
            </button>
          )}
          <Button variant="secondary" size="sm" loading={isLoading} onClick={generate}>
            {summary ? (
              <>
                <RefreshCw className="h-3.5 w-3.5" />
                Regenerate
              </>
            ) : (
              <>
                <Sparkles className="h-3.5 w-3.5" />
                Generate
              </>
            )}
          </Button>
        </div>
      </CardHeader>

      <CardBody>
        {/* Loading skeleton */}
        {isLoading && (
          <div className="space-y-5">
            <div>
              <div className="h-2.5 w-16 bg-gray-100 rounded animate-pulse mb-3" />
              <div className="space-y-2">
                <div className="h-3 bg-gray-100 rounded animate-pulse" style={{ width: '90%' }} />
                <div className="h-3 bg-gray-100 rounded animate-pulse" style={{ width: '75%' }} />
              </div>
            </div>
            <div>
              <div className="h-2.5 w-20 bg-gray-100 rounded animate-pulse mb-3" />
              <div className="space-y-2">
                {[85, 70, 90, 65, 80].map((w, i) => (
                  <div key={i} className="h-3 bg-gray-100 rounded animate-pulse" style={{ width: `${w}%` }} />
                ))}
              </div>
            </div>
            <div>
              <div className="h-2.5 w-24 bg-gray-100 rounded animate-pulse mb-3" />
              <div className="space-y-2">
                {[75, 60, 70].map((w, i) => (
                  <div key={i} className="h-3 bg-gray-100 rounded animate-pulse" style={{ width: `${w}%` }} />
                ))}
              </div>
            </div>
          </div>
        )}

        {/* Error state */}
        {error && !isLoading && (
          <div className="bg-amber-50 rounded-lg px-4 py-3 border border-amber-200 flex flex-col gap-2">
            <div className="flex items-start gap-2">
              <AlertCircle className="h-4 w-4 text-amber-500 flex-shrink-0 mt-0.5" />
              <span className="text-sm text-amber-700">{error}</span>
            </div>
            <Button variant="ghost" size="sm" onClick={generate} className="self-start">
              Retry
            </Button>
          </div>
        )}

        {/* Empty state */}
        {!summary && !isLoading && !error && (
          <div className="py-8 text-center">
            <Sparkles className="h-8 w-8 text-violet-200 mx-auto" />
            <p className="text-sm text-gray-400 mt-3">No summary generated for this period</p>
            <Button variant="primary" size="sm" onClick={generate} className="mt-4">
              <Sparkles className="h-3.5 w-3.5" />
              Generate AI Summary
            </Button>
          </div>
        )}

        {/* Summary content */}
        {summary && !isLoading && (
          <div className="space-y-5">
            {/* Overview */}
            <div>
              <div className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
                Overview
              </div>
              <p className="text-sm text-gray-700 leading-relaxed">{summary.overview}</p>
            </div>

            {/* Key Insights */}
            {summary.insights.length > 0 && (
              <div>
                <div className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
                  Key Insights
                </div>
                <ul className="space-y-2">
                  {summary.insights.map((insight, i) => (
                    <li key={i} className="flex items-start gap-2.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-violet-400 flex-shrink-0 mt-1.5" />
                      <span className="text-sm text-gray-700">{insight}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Recommendations */}
            {summary.recommendations.length > 0 && (
              <div>
                <div className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
                  Recommendations
                </div>
                <ul className="space-y-2">
                  {summary.recommendations.map((rec, i) => (
                    <li key={i} className="flex items-start gap-2.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 flex-shrink-0 mt-1.5" />
                      <span className="text-sm text-gray-700">{rec}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Footer */}
            <div className="mt-4 pt-4 border-t border-gray-100 flex flex-wrap items-center gap-x-2 gap-y-1">
              <Shield className="h-3.5 w-3.5 text-gray-300" />
              <span className="text-xs text-gray-400">Generated locally from metrics only</span>
              <span className="text-gray-200">·</span>
              <span className="text-xs text-gray-400">{summary.modelName} via Ollama</span>
              {generatedAt && (
                <>
                  <span className="text-gray-200">·</span>
                  <span className="text-xs text-gray-400">{timeAgo(generatedAt)}</span>
                </>
              )}
            </div>
          </div>
        )}
      </CardBody>
    </Card>
  );
}
