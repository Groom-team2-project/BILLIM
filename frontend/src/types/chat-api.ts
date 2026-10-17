/** 채팅 API 응답. 화면 목데이터 타입과 구분한다. */
export type ApiChatMember = {
  id: string;
  displayName: string;
  joinedAt: string;
};

export type ApiRentalStatus =
  | "REQUESTED"
  | "APPROVED"
  | "REJECTED"
  | "CANCELED"
  | "ACTIVE"
  | "RETURNED"
  | "EXPIRED";

export type ApiChatRental = {
  id: string;
  itemId: string;
  itemTitle: string;
  owner: ApiChatMember;
  borrower: ApiChatMember;
  startDate: string;
  endDate: string;
  status: ApiRentalStatus;
  overdue: boolean;
  dDay: number;
  placeName: string;
  version: number;
  allowedActions: string[];
  createdAt: string;
};

export type ApiChatAccess = {
  canSend: boolean;
  allowedRentalIds: string[];
  reason:
    | "NORMAL"
    | "BLOCKED"
    | "RESTRICTED"
    | "COMMUNITY_CHANGED"
    | "ITEM_UNAVAILABLE"
    | "EXISTING_RENTAL_ONLY";
};

export type ApiChatRoom = {
  id: string;
  itemId: string;
  communityId: string;
  itemTitle: string;
  itemThumbnailUrl?: string | null;
  otherMember: ApiChatMember;
  unreadCount: number;
  lastMessagePreview?: string;
  lastMessageAt?: string;
  rentals: ApiChatRental[];
  access: ApiChatAccess;
};

export type ApiChatRoomPage = {
  items: ApiChatRoom[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
};

export type ChatRoomFilter = "ALL" | "BORROWER" | "OWNER" | "UNREAD";

export type ApiChatMessage = {
  id: string;
  roomId: string;
  sequence: number;
  type: "TEXT" | "SYSTEM";
  senderId?: string;
  body?: string;
  rentalId?: string;
  appointmentProposalId?: string;
  clientMessageId?: string;
  system?: {
    eventType: string;
    aggregateVersion: number;
    occurredAt: string;
    label: string;
  };
  createdAt: string;
};

export type ApiChatMessagePage = {
  items: ApiChatMessage[];
  hasMore: boolean;
  nextAfterSequence: number | null;
  nextBeforeSequence: number | null;
  otherLastReadSequence: number;
};

export type SendChatMessageBody = {
  clientMessageId: string;
  body: string;
  rentalId?: string;
};

export type ApiChatReadPosition = { lastReadSequence: number };
export type ApiChatUnreadCount = { unreadCount: number };
