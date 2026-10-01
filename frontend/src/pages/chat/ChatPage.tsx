import { useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Chip } from "@/components/ui/chip";
import { ChatListItem } from "@/components/custom/chat/ChatListItem";
import { ChatRoom } from "@/components/custom/chat/ChatRoom";
import { CHATS } from "@/data/chat";
import styles from "@/pages/chat/ChatPage.module.css";
import { classes } from "@/utils/classes";
import { ChipScroller } from "@/components/ui/chip-scroller";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, Loading, Skeleton } from "@/components/ui/state";

const c = classes(styles);

type ChatFilter = "all" | "borrowed" | "lent" | "unread";

const unreadOf = (role: "borrowed" | "lent") =>
  CHATS.filter((c) => c.role === role).reduce((sum, c) => sum + c.unread, 0);

export function ChatPage() {
  const { chatId } = useParams();
  const selectedId = chatId ?? CHATS[0].id;
  const [filter, setFilter] = useState<ChatFilter>("all");
  const [params] = useSearchParams();
  /* 프로토타입 분리: 목록 상태(loading·empty·listError) / 방 상태(error=전송 실패 등) */
  const raw = params.get("state");
  const state = raw === "loading" || raw === "empty" ? raw : raw === "listError" ? "error" : "default";
  const navigate = useNavigate();

  const rows = CHATS.filter((c) =>
    filter === "all" ? true : filter === "unread" ? c.unread > 0 : c.role === filter,
  );

  const list = (
    <section className={c("chat-list")} aria-label="채팅 목록">
      <div className={c("chat-list-head")}>
        <h1 className={c("t-title chat-list-title only-desk")}>채팅</h1>
        {state === "loading" || state === "empty" ? null : (
        <ChipScroller label="채팅 필터">
          <Chip selected={filter === "all"} onClick={() => setFilter("all")}>전체</Chip>
          <Chip count={unreadOf("borrowed")} selected={filter === "borrowed"} onClick={() => setFilter("borrowed")}>
            빌린 물건
          </Chip>
          <Chip count={unreadOf("lent")} selected={filter === "lent"} onClick={() => setFilter("lent")}>
            빌려준 물건
          </Chip>
          <Chip selected={filter === "unread"} onClick={() => setFilter("unread")}>안 읽음</Chip>
        </ChipScroller>
        )}
      </div>
      <div className={c("chat-list-rows")}>
        {state === "loading" ? (
          <Loading label="채팅 목록 불러오는 중">
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className={c("chat-list-skel")}>
                <Skeleton w={44} h={44} r={999} />
                <div className={c("chat-list-skel-lines")}>
                  <Skeleton w="40%" />
                  <Skeleton w="75%" />
                </div>
              </div>
            ))}
          </Loading>
        ) : state === "error" ? (
          <ErrorState title="채팅 목록을 불러오지 못했어요" />
        ) : state === "empty" || rows.length === 0 ? (
          <EmptyState
            icon="chat"
            title="아직 채팅이 없어요"
            actions={<Button icon="search" onClick={() => navigate("/search")}>물건 찾아보기</Button>}
          >
            물건 상세에서 ‘채팅으로 물어보기’를 누르면 빌리기 전에 궁금한 걸 물어볼 수 있어요.
          </EmptyState>
        ) : (
          rows.map((chat) => <ChatListItem key={chat.id} chat={chat} selected={chat.id === selectedId} />)
        )}
      </div>
    </section>
  );

  return (
    <div className={c(`chat-page${chatId ? " chat-page--room" : ""}`)}>
      <div className={c(chatId ? "only-desk chat-page-list" : "chat-page-list")}>{list}</div>
      <div className={c(chatId ? "chat-page-room" : "only-desk chat-page-room")}>
        <ChatRoom />
      </div>
    </div>
  );
}
