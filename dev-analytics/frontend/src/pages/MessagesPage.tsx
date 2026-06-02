import { useState, useEffect, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Send, MessageSquare } from 'lucide-react';
import { messagingApi } from '@/api/messaging';
import { useAuth } from '@/context/AuthContext';
import { Avatar } from '@/components/ui/Avatar';
import { Spinner } from '@/components/ui/Spinner';
import type { InboxEntryDto } from '@/types/messaging';

export function MessagesPage() {
  const { user: me } = useAuth();
  const qc = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedUserId, setSelectedUserId] = useState<number | null>(
    searchParams.get('to') ? Number(searchParams.get('to')) : null,
  );
  const [draft, setDraft] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const { data: inbox = [], isLoading: inboxLoading } = useQuery({
    queryKey: ['messages-inbox'],
    queryFn: () => messagingApi.inbox().then((r) => r.data),
    refetchInterval: 30_000,
  });

  const { data: messages = [], isLoading: threadLoading } = useQuery({
    queryKey: ['messages-conversation', selectedUserId],
    queryFn: () =>
      selectedUserId
        ? messagingApi.conversation(selectedUserId).then((r) => r.data)
        : Promise.resolve([]),
    enabled: selectedUserId !== null,
    refetchInterval: 10_000,
  });

  const sendMutation = useMutation({
    mutationFn: (body: string) => messagingApi.send(selectedUserId!, body),
    onSuccess: () => {
      setDraft('');
      qc.invalidateQueries({ queryKey: ['messages-conversation', selectedUserId] });
      qc.invalidateQueries({ queryKey: ['messages-inbox'] });
      qc.invalidateQueries({ queryKey: ['messages-unread-count'] });
    },
  });

  useEffect(() => {
    if (selectedUserId) {
      qc.invalidateQueries({ queryKey: ['messages-inbox'] });
      qc.invalidateQueries({ queryKey: ['messages-unread-count'] });
    }
  }, [selectedUserId, qc]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  function openConversation(partnerId: number) {
    setSelectedUserId(partnerId);
    setSearchParams(partnerId ? { to: String(partnerId) } : {}, { replace: true });
    setTimeout(() => inputRef.current?.focus(), 100);
  }

  function send() {
    const text = draft.trim();
    if (!text || sendMutation.isPending) return;
    sendMutation.mutate(text);
  }

  const selectedEntry = inbox.find((e) => e.partnerId === selectedUserId);

  return (
    <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>
      {/* ── Left: inbox ──────────────────────────────────────────────────── */}
      <div style={{
        width: 300,
        borderRight: '1px solid var(--line)',
        display: 'flex',
        flexDirection: 'column',
        flexShrink: 0,
        overflow: 'hidden',
      }}>
        <div style={{ padding: '16px 18px 12px', borderBottom: '1px solid var(--line-2)' }}>
          <span className="t-label" style={{ fontSize: 10, letterSpacing: '0.06em' }}>
            DIRECT MESSAGES
          </span>
        </div>

        <div style={{ flex: 1, overflowY: 'auto' }}>
          {inboxLoading && (
            <div className="row gap-2" style={{ padding: 18 }}>
              <Spinner size="sm" />
              <span className="t-muted" style={{ fontSize: 12 }}>Loading…</span>
            </div>
          )}

          {!inboxLoading && inbox.length === 0 && (
            <div style={{ padding: 24, textAlign: 'center' }}>
              <MessageSquare width={28} height={28} style={{ color: 'var(--line)', margin: '0 auto 10px' }} />
              <p className="t-muted" style={{ fontSize: 12 }}>No conversations yet.</p>
              <p className="t-muted" style={{ fontSize: 11, marginTop: 4 }}>
                Open a team member's profile to start a message.
              </p>
            </div>
          )}

          {inbox.map((entry) => (
            <ConversationRow
              key={entry.partnerId}
              entry={entry}
              isSelected={entry.partnerId === selectedUserId}
              currentUserId={me?.id ?? 0}
              onClick={() => openConversation(entry.partnerId)}
            />
          ))}
        </div>
      </div>

      {/* ── Right: thread ────────────────────────────────────────────────── */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {!selectedUserId ? (
          <div style={{
            flex: 1, display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center', color: 'var(--fg-3)',
          }}>
            <MessageSquare width={40} height={40} style={{ color: 'var(--line)', marginBottom: 12 }} />
            <p style={{ fontSize: 14 }}>Select a conversation</p>
            <p className="t-muted" style={{ fontSize: 12, marginTop: 4 }}>
              or message a teammate from the Team page.
            </p>
          </div>
        ) : (
          <>
            {/* Header */}
            {selectedEntry && (
              <div className="row gap-2" style={{
                padding: '12px 20px',
                borderBottom: '1px solid var(--line-2)',
                flexShrink: 0,
              }}>
                <Avatar
                  user={{
                    id: selectedEntry.partnerId,
                    username: selectedEntry.partnerUsername,
                    hasCustomAvatar: selectedEntry.partnerHasCustomAvatar,
                    avatarPreset: selectedEntry.partnerAvatarPreset ?? undefined,
                  }}
                  size="sm"
                />
                <span style={{ fontWeight: 500, fontSize: 13 }}>{selectedEntry.partnerUsername}</span>
              </div>
            )}

            {/* Messages */}
            <div style={{
              flex: 1, overflowY: 'auto',
              padding: '16px 20px',
              display: 'flex', flexDirection: 'column', gap: 10,
            }}>
              {threadLoading && (
                <div className="row gap-2" style={{ padding: '12px 0' }}>
                  <Spinner size="sm" />
                  <span className="t-muted" style={{ fontSize: 12 }}>Loading…</span>
                </div>
              )}

              {!threadLoading && messages.length === 0 && (
                <div style={{ textAlign: 'center', padding: '40px 0' }}>
                  <p className="t-muted" style={{ fontSize: 12 }}>No messages yet. Say hello!</p>
                </div>
              )}

              {[...messages].reverse().map((msg) => {
                const isMe = msg.senderId === me?.id;
                return (
                  <div key={msg.id} style={{ display: 'flex', justifyContent: isMe ? 'flex-end' : 'flex-start' }}>
                    <div style={{
                      maxWidth: '75%',
                      padding: '9px 14px',
                      borderRadius: isMe ? '14px 14px 4px 14px' : '14px 14px 14px 4px',
                      background: isMe ? 'var(--accent)' : 'var(--bg-2)',
                      color: isMe ? '#fff' : 'var(--fg)',
                      fontSize: 13,
                      lineHeight: 1.55,
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                    }}>
                      {msg.body}
                    </div>
                  </div>
                );
              })}

              {sendMutation.isPending && (
                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                  <div style={{
                    padding: '9px 14px',
                    borderRadius: '14px 14px 4px 14px',
                    background: 'var(--accent)',
                    opacity: 0.5,
                    fontSize: 13,
                    color: '#fff',
                  }}>
                    {draft}
                  </div>
                </div>
              )}

              {sendMutation.isError && (
                <div style={{
                  padding: '8px 12px',
                  background: 'var(--coral-bg)',
                  border: '1px solid var(--coral)',
                  borderRadius: 6,
                  fontSize: 12,
                  color: 'var(--coral-strong)',
                }}>
                  Failed to send. Please try again.
                </div>
              )}

              <div ref={bottomRef} />
            </div>

            {/* Compose */}
            <div style={{
              borderTop: '1px solid var(--line-2)',
              padding: '12px 20px',
              display: 'flex', gap: 8, alignItems: 'flex-end',
              flexShrink: 0,
            }}>
              <textarea
                ref={inputRef}
                className="input"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    send();
                  }
                }}
                placeholder="Write a message… (Enter to send, Shift+Enter for newline)"
                rows={2}
                disabled={sendMutation.isPending}
                aria-label="Message input"
                style={{ flex: 1, resize: 'none', minHeight: 44 }}
              />
              <button
                className="btn btn-sm btn-accent"
                onClick={send}
                disabled={!draft.trim() || sendMutation.isPending}
                aria-label="Send message"
              >
                <Send width={12} height={12} />
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

function ConversationRow({
  entry,
  isSelected,
  currentUserId,
  onClick,
}: {
  entry: InboxEntryDto;
  isSelected: boolean;
  currentUserId: number;
  onClick: () => void;
}) {
  const preview = entry.lastSenderId === currentUserId
    ? `You: ${entry.lastBody}`
    : entry.lastBody;
  const truncated = preview.length > 50 ? preview.slice(0, 47) + '…' : preview;

  return (
    <button
      onClick={onClick}
      style={{
        width: '100%', textAlign: 'left',
        padding: '10px 14px',
        background: isSelected ? 'var(--bg-2)' : 'transparent',
        border: 'none',
        borderBottom: '1px solid var(--line-2)',
        cursor: 'pointer',
        display: 'flex', alignItems: 'center', gap: 10,
        position: 'relative',
      }}
    >
      <Avatar
        user={{
          id: entry.partnerId,
          username: entry.partnerUsername,
          hasCustomAvatar: entry.partnerHasCustomAvatar,
          avatarPreset: entry.partnerAvatarPreset ?? undefined,
        }}
        size="sm"
      />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 13, fontWeight: entry.unreadCount > 0 ? 600 : 400, color: 'var(--fg)' }}>
            {entry.partnerUsername}
          </span>
          {entry.unreadCount > 0 && (
            <span style={{
              background: 'var(--accent)',
              color: '#fff',
              borderRadius: 10,
              fontSize: 10,
              fontWeight: 600,
              padding: '1px 6px',
              minWidth: 18,
              textAlign: 'center',
            }}>
              {entry.unreadCount > 99 ? '99+' : entry.unreadCount}
            </span>
          )}
        </div>
        <div className="t-muted" style={{ fontSize: 11, marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {truncated}
        </div>
      </div>
    </button>
  );
}