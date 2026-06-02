export interface DirectMessageDto {
  id: number;
  senderId: number;
  recipientId: number;
  body: string;
  createdAt: string;
  readAt: string | null;
}

export interface InboxEntryDto {
  partnerId: number;
  partnerUsername: string;
  partnerAvatarPreset: string | null;
  partnerHasCustomAvatar: boolean;
  lastBody: string;
  lastMessageAt: string;
  lastSenderId: number;
  unreadCount: number;
}

export interface UnreadCountDto {
  count: number;
}