import api from '@/lib/api';
import type { DirectMessageDto, InboxEntryDto, UnreadCountDto } from '@/types/messaging';

export const messagingApi = {
  send: (recipientId: number, body: string) =>
    api.post<DirectMessageDto>('/messages', { recipientId, body }),

  inbox: () =>
    api.get<InboxEntryDto[]>('/messages/conversations'),

  conversation: (userId: number, page = 0, size = 50) =>
    api.get<DirectMessageDto[]>(`/messages/conversations/${userId}`, { params: { page, size } }),

  unreadCount: () =>
    api.get<UnreadCountDto>('/messages/unread-count'),
};