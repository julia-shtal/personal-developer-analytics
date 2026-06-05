import { useState, useEffect, useRef } from 'react';
import { X, Send, Sparkles } from 'lucide-react';
import { Spinner } from '@/components/ui/Spinner';
import { aiApi } from '@/api/ai';
import { AI } from '@/components/icons';
import type { MessageDto, MetricsSummaryDto } from '@/types/ai';

interface Props {
  open: boolean;
  onClose: () => void;
  summary: MetricsSummaryDto;
}

export function FollowUpDrawer({ open, onClose, summary }: Props) {
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [messages, setMessages] = useState<MessageDto[]>([]);
  const [input, setInput] = useState('');
  const [isStarting, setIsStarting] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setConversationId(null);
      setMessages([]);
      setInput('');
      setError(null);
      return;
    }

    async function start() {
      setIsStarting(true);
      setError(null);
      try {
        const conv = await aiApi.startConversation(summary.scope, JSON.stringify(summary));
        setConversationId(conv.id);
        setTimeout(() => inputRef.current?.focus(), 100);
      } catch {
        setError('Failed to start conversation. Please try again.');
      } finally {
        setIsStarting(false);
      }
    }

    start();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isSending]);

  async function send() {
    const text = input.trim();
    if (!text || !conversationId || isSending) return;

    setInput('');
    setIsSending(true);
    setError(null);

    const optimisticId = Date.now();
    const optimisticMsg: MessageDto = {
      id: optimisticId,
      role: 'USER',
      content: text,
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, optimisticMsg]);

    try {
      const response = await aiApi.sendMessage(conversationId, text);
      setMessages((prev) => [...prev, response]);
    } catch {
      setMessages((prev) => prev.filter((m) => m.id !== optimisticId));
      setError('Failed to get a response. Please try again.');
    } finally {
      setIsSending(false);
      inputRef.current?.focus();
    }
  }

  if (!open) return null;

  return (
    <>
      {/* Backdrop */}
      <div
        onClick={onClose}
        style={{
          position: 'fixed', inset: 0, zIndex: 49,
          background: 'color-mix(in oklab, #000 25%, transparent)',
        }}
        aria-hidden="true"
      />

      {/* Drawer */}
      <div
        role="dialog"
        aria-label="AI follow-up chat"
        style={{
          position: 'fixed', right: 0, top: 0,
          height: '100vh', width: 'min(480px, 100vw)',
          background: 'var(--bg-card)',
          borderLeft: '1px solid var(--line)',
          boxShadow: '-4px 0 32px rgba(0,0,0,.14)',
          display: 'flex', flexDirection: 'column',
          zIndex: 50,
          animation: 'drawer-slide-in .2s cubic-bezier(.2,.9,.3,1)',
        }}
      >
        {/* Header */}
        <div className="row gap-2" style={{
          padding: '14px 18px',
          borderBottom: '1px solid var(--line-2)',
          flexShrink: 0,
        }}>
          <button
            onClick={onClose}
            className="btn btn-sm btn-icon"
            aria-label="Close follow-up chat"
            style={{ marginRight: 4 }}
          >
            <X width={14} height={14} />
          </button>
          <AI width={14} height={14} style={{ color: 'var(--accent-strong)' }} />
          <span className="t-label" style={{ color: 'var(--accent-strong)', letterSpacing: '0.06em' }}>
            ASK FOLLOW-UP
          </span>
          <span className="tick">·</span>
          <span className="t-label" style={{ fontSize: 10, color: 'var(--fg-3)' }}>
            {summary.modelName} via Ollama
          </span>
        </div>

        {/* Messages */}
        <div style={{
          flex: 1, overflowY: 'auto',
          padding: '16px 18px',
          display: 'flex', flexDirection: 'column', gap: 12,
        }}>
          {isStarting && (
            <div className="row gap-2" style={{ padding: '12px 0' }}>
              <Spinner size="sm" />
              <span className="t-muted" style={{ fontSize: 12 }}>Starting conversation…</span>
            </div>
          )}

          {!isStarting && messages.length === 0 && !error && (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Sparkles width={28} height={28} style={{ color: 'var(--line)', margin: '0 auto 12px' }} />
              <p className="t-muted" style={{ fontSize: 12 }}>
                Ask anything about your metrics summary for this period.
              </p>
              <p className="t-muted" style={{ fontSize: 11, marginTop: 4 }}>
                Press Enter to send · Shift+Enter for newline
              </p>
            </div>
          )}

          {messages.map((msg) => (
            <div
              key={msg.id}
              style={{
                display: 'flex',
                justifyContent: msg.role === 'USER' ? 'flex-end' : 'flex-start',
              }}
            >
              <div style={{
                maxWidth: '85%',
                padding: '10px 14px',
                borderRadius: msg.role === 'USER'
                  ? '12px 12px 4px 12px'
                  : '12px 12px 12px 4px',
                background: msg.role === 'USER' ? 'var(--accent)' : 'var(--bg-2)',
                color: msg.role === 'USER' ? '#fff' : 'var(--fg)',
                fontSize: 13,
                lineHeight: 1.55,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
              }}>
                {msg.content}
              </div>
            </div>
          ))}

          {isSending && (
            <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
              <div className="row gap-2" style={{
                padding: '10px 14px',
                borderRadius: '12px 12px 12px 4px',
                background: 'var(--bg-2)',
              }}>
                <Spinner size="sm" />
                <span className="t-muted" style={{ fontSize: 12 }}>Thinking…</span>
              </div>
            </div>
          )}

          {error && (
            <div style={{
              padding: '8px 12px',
              background: 'var(--coral-bg)',
              border: '1px solid var(--coral)',
              borderRadius: 6,
            }}>
              <span style={{ fontSize: 12, color: 'var(--coral-strong)' }}>{error}</span>
            </div>
          )}

          <div ref={bottomRef} />
        </div>

        {/* Input */}
        <div style={{
          borderTop: '1px solid var(--line-2)',
          padding: '12px 18px',
          display: 'flex', gap: 8, alignItems: 'flex-end',
          flexShrink: 0,
        }}>
          <textarea
            ref={inputRef}
            className="input"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                send();
              }
            }}
            placeholder="Ask a follow-up question…"
            rows={2}
            disabled={!conversationId || isSending}
            aria-label="Follow-up message"
            style={{ flex: 1, resize: 'none', minHeight: 44 }}
          />
          <button
            className="btn btn-sm btn-accent"
            onClick={send}
            disabled={!conversationId || !input.trim() || isSending}
            aria-label="Send message"
          >
            <Send width={12} height={12} />
          </button>
        </div>
      </div>

      <style>{`
        @keyframes drawer-slide-in {
          from { transform: translateX(32px); opacity: 0 }
          to   { transform: none; opacity: 1 }
        }
      `}</style>
    </>
  );
}
