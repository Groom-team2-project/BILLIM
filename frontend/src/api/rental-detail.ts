import { api } from "@/api/client";

/** C_031 거래 상세 중 알림의 채팅 이동에 필요한 필드 */
export function getRentalChatRoom(rentalId: string): Promise<{ chatRoomId?: string }> {
  return api<{ chatRoomId?: string }>(`/rentals/${encodeURIComponent(rentalId)}`);
}
