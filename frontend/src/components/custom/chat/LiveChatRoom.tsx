import { useCallback, useEffect, useRef, useState } from "react";
import { getChatMessages, getChatRoom, sendChatMessage, updateChatReadPosition } from "@/api/chat";
import { newIdempotencyKey } from "@/api/client";
import { reportMessage, type ReportReasonCode } from "@/api/reports";
import { useUi } from "@/components/overlay/UiContext";
import { Alert } from "@/components/ui/alert";
import { Avatar } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/state";
import { Bubble, DateDivider, SystemMessage } from "@/components/custom/chat/Bubble";
import { Composer } from "@/components/custom/chat/Composer";
import { ContextBar } from "@/components/custom/chat/ContextBar";
import { refreshChatUnreadCount } from "@/stores/chat-unread";
import type { ApiChatMessage, ApiChatMessagePage, ApiChatRoom } from "@/types/chat-api";
import { chatBlockedMessage, chatTimeLabel, isMoneyRequest } from "@/utils/chat-view";
import { mergeChatMessages } from "@/utils/chat-state";
import { chatSocketUrl, parseChatSocketMessage } from "@/utils/chat-realtime";
import { classes } from "@/utils/classes";
import styles from "@/components/custom/chat/ChatRoom.module.css";

const c = classes(styles);
const reportReasons: Record<string, ReportReasonCode> = {
  "돈이나 보증금을 요구했어요": "MONEY_REQUEST",
  "물건 상태가 설명과 크게 달라요": "ITEM_MISMATCH",
  "약속을 반복해서 어겨요": "NO_SHOW",
  "무례하거나 위협적인 언행을 했어요": "ABUSE",
  "사기가 의심돼요": "FRAUD",
};

type Pending = { clientMessageId: string; body: string; rentalId?: string; failed: boolean };

