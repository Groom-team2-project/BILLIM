/** 채팅 도메인 타입 */
import type { CategoryId } from "@/types/item";
import type { RentalStatusView } from "@/types/rental";

export type Chat = {
  id: string;
  name: string;
  item: string;
  cat: CategoryId;
  status: RentalStatusView;
  /** borrowed = 내가 빌리는 대화, lent = 내가 빌려주는 대화 */
  role: "borrowed" | "lent";
  last: string;
  time: string;
  unread: number;
  appt?: string;
};

export type Appointment = {
  kind: "전달" | "반납";
  date: string;
  time: string;
  place: string;
};
