import { useCallback, useEffect, useRef, useState } from "react";
import { getChatMessages, getChatRoom, issueChatSocketTicket, sendChatMessage, updateChatReadPosition } from "@/api/chat";
import { acceptAppointment, getAppointments, proposeAppointment, withdrawAppointment } from "@/api/appointments";
import { getCommunityPlaces, type ApiCommunityPlace } from "@/api/communities";
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
import { AppointmentCard } from "@/components/custom/chat/AppointmentCard";
import { AppointmentSheet } from "@/components/custom/chat/AppointmentSheet";
import { refreshChatUnreadCount } from "@/stores/chat-unread";
import { useSession } from "@/stores/session";
import type { ApiChatMessage, ApiChatMessagePage, ApiChatRoom } from "@/types/chat-api";
import type { ApiAppointmentSlot, AppointmentKind } from "@/types/appointment-api";
import { ApiError } from "@/api/client";
import { appointmentView } from "@/utils/appointment-view";
import { chatBlockedMessage, chatTimeLabel, isMoneyRequest } from "@/utils/chat-view";
import { mergeChatMessages } from "@/utils/chat-state";
import { readPendingChatMessages, writePendingChatMessages, type PendingChatMessage } from "@/utils/chat-pending";
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

export function LiveChatRoom({ roomId, onRead, onMessage }: {
  roomId: string;
  onRead: (roomId: string) => void;
  onMessage: (roomId: string, message: ApiChatMessage) => void;
}) {
  const { report, toast } = useUi();
  const { memberId } = useSession();
  const [room, setRoom] = useState<ApiChatRoom | null>(null);
  const [messages, setMessages] = useState<ApiChatMessage[]>([]);
  const [pending, setPending] = useState<PendingChatMessage[]>(() => readPendingChatMessages(memberId, roomId));
  const [connectionState, setConnectionState] = useState<"connecting" | "connected" | "reconnecting" | "unauthorized">("connecting");
  const [older, setOlder] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [otherLastRead, setOtherLastRead] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [retry, setRetry] = useState(0);
  const [foregroundEpoch, setForegroundEpoch] = useState(0);
  const [selectedRentalId, setSelectedRentalId] = useState<string>();
  const [slots, setSlots] = useState<ApiAppointmentSlot[]>([]);
  const [appointmentLoading, setAppointmentLoading] = useState(false);
  const [appointmentError, setAppointmentError] = useState(false);
  const [appointmentRevision, setAppointmentRevision] = useState(0);
  const [appointmentBusy, setAppointmentBusy] = useState(false);
  const [places, setPlaces] = useState<ApiCommunityPlace[]>([]);
  const [sheetKind, setSheetKind] = useState<"give" | "back">();
  const [dismissedWarnings, setDismissedWarnings] = useState<Set<string>>(() => new Set());
  const lastReadRef = useRef(0);
  const highestSequence = messages.at(-1)?.sequence ?? 0;
  const lastFetchedSequenceRef = useRef(0);

  useEffect(() => {
    writePendingChatMessages(memberId, roomId, pending);
  }, [memberId, roomId, pending]);

  const receiveMessages = useCallback((incoming: ApiChatMessage[]) => {
    setMessages((previous) => mergeChatMessages(previous, incoming));
    if (incoming.length > 0) {
      const latest = incoming.reduce((current, message) => message.sequence > current.sequence ? message : current);
      onMessage(roomId, latest);
    }
    const confirmedIds = new Set(incoming
      .map((message) => message.clientMessageId)
      .filter((id): id is string => typeof id === "string"));
    if (confirmedIds.size > 0) {
      setPending((previous) => previous.filter((entry) => !confirmedIds.has(entry.clientMessageId)));
    }
  }, [onMessage, roomId]);

  useEffect(() => {
    let active = true;
    Promise.all([getChatRoom(roomId), getChatMessages(roomId)]).then(([detail, page]) => {
      if (!active) return;
      setRoom(detail);
      const rentalId = detail.access.allowedRentalIds[0] ?? detail.rentals[0]?.id;
      setSelectedRentalId(rentalId);
      setAppointmentLoading(Boolean(rentalId));
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
    if (!selectedRentalId) return;
    let active = true;
    getAppointments(selectedRentalId).then((items) => {
      if (active) { setSlots(items); setAppointmentError(false); }
    }).catch(() => {
      if (active) setAppointmentError(true);
    }).finally(() => {
      if (active) setAppointmentLoading(false);
    });
    return () => { active = false; };
  }, [selectedRentalId, appointmentRevision]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (!document.hidden && selectedRentalId) setAppointmentRevision((value) => value + 1);
    }, 30000);
    return () => window.clearInterval(timer);
  }, [selectedRentalId]);

  useEffect(() => {
    if (loading || error) return;
    let active = true;
    let busy = false;
    let socket: WebSocket | null = null;
    let reconnectTimer: number | undefined;
    let pollTimer: number | undefined;
    let reconnectDelay = 1000;
    let unauthorized = false;
    const poll = async () => {
      if (busy || !active || unauthorized || document.hidden) return;
      busy = true;
      try {
        let cursor = lastFetchedSequenceRef.current;
        let page: ApiChatMessagePage;
        do {
          page = await getChatMessages(roomId, { afterSequence: cursor });
          if (!active) return;
          receiveMessages(page.items);
          if (page.items.some((item) => item.type === "SYSTEM")) setAppointmentRevision((value) => value + 1);
          setOtherLastRead(page.otherLastReadSequence);
          const nextCursor = page.nextAfterSequence ?? page.items.at(-1)?.sequence ?? cursor;
          if (nextCursor <= cursor) break;
          cursor = nextCursor;
          lastFetchedSequenceRef.current = cursor;
        } while (page.hasMore && page.items.length > 0);
      } catch (exception) {
        if (exception instanceof ApiError && exception.unauthenticated) {
          unauthorized = true;
          setConnectionState("unauthorized");
          socket?.close();
        }
        // 그 밖의 실패는 연결 복구 뒤 같은 커서에서 다시 조회한다.
      } finally {
        busy = false;
      }
    };
    const schedulePoll = () => {
      window.clearTimeout(pollTimer);
      if (!active || unauthorized) return;
      pollTimer = window.setTimeout(() => {
        void poll();
        schedulePoll();
      }, socket?.readyState === WebSocket.OPEN ? 15000 : 3000);
    };
    const connect = async () => {
      if (!active || unauthorized) return;
      try {
        const endpoint = import.meta.env.VITE_CHAT_WS_URL?.trim();
        // 연결마다 새 연결권 발급. 원문은 메모리에서만 사용.
        let url = chatSocketUrl(roomId, window.location.href, endpoint);
        if (endpoint) {
          const { ticket } = await issueChatSocketTicket(roomId);
          if (!active || unauthorized) return;
          url = chatSocketUrl(roomId, window.location.href, endpoint, ticket);
        }
        const current = new WebSocket(url);
        socket = current;
        current.onopen = () => {
          reconnectDelay = 1000;
          setConnectionState("connected");
          void poll();
          schedulePoll();
        };
        current.onmessage = (event) => {
          const message = parseChatSocketMessage(event.data, roomId);
          if (message && active) {
            receiveMessages([message]);
            if (message.type === "SYSTEM") setAppointmentRevision((value) => value + 1);
          }
        };
        current.onerror = () => current.close();
        current.onclose = () => {
          if (!active || unauthorized) return;
          setConnectionState("reconnecting");
          schedulePoll();
          reconnectTimer = window.setTimeout(() => void connect(), reconnectDelay);
          reconnectDelay = Math.min(reconnectDelay * 2, 30000);
        };
      } catch (exception) {
        if (!active || unauthorized) return;
        if (exception instanceof ApiError && exception.unauthenticated) {
          unauthorized = true;
          setConnectionState("unauthorized");
          return;
        }
        setConnectionState("reconnecting");
        schedulePoll();
        reconnectTimer = window.setTimeout(() => void connect(), reconnectDelay);
        reconnectDelay = Math.min(reconnectDelay * 2, 30000);
      }
    };
    void connect();
    schedulePoll();
    const onFocus = () => {
      if (document.hidden) return;
      setForegroundEpoch((previous) => previous + 1);
      void poll();
    };
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onFocus);
    return () => {
      active = false;
      window.clearTimeout(pollTimer);
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

  const send = async (entry: PendingChatMessage) => {
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
    writePendingChatMessages(memberId, roomId, [...pending, entry]);
    setPending((previous) => [...previous, entry]);
    void send(entry);
  };

  const openAppointmentSheet = async (kind: "give" | "back") => {
    const slot = slots.find((entry) => entry.rentalId === selectedRentalId && entry.kind === (kind === "give" ? "PICKUP" : "RETURN"));
    if (!room || !slot?.allowedActions.includes("PROPOSE")) return;
    setAppointmentBusy(true);
    try {
      const options = await getCommunityPlaces(room.communityId);
      if (options.length === 0) {
        toast("선택할 수 있는 공용 장소가 없어요.");
        return;
      }
      setPlaces(options);
      setSheetKind(kind);
    } catch {
      toast("거래 장소를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setAppointmentBusy(false);
    }
  };

  const appointmentFailed = (error: unknown) => {
    if (error instanceof ApiError && error.status === 409) {
      setAppointmentRevision((value) => value + 1);
      toast("약속이 변경됐어요. 다시 확인해 주세요.");
    } else {
      toast(error instanceof ApiError ? error.message : "약속을 처리하지 못했어요. 다시 시도해 주세요.");
    }
  };

  const updateAppointment = async (slot: ApiAppointmentSlot, action: "ACCEPT" | "WITHDRAW") => {
    const proposal = slot.pendingProposal;
    if (!proposal || !slot.allowedActions.includes(action) || appointmentBusy) return;
    setAppointmentBusy(true);
    try {
      const updated = action === "ACCEPT"
        ? await acceptAppointment(proposal.id, slot.version)
        : await withdrawAppointment(proposal.id, slot.version);
      setSlots((previous) => previous.map((entry) => entry.id === updated.id ? updated : entry));
      setAppointmentRevision((value) => value + 1);
      toast(action === "ACCEPT" ? "약속을 수락했어요." : "제안을 철회했어요.");
    } catch (error) {
      appointmentFailed(error);
    } finally {
      setAppointmentBusy(false);
    }
  };

  const submitAppointment = async (kind: "give" | "back", scheduledAt: string, placeId: string) => {
    const slotKind: AppointmentKind = kind === "give" ? "PICKUP" : "RETURN";
    const slot = slots.find((entry) => entry.rentalId === selectedRentalId && entry.kind === slotKind);
    if (!selectedRentalId || !slot?.allowedActions.includes("PROPOSE")) return false;
    try {
      const updated = await proposeAppointment(selectedRentalId, slotKind, {
        expectedVersion: slot.version, scheduledAt, placeId,
      });
      setSlots((previous) => previous.map((entry) => entry.id === updated.id ? updated : entry));
      setAppointmentRevision((value) => value + 1);
      toast("약속을 보냈어요.");
      return true;
    } catch (error) {
      appointmentFailed(error);
      return false;
    }
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
  const selectedRental = room.rentals.find((entry) => entry.id === selectedRentalId);
  const visibleSlots = slots.filter((slot) => slot.rentalId === selectedRentalId);
  const proposalKinds = visibleSlots.filter((slot) => slot.allowedActions.includes("PROPOSE"));

  return (
    <section className={c("chat-room")} aria-label={`${other} 님과의 채팅`}>
      <div className={c("chat-room-head only-desk")}>
        <Avatar name={other} size={36} />
        <div className={c("chat-room-head-body")}><div className={c("t-label")}>{other}</div></div>
      </div>
      <ContextBar room={room} selectedRentalId={selectedRentalId} action={proposalKinds.length > 0 ?
        <Button size="sm" variant="secondary" icon="clock" disabled={appointmentBusy}
          onClick={() => void openAppointmentSheet(proposalKinds[0].kind === "PICKUP" ? "give" : "back")}>약속 잡기</Button> : null} />
      {connectionState === "reconnecting" ? <Alert tone="warning" title="실시간 연결을 다시 시도하고 있어요">
        새 메시지는 주기적으로 확인하고 있어요. 일시적으로 늦게 표시될 수 있어요.
      </Alert> : null}
      {connectionState === "unauthorized" ? <Alert tone="warning" title="로그인이 만료됐어요">
        다시 로그인한 뒤 채팅을 이용해 주세요.
      </Alert> : null}
      {allowedRentals.length > 1 ? <label className={c("chat-rental-picker")}>
        대여 건 <select value={selectedRentalId} onChange={(event) => {
          setAppointmentLoading(true);
          setSelectedRentalId(event.target.value);
        }}>
          {allowedRentals.map((rental) => <option key={rental.id} value={rental.id}>
            {rental.startDate} – {rental.endDate}
          </option>)}
        </select>
      </label> : null}
      <div className={c("chat-room-scroll")} role="log" aria-live="polite" aria-label={`${other} 님과의 대화`}>
        {older ? <Button size="sm" variant="secondary" disabled={loadingOlder} onClick={() => void loadOlder()}>
          {loadingOlder ? "불러오는 중" : "이전 대화 보기"}
        </Button> : null}
        {messages.length === 0 && pending.length === 0 && visibleSlots.every((slot) => !slot.pendingProposal && !slot.confirmedProposal) ?
          <EmptyState icon="chat" title="대화를 시작해 보세요" /> : null}
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
        {appointmentLoading && visibleSlots.length === 0 ? <Skeleton w="70%" h={120} r={16} /> : null}
        {appointmentError ? <ErrorState title="약속을 불러오지 못했어요" onRetry={() => setAppointmentRevision((value) => value + 1)} /> : null}
        {!appointmentError ? visibleSlots.flatMap((slot) => {
          const cards = [];
          if (slot.confirmedProposal) cards.push(<AppointmentCard key={`${slot.id}-confirmed`}
            appt={appointmentView(slot.confirmedProposal)} status="confirmed" other={other}
            onRequestChange={slot.allowedActions.includes("PROPOSE") && !appointmentBusy
              ? () => void openAppointmentSheet(slot.kind === "PICKUP" ? "give" : "back") : undefined} />);
          if (slot.pendingProposal) cards.push(<AppointmentCard key={`${slot.id}-pending`}
            appt={appointmentView(slot.pendingProposal)} status={slot.confirmedProposal ? "change" : "proposed"}
            mine={slot.pendingProposal.proposerId !== room.otherMember.id} other={other}
            onAccept={slot.allowedActions.includes("ACCEPT") && !appointmentBusy ? () => void updateAppointment(slot, "ACCEPT") : undefined}
            onWithdraw={slot.allowedActions.includes("WITHDRAW") && !appointmentBusy ? () => void updateAppointment(slot, "WITHDRAW") : undefined}
            onChangeTime={slot.allowedActions.includes("PROPOSE") && !appointmentBusy
              ? () => void openAppointmentSheet(slot.kind === "PICKUP" ? "give" : "back") : undefined} />);
          return cards;
        }) : null}
        {pending.map((entry) => <div key={entry.clientMessageId}>
          <Bubble me failed={entry.failed} time={entry.failed ? undefined : "보내는 중"}>{entry.body}</Bubble>
          {entry.failed ? <div className={c("chat-retry")}>
            <Button size="sm" variant="secondary" icon="refresh" onClick={() => void send(entry)}>다시 보내기</Button>
            <Button size="sm" variant="ghost" onClick={() => setPending((previous) => previous.filter((item) => item.clientMessageId !== entry.clientMessageId))}>삭제</Button>
          </div> : null}
        </div>)}
      </div>
      <Composer blocked={!room.access.canSend || connectionState === "unauthorized"}
        blockedMessage={connectionState === "unauthorized" ? "로그인이 만료됐어요. 다시 로그인해 주세요." : chatBlockedMessage(room.access.reason)}
        onSend={createMessage} />
      {sheetKind && selectedRental ? <AppointmentSheet key={`${selectedRental.id}-${sheetKind}`}
        initialKind={sheetKind} onClose={() => setSheetKind(undefined)} live={{
          dates: { give: selectedRental.startDate, back: selectedRental.endDate },
          places,
          kinds: proposalKinds.map((slot) => slot.kind === "PICKUP" ? "give" : "back"),
          onSubmit: submitAppointment,
        }} /> : null}
    </section>
  );
}