export function LiveChatRoom({ roomId, onRead }: { roomId: string; onRead: (roomId: string) => void }) {
  const { report, toast } = useUi();
  const [room, setRoom] = useState<ApiChatRoom | null>(null);
  const [messages, setMessages] = useState<ApiChatMessage[]>([]);
  const [pending, setPending] = useState<Pending[]>([]);
  const [older, setOlder] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [otherLastRead, setOtherLastRead] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [retry, setRetry] = useState(0);
  const [foregroundEpoch, setForegroundEpoch] = useState(0);
  const [selectedRentalId, setSelectedRentalId] = useState<string>();
  const [dismissedWarnings, setDismissedWarnings] = useState<Set<string>>(() => new Set());
  const lastReadRef = useRef(0);
  const highestSequence = messages.at(-1)?.sequence ?? 0;
  const lastFetchedSequenceRef = useRef(0);

  const receiveMessages = useCallback((incoming: ApiChatMessage[]) => {
    setMessages((previous) => mergeChatMessages(previous, incoming));
    const confirmedIds = new Set(incoming
      .map((message) => message.clientMessageId)
      .filter((id): id is string => typeof id === "string"));
    if (confirmedIds.size > 0) {
      setPending((previous) => previous.filter((entry) => !confirmedIds.has(entry.clientMessageId)));
    }
  }, []);

  useEffect(() => {
    let active = true;
    Promise.all([getChatRoom(roomId), getChatMessages(roomId)]).then(([detail, page]) => {
      if (!active) return;
      setRoom(detail);
      setSelectedRentalId(detail.access.allowedRentalIds[0] ?? detail.rentals[0]?.id);
      receiveMessages(page.items);
      lastFetchedSequenceRef.current = Math.max(0, ...page.items.map((item) => item.sequence));
      setOtherLastRead(page.otherLastReadSequence);
      setOlder(page.hasMore);
      setError(false);
    }).catch(() => {
      if (active) setError(true);
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [roomId, retry, receiveMessages]);

  useEffect(() => {
    if (loading || error) return;
    let active = true;
    let busy = false;
    let socket: WebSocket | null = null;
    let reconnectTimer: number | undefined;
    const poll = async () => {
      if (busy || !active || document.hidden) return;
      busy = true;
      try {
        let cursor = lastFetchedSequenceRef.current;
        let page: ApiChatMessagePage;
        do {
          page = await getChatMessages(roomId, { afterSequence: cursor });
          if (!active) return;
          receiveMessages(page.items);
          setOtherLastRead(page.otherLastReadSequence);
          const nextCursor = page.nextAfterSequence ?? page.items.at(-1)?.sequence ?? cursor;
          if (nextCursor <= cursor) break;
          cursor = nextCursor;
          lastFetchedSequenceRef.current = cursor;
        } while (page.hasMore && page.items.length > 0);
      } catch {
        // 연결 복구 뒤 같은 커서에서 다시 조회한다.
      } finally {
        busy = false;
      }
    };
    const connect = () => {
      if (!active) return;
      try {
        const current = new WebSocket(chatSocketUrl(roomId, window.location.href));
        socket = current;
        current.onopen = () => { void poll(); };
        current.onmessage = (event) => {
          const message = parseChatSocketMessage(event.data, roomId);
          if (message && active) {
            receiveMessages([message]);
          }
        };
        current.onerror = () => current.close();
        current.onclose = () => {
          if (active) reconnectTimer = window.setTimeout(connect, 3000);
        };
      } catch {
        reconnectTimer = window.setTimeout(connect, 3000);
      }
    };
    connect();
    const timer = window.setInterval(() => { void poll(); }, 3000);
    const onFocus = () => {
      if (document.hidden) return;
      setForegroundEpoch((previous) => previous + 1);
      void poll();
    };
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onFocus);
    return () => {
      active = false;
      window.clearInterval(timer);
      window.clearTimeout(reconnectTimer);
      socket?.close();
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onFocus);
    };
  }, [roomId, loading, error, receiveMessages]);

  useEffect(() => {
    if (!room || highestSequence <= lastReadRef.current || document.hidden) return;
    const sequence = highestSequence;
    lastReadRef.current = sequence;
    updateChatReadPosition(roomId, sequence).then(() => {
      onRead(roomId);
      void refreshChatUnreadCount();
    }).catch(() => {
      lastReadRef.current = Math.min(lastReadRef.current, sequence - 1);
    });
  }, [room, roomId, highestSequence, onRead, foregroundEpoch]);

  const loadOlder = async () => {
    const first = messages[0]?.sequence;
    if (!first || loadingOlder) return;
    setLoadingOlder(true);
    try {
      const page = await getChatMessages(roomId, { beforeSequence: first });
      receiveMessages(page.items);
      setOtherLastRead(page.otherLastReadSequence);
      setOlder(page.hasMore);
    } catch {
      // 버튼을 남겨 같은 커서로 다시 시도한다.
    } finally {
      setLoadingOlder(false);
    }
  };

  const send = async (entry: Pending) => {
    setPending((previous) => previous.map((item) => item.clientMessageId === entry.clientMessageId ? { ...item, failed: false } : item));
    try {
      const saved = await sendChatMessage(roomId, {
        clientMessageId: entry.clientMessageId,
        body: entry.body,
        rentalId: entry.rentalId,
      });
      receiveMessages([saved]);
      setPending((previous) => previous.filter((item) => item.clientMessageId !== entry.clientMessageId));
    } catch {
      setPending((previous) => previous.map((item) => item.clientMessageId === entry.clientMessageId ? { ...item, failed: true } : item));
    }
  };

  const createMessage = (body: string) => {
    const entry = { clientMessageId: newIdempotencyKey(), body, rentalId: selectedRentalId, failed: false };
    setPending((previous) => [...previous, entry]);
    void send(entry);
  };

  const openReport = (messageId: string, name: string) => report({
    name,
    onConfirm: (reason) => {
      const reasonCode = reportReasons[reason] ?? "OTHER";
      void reportMessage(messageId, reasonCode, reasonCode === "OTHER" ? reason : undefined)
        .then(() => toast("신고를 접수했어요"))
        .catch(() => toast("신고를 접수하지 못했어요. 다시 시도해 주세요."));
    },
  });

  if (loading) return <section className={c("chat-room")} aria-label="대화 불러오는 중"><div className={c("chat-room-scroll")}>
    <Skeleton w="60%" h={40} r={16} /><Skeleton w="45%" h={40} r={16} />
  </div></section>;
  if (error || !room) return <section className={c("chat-room")}><ErrorState title="대화를 불러오지 못했어요" onRetry={() => {
    setLoading(true);
    setRetry((value) => value + 1);
  }} /></section>;

  const other = room.otherMember.displayName;
  const allowedRentals = room.rentals.filter((rental) => room.access.allowedRentalIds.includes(rental.id));

  return (
    <section className={c("chat-room")} aria-label={`${other} 님과의 채팅`}>
      <div className={c("chat-room-head only-desk")}>
        <Avatar name={other} size={36} />
        <div className={c("chat-room-head-body")}><div className={c("t-label")}>{other}</div></div>
      </div>
      <ContextBar room={room} selectedRentalId={selectedRentalId} />
      {allowedRentals.length > 1 ? <label className={c("chat-rental-picker")}>
        대여 건 <select value={selectedRentalId} onChange={(event) => setSelectedRentalId(event.target.value)}>
          {allowedRentals.map((rental) => <option key={rental.id} value={rental.id}>
            {rental.startDate} – {rental.endDate}
          </option>)}
        </select>
      </label> : null}
      <div className={c("chat-room-scroll")} role="log" aria-live="polite" aria-label={`${other} 님과의 대화`}>
        {older ? <Button size="sm" variant="secondary" disabled={loadingOlder} onClick={() => void loadOlder()}>
          {loadingOlder ? "불러오는 중" : "이전 대화 보기"}
        </Button> : null}
        {messages.length === 0 && pending.length === 0 ? <EmptyState icon="chat" title="대화를 시작해 보세요" /> : null}
        {messages.map((message, index) => {
          const date = new Date(message.createdAt).toDateString();
          const divider = index === 0 || date !== new Date(messages[index - 1].createdAt).toDateString();
          return <div key={message.id} className={c("chat-message-group")}>
            {divider ? <DateDivider>{new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric", weekday: "long" }).format(new Date(message.createdAt))}</DateDivider> : null}
            {message.type === "SYSTEM" ? <SystemMessage icon="ring">{message.system?.label ?? message.body ?? "거래 상태가 변경됐어요"}</SystemMessage> : <>
              <Bubble me={message.senderId !== room.otherMember.id} time={chatTimeLabel(message.createdAt)}
                read={message.senderId !== room.otherMember.id && message.sequence <= otherLastRead}>{message.body ?? ""}</Bubble>
              {message.senderId === room.otherMember.id && isMoneyRequest(message.body) && !dismissedWarnings.has(message.id) ?
                <Alert tone="danger" title="돈이나 보증금을 보내지 마세요" actions={<>
                  <Button size="sm" variant="secondary" icon="flag" onClick={() => openReport(message.id, other)}>신고하기</Button>
                  <Button size="sm" variant="ghost" onClick={() => setDismissedWarnings((previous) => new Set(previous).add(message.id))}>괜찮아요</Button>
                </>}>
                  BILLIM의 대여는 모두 무료예요. 입금·보증금 요청은 커뮤니티 규칙 위반이에요.
                </Alert> : null}
            </>}
          </div>;
        })}
        {pending.map((entry) => <div key={entry.clientMessageId}>
          <Bubble me failed={entry.failed} time={entry.failed ? undefined : "보내는 중"}>{entry.body}</Bubble>
          {entry.failed ? <div className={c("chat-retry")}>
            <Button size="sm" variant="secondary" icon="refresh" onClick={() => void send(entry)}>다시 보내기</Button>
            <Button size="sm" variant="ghost" onClick={() => setPending((previous) => previous.filter((item) => item.clientMessageId !== entry.clientMessageId))}>삭제</Button>
          </div> : null}
        </div>)}
      </div>
      <Composer blocked={!room.access.canSend} blockedMessage={chatBlockedMessage(room.access.reason)} onSend={createMessage} />
    </section>
  );
}
