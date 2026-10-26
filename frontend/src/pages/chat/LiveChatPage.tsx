import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { getChatRooms } from "@/api/chat";
import { LiveChatRoom } from "@/components/custom/chat/LiveChatRoom";
import { ChatListItem } from "@/components/custom/chat/ChatListItem";
import { Button } from "@/components/ui/button";
import { Chip } from "@/components/ui/chip";
import { ChipScroller } from "@/components/ui/chip-scroller";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/state";
import { refreshChatUnreadCount } from "@/stores/chat-unread";
import type { ApiChatMessage, ApiChatRoom, ChatRoomFilter } from "@/types/chat-api";
import { classes } from "@/utils/classes";
import styles from "@/pages/chat/ChatPage.module.css";

const c = classes(styles);
const PAGE_SIZE = 20;

export function LiveChatPage() {
  const { chatId } = useParams();
  const navigate = useNavigate();
  const [desktop, setDesktop] = useState(() => window.matchMedia("(min-width: 760px)").matches);
  const [filter, setFilter] = useState<ChatRoomFilter>("ALL");
  const [rooms, setRooms] = useState<ApiChatRoom[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const query = window.matchMedia("(min-width: 760px)");
    const onChange = () => setDesktop(query.matches);
    query.addEventListener("change", onChange);
    return () => query.removeEventListener("change", onChange);
  }, []);

  useEffect(() => {
    let active = true;
    let busy = false;
    let loaded = false;
    const refresh = async () => {
      if (!active || busy || document.hidden) return;
      busy = true;
      try {
        const pages = await Promise.all(Array.from({ length: page + 1 }, (_, index) =>
          getChatRooms({ filter, page: index, size: PAGE_SIZE })));
        if (!active) return;
        setRooms([...new Map(pages.flatMap((result) => result.items).map((room) => [room.id, room])).values()]);
        setHasNext(pages.at(-1)?.hasNext ?? false);
        setError(false);
        loaded = true;
        void refreshChatUnreadCount();
      } catch {
        if (active && !loaded) setError(true);
      } finally {
        busy = false;
        if (active) setLoading(false);
      }
    };
    void refresh();
    const timer = window.setInterval(() => { void refresh(); }, 15000);
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      active = false;
      window.clearInterval(timer);
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, [filter, page, retry]);

  const changeFilter = (next: ChatRoomFilter) => {
    setRooms([]);
    setPage(0);
    setFilter(next);
    setLoading(true);
    if (next === filter) setRetry((value) => value + 1);
  };
  const onRead = (roomId: string) => {
    setRooms((previous) => previous.map((room) => room.id === roomId ? { ...room, unreadCount: 0 } : room));
    void refreshChatUnreadCount();
  };
  const onMessage = useCallback((roomId: string, message: ApiChatMessage) => {
    const preview = message.type === "SYSTEM"
      ? message.system?.label ?? message.body ?? "거래 상태가 변경됐어요"
      : message.body ?? "";
    setRooms((previous) => {
      const index = previous.findIndex((room) => room.id === roomId);
      if (index < 0) return previous;
      const current = previous[index];
      if (current.lastMessageAt && Date.parse(current.lastMessageAt) > Date.parse(message.createdAt)) {
        return previous;
      }
      return [
        { ...current, lastMessagePreview: preview.slice(0, 200), lastMessageAt: message.createdAt },
        ...previous.filter((_, position) => position !== index),
      ];
    });
  }, []);
  const selectedId = chatId ?? (desktop ? rooms[0]?.id : undefined);

  return (
    <div className={c(`chat-page${chatId ? " chat-page--room" : ""}`)}>
      <div className={c(chatId ? "only-desk chat-page-list" : "chat-page-list")}>
        <section className={c("chat-list")} aria-label="채팅 목록">
          <div className={c("chat-list-head")}>
            <h1 className={c("t-title chat-list-title only-desk")}>채팅</h1>
            <ChipScroller label="채팅 필터">
              <Chip selected={filter === "ALL"} onClick={() => changeFilter("ALL")}>전체</Chip>
              <Chip selected={filter === "BORROWER"} onClick={() => changeFilter("BORROWER")}>빌린 물건</Chip>
              <Chip selected={filter === "OWNER"} onClick={() => changeFilter("OWNER")}>빌려준 물건</Chip>
              <Chip selected={filter === "UNREAD"} onClick={() => changeFilter("UNREAD")}>안 읽음</Chip>
            </ChipScroller>
          </div>
          <div className={c("chat-list-rows")}>
            {loading && rooms.length === 0 ? [0, 1, 2, 3].map((index) => (
              <div key={index} className={c("chat-list-skel")}>
                <Skeleton w={44} h={44} r={999} />
                <div className={c("chat-list-skel-lines")}><Skeleton w="40%" /><Skeleton w="75%" /></div>
              </div>
            )) : error ? (
              <ErrorState title="채팅 목록을 불러오지 못했어요" onRetry={() => { setLoading(true); setRetry((value) => value + 1); }} />
            ) : rooms.length === 0 ? (
              <EmptyState icon="chat" title="아직 채팅이 없어요" actions={
                <Button icon="search" onClick={() => navigate("/search")}>물건 찾아보기</Button>
              }>물건 상세에서 ‘채팅으로 물어보기’를 누르면 빌리기 전에 궁금한 걸 물어볼 수 있어요.</EmptyState>
            ) : <>
              {rooms.map((room) => <ChatListItem key={room.id} room={room} selected={room.id === selectedId} />)}
              {hasNext ? <Button variant="secondary" onClick={() => { setLoading(true); setPage((value) => value + 1); }} disabled={loading}>
                {loading ? "불러오는 중" : "더 보기"}
              </Button> : null}
            </>}
          </div>
        </section>
      </div>
      <div className={c(chatId ? "chat-page-room" : "only-desk chat-page-room")}>
        {selectedId ? <LiveChatRoom key={selectedId} roomId={selectedId} onRead={onRead} onMessage={onMessage} /> : null}
      </div>
    </div>
  );
}
