import type { ReactNode } from "react";

/** 하단 탭 메뉴 정의 — 등록은 가운데 원형 버튼, 채팅은 안 읽음 뱃지 */
export type TabMenuItem = {
  to: string;
  label: string;
  icon: ReactNode;
  end?: boolean;
  register?: boolean;
  chatDot?: boolean;
};

function icon(path: ReactNode) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {path}
    </svg>
  );
}

export const TAB_MENU: TabMenuItem[] = [
  { to: "/", label: "홈", end: true, icon: icon(<path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1z" />) },
  { to: "/search", label: "물건 찾기", icon: icon(<><circle cx="11" cy="11" r="6.5" /><path d="m20 20-4.4-4.4" /></>) },
  { to: "/register", label: "등록", register: true, icon: icon(<path d="M12 5v14M5 12h14" />) },
  { to: "/chat", label: "채팅", chatDot: true, icon: icon(<path d="M20 12a8 8 0 1 0-3.1 6.3L21 20l-1.3-3.9A7.9 7.9 0 0 0 20 12z" />) },
  { to: "/rentals", label: "내 대여", icon: icon(<><path d="M8 6h12M8 12h12M8 18h12" /><path d="M4 6h.01M4 12h.01M4 18h.01" /></>) },
];
