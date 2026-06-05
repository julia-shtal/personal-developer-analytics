import { Brain, X, Sparkles, Shield } from 'lucide-react';
import type { MetricsSummaryDto } from '@/types/ai';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  metricLabel: string;
  metricDescription: string;
  metricValue: string;
  summary: MetricsSummaryDto | null;
}

export function AiMetricExplainDrawer({
  isOpen,
  onClose,
  metricLabel,
  metricDescription,
  metricValue,
  summary,
}: Props) {
  return (
    <div className={isOpen ? 'pointer-events-auto' : 'pointer-events-none'}>
      {/* Backdrop */}
      {isOpen && (
        <div className="fixed inset-0 z-40 bg-black/20 backdrop-blur-sm" onClick={onClose} />
      )}

      {/* Drawer panel */}
      <div
        className={[
          'fixed top-0 right-0 bottom-0 z-50 w-full max-w-md bg-white border-l border-gray-200 shadow-2xl flex flex-col',
          'transition-transform duration-300 ease-in-out',
          isOpen ? 'translate-x-0' : 'translate-x-full',
        ].join(' ')}
      >
        {/* Header */}
        <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between flex-shrink-0">
          <div className="flex items-center gap-2">
            <Brain className="h-4 w-4" style={{ color: 'var(--accent)' }} />
            <span className="text-sm font-semibold text-gray-900">Metric Explanation</span>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-5 py-5 space-y-6">
          {/* Section 1 — Metric */}
          <div>
            <h3 className="text-base font-semibold text-gray-900">{metricLabel}</h3>
            <p className="text-sm text-gray-600 leading-relaxed mt-1">{metricDescription}</p>
          </div>

          {/* Section 2 — Current value */}
          <div className="space-y-1.5">
            <div className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
              Current Value
            </div>
            <span
              className="inline-flex px-4 py-2 rounded-lg text-2xl font-semibold"
              style={{ background: 'var(--accent-bg)', color: 'var(--accent-strong)' }}
            >
              {metricValue}
            </span>
          </div>

          {/* Section 3 — AI Insights */}
          <div className="space-y-1.5">
            <div className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
              AI Insights for this period
            </div>
            {summary && summary.insights.length > 0 ? (
              <ul className="space-y-2">
                {summary.insights.slice(0, 3).map((insight, i) => (
                  <li key={i} className="flex items-start gap-2.5">
                    <span
                      className="w-1.5 h-1.5 rounded-full flex-shrink-0 mt-1.5"
                      style={{ background: 'var(--accent)' }}
                    />
                    <span className="text-sm text-gray-700">{insight.text}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <div className="flex items-start gap-2 py-3 px-4 bg-gray-50 rounded-lg border border-gray-100">
                <Sparkles className="h-4 w-4 text-gray-400 flex-shrink-0 mt-0.5" />
                <span className="text-sm text-gray-500">
                  Generate an AI summary first to see metric-specific insights.
                </span>
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        {summary && (
          <div className="px-5 py-4 border-t border-gray-100 flex-shrink-0">
            <div className="flex items-center gap-1.5">
              <Shield className="h-3.5 w-3.5 text-gray-300" />
              <span className="text-xs text-gray-400">
                Generated locally from metrics only · {summary.modelName} via Ollama
              </span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
