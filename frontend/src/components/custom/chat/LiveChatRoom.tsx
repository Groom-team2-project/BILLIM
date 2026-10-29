import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getChatMessages, getChatRoom, issueChatSocketTicket, sendChatMessage, updateChatReadPosition } from "@/api/chat";
import { acceptAppointment, getAppointments, proposeAppointment, withdrawAppointment } from "@/api/appointments";
import { commandRental, getRental } from "@/api/rentals";
import { getCommunityPlaces, type ApiCommunityPlace } from "@/api/communities";
import { newIdempotencyKey } from "@/api/client";
import { createReport, reportMessage, type ReportReasonCode } from "@/api/reports";
import { blockMember } from "@/api/blocks";
import { useUi } from "@/components/overlay/UiContext";
import { Alert } from "@/components/ui/alert";
import { Avatar } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { MoreMenu } from "@/components/ui/more-menu";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/state";
import { Bubble, DateDivider, SystemMessage } from "@/components/custom/chat/Bubble";
import { Composer } from "@/components/custom/chat/Composer";
import { ContextBar } from "@/components/custom/chat/ContextBar";
import { STATUS_UI } from "@/components/custom/rentals/status";
import { AppointmentCard } from "@/components/custom/chat/AppointmentCard";
import { AppointmentSheet } from "@/components/custom/chat/AppointmentSheet";
import { StatusBadge } from "@/components/custom/rentals/StatusBadge";
import { refreshChatUnreadCount } from "@/stores/chat-unread";
import { useSession } from "@/stores/session";
import type { ApiChatMessage, ApiChatMessagePage, ApiChatRoom } from "@/types/chat-api";
import type { ApiAppointmentSlot, AppointmentKind } from "@/types/appointment-api";
import { ApiError } from "@/api/client";
import { appointmentView } from "@/utils/appointment-view";
import { chatBlockedMessage, chatDateRange, chatRentalResponse, chatRentalStatus, chatTimeLabel, chatTimeline, isMoneyRequest, selectableChatRentals } from "@/utils/chat-view";
import { countNewChatMessages, isChatAtBottom, isChatMessageEndVisible, mergeChatMessages } from "@/utils/chat-state";
import { readPendingChatMessages, writePendingChatMessages, type PendingChatMessage } from "@/utils/chat-pending";
import { chatSocketUrl, parseChatSocketMessage } from "@/utils/chat-realtime";
import { classes } from "@/utils/classes";
import { toReasonCode, toReasonDetail } from "@/utils/reportReason";
import styles from "@/components/custom/chat/ChatRoom.module.css";

const c = classes(styles);
const reportReasons: Record<string, ReportReasonCode> = {
  "돈이나 보증금을 요구했어요": "MONEY_REQUEST",
  "물건 상태가 설명과 크게 달라요": "ITEM_MISMATCH",
  "약속을 반복해서 어겨요": "NO_SHOW",
  "무례하거나 위협적인 언행을 했어요": "ABUSE",
  "사기가 의심돼요": "FRAUD",
};

export function LiveChatRoom({ roomId, onRead, onMessage, onAppointmentChanged, targetRentalId }: {
  roomId: string;
  onRead: (roomId: string) => void;
  onMessage: (roomId: string, message: ApiChatMessage) => void;
  onAppointmentChanged: (room: ApiChatRoom) => void;
  targetRentalId?: string;
}) {
  const { confirm, reason, report, toast } = useUi();
  const navigate = useNavigate();
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
  const [rentalActionBusy, setRentalActionBusy] = useState(false);
  const [places, setPlaces] = useState<ApiCommunityPlace[]>([]);
  const [sheetKind, setSheetKind] = useState<"give" | "back">();
  const [dismissedWarnings, setDismissedWarnings] = useState<Set<string>>(() => new Set());
  const lastReadRef = useRef(0);
  const highestSequence = messages.at(-1)?.sequence ?? 0;
  const lastFetchedSequenceRef = useRef(0);
  const scrollRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const followLatestRef = useRef(true);
  const manualRentalSelectionRef = useRef(false);
  const rentalActionInFlightRef = useRef(false);
  const olderAnchorRef = useRef<{ id: string; bottom: number } | null>(null);
  const [atBottom, setAtBottom] = useState(true);
  const [seenSequence, setSeenSequence] = useState(0);

  const recordVisibleMessages = useCallback(() => {
    const element = scrollRef.current;
    if (!element || document.hidden) return;
    const bounds = element.getBoundingClientRect();
    const viewport = {
      top: Math.max(0, bounds.top + element.clientTop),
      bottom: Math.min(window.innerHeight, bounds.top + element.clientTop + element.clientHeight),
      left: Math.max(0, bounds.left + element.clientLeft),
      right: Math.min(window.innerWidth, bounds.left + element.clientLeft + element.clientWidth),
    };
    let visibleSequence = 0;
    element.querySelectorAll<HTMLElement>("[data-message-sequence]").forEach((message) => {
      if (isChatMessageEndVisible(viewport, message.getBoundingClientRect())) {
        visibleSequence = Math.max(visibleSequence, Number(message.dataset.messageSequence));
      }
    });
    setSeenSequence((previous) => Math.max(previous, visibleSequence));
  }, []);

  const scrollToLatestMessage = useCallback(() => {
    const element = scrollRef.current;
    if (!element) return;
    // 최신 메시지 아래의 약속 카드·전송 대기 영역까지 보이도록 실제 끝으로 이동.
    element.scrollTop = element.scrollHeight;
  }, []);

  const jumpToLatest = () => {
    followLatestRef.current = true;
    scrollToLatestMessage();
    setAtBottom(true);
    recordVisibleMessages();
  };

  const onScroll = () => {
    const element = scrollRef.current;
    if (!element) return;
    const entries = element.querySelectorAll<HTMLElement>("[data-message-sequence]");
    const latest = entries.item(entries.length - 1);
    const bottom = isChatAtBottom(element) || Boolean(latest
      && isChatMessageEndVisible(element.getBoundingClientRect(), latest.getBoundingClientRect()));
    followLatestRef.current = bottom;
    setAtBottom(bottom);
    recordVisibleMessages();
  };

  useLayoutEffect(() => {
    const element = scrollRef.current;
    if (!element) return;
    const anchor = olderAnchorRef.current;
    if (anchor) {
      const message = Array.from(element.querySelectorAll<HTMLElement>("[data-message-id]"))
        .find((entry) => entry.dataset.messageId === anchor.id);
      if (message) element.scrollTop += message.getBoundingClientRect().bottom
        - element.getBoundingClientRect().top - anchor.bottom;
      olderAnchorRef.current = null;
    } else if (followLatestRef.current) {
      scrollToLatestMessage();
    }
    recordVisibleMessages();
  }, [messages, pending, loading, error, atBottom, highestSequence, foregroundEpoch, recordVisibleMessages, scrollToLatestMessage]);

  useEffect(() => {
    const element = scrollRef.current;
    const content = contentRef.current;
    if (!element || !content) return;
    // 약속 카드·줄바꿈·화면 크기 변경 후에도 하단 고정 유지.
    const observer = new ResizeObserver(() => {
      if (followLatestRef.current) scrollToLatestMessage();
      recordVisibleMessages();
    });
    observer.observe(element);
    observer.observe(content);
    return () => observer.disconnect();
  }, [loading, error, recordVisibleMessages, scrollToLatestMessage]);

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
      const targetAllowed = Boolean(targetRentalId && detail.access.allowedRentalIds.includes(targetRentalId));
      const rentalId = targetAllowed ? targetRentalId : selectableChatRentals(detail)[0]?.id;
      manualRentalSelectionRef.current = targetAllowed;
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
  }, [roomId, retry, receiveMessages, targetRentalId]);

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
    if (appointmentRevision === 0) return;
    let active = true;
    getChatRoom(roomId).then((detail) => {
      if (!active) return;
      setRoom(detail);
      const selectableRentals = selectableChatRentals(detail);
      setSelectedRentalId((current) => manualRentalSelectionRef.current
        && selectableRentals.some((rental) => rental.id === current)
        ? current : selectableRentals[0]?.id);
    }).catch(() => {
      // 다음 시스템 메시지·주기 조회에서 다시 갱신한다.
    });
    return () => { active = false; };
  }, [roomId, appointmentRevision]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (!document.hidden) setAppointmentRevision((value) => value + 1);
    }, 30000);
    return () => window.clearInterval(timer);
  }, []);

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
    if (!room || seenSequence <= lastReadRef.current || document.hidden) return;
    const sequence = seenSequence;
    lastReadRef.current = sequence;
    updateChatReadPosition(roomId, sequence).then(() => {
      onRead(roomId);
      void refreshChatUnreadCount();
    }).catch(() => {
      lastReadRef.current = Math.min(lastReadRef.current, sequence - 1);
    });
  }, [room, roomId, seenSequence, onRead, foregroundEpoch]);

  const loadOlder = async () => {
    const first = messages[0]?.sequence;
    if (!first || loadingOlder) return;
    setLoadingOlder(true);
    try {
      const page = await getChatMessages(roomId, { beforeSequence: first });
      const element = scrollRef.current;
      if (element) {
        const top = element.getBoundingClientRect().top;
        const visible = Array.from(element.querySelectorAll<HTMLElement>("[data-message-id]"))
          .find((entry) => entry.getBoundingClientRect().bottom > top);
        if (visible) olderAnchorRef.current = {
          id: visible.dataset.messageId!, bottom: visible.getBoundingClientRect().bottom - top,
        };
      }
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
    jumpToLatest();
    const entry = { clientMessageId: newIdempotencyKey(), body, rentalId: selectedRentalId, failed: false };
    writePendingChatMessages(memberId, roomId, [...pending, entry]);
    setPending((previous) => [...previous, entry]);
    void send(entry);
  };

  const respondToRental = async (rentalId: string, action: "approve" | "reject", rejectionReason?: string) => {
    if (rentalActionInFlightRef.current) return;
    rentalActionInFlightRef.current = true;
    setRentalActionBusy(true);
    try {
      const latest = await getRental(rentalId);
      if (latest.rental.status !== "REQUESTED" || !latest.rental.allowedActions.includes(action.toUpperCase())) {
        toast("대여 요청이 변경됐어요. 다시 확인해 주세요.");
        setAppointmentRevision((value) => value + 1);
        return;
      }
      const updated = await commandRental(rentalId, action, latest.rental.version, rejectionReason);
      setRoom((current) => current ? {
        ...current,
        rentals: current.rentals.map((entry) => entry.id === rentalId ? updated.rental : entry),
      } : current);
      if (action === "approve") setAppointmentLoading(true);
      setAppointmentRevision((value) => value + 1);
      toast(action === "approve" ? "요청을 승인했어요. 이제 약속을 잡아 주세요." : "요청을 거절했어요.");
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 409) setAppointmentRevision((value) => value + 1);
      toast(failure instanceof ApiError ? failure.message : "요청을 처리하지 못했어요. 다시 시도해 주세요.");
    } finally {
      rentalActionInFlightRef.current = false;
      setRentalActionBusy(false);
    }
  };

  const approveRental = (rentalId: string) => confirm({
    title: "대여 요청을 승인할까요?",
    body: "승인하면 채팅에서 전달 약속을 잡아요.",
    confirm: "승인",
    onConfirm: () => { void respondToRental(rentalId, "approve"); },
  });

  const rejectRental = (rentalId: string) => reason({
    title: "대여 요청을 거절할까요?",
    body: "거절 사유를 남기면 이웃에게 함께 전달돼요.",
    placeholder: "거절 사유 (필수, 최대 500자)",
    required: true,
    confirm: "거절",
    danger: true,
    onConfirm: (value) => {
      if (value.trim().length > 500) {
        toast("사유는 500자까지 입력해 주세요.");
        return;
      }
      void respondToRental(rentalId, "reject", value.trim());
    },
  });

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
      if (room) onAppointmentChanged(room);
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
      if (room) onAppointmentChanged(room);
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

  const openMemberReport = (memberId: string, name: string) => report({
    name,
    onConfirm: async (selectedReason) => {
      try {
        await createReport({
          targetType: "MEMBER", targetId: memberId,
          reasonCode: toReasonCode(selectedReason), detail: toReasonDetail(selectedReason),
        });
        toast("신고를 접수했어요");
      } catch (error) {
        toast(error instanceof ApiError ? error.message : "신고를 접수하지 못했어요");
      }
    },
  });

  const openBlockConfirm = (memberId: string, name: string) => confirm({
    title: `${name} 님을 차단할까요?`,
    body: "차단하면 서로 대화할 수 없고 물건도 볼 수 없어요.",
    confirm: "차단", danger: true,
    onConfirm: async () => {
      try {
        await blockMember(memberId);
        toast("차단했어요");
        navigate("/chat", { replace: true, state: { blockedRoomId: roomId } });
      } catch (error) {
        toast(error instanceof ApiError ? error.message : "차단하지 못했어요");
      }
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
  const safetyItems = [
    { label: "이웃 프로필", onSelect: () => navigate(`/members/${encodeURIComponent(room.otherMember.id)}`) },
    { label: "신고하기", danger: true, onSelect: () => openMemberReport(room.otherMember.id, other) },
    { label: "차단", danger: true, onSelect: () => openBlockConfirm(room.otherMember.id, other) },
  ];
  const selectableRentals = selectableChatRentals(room);
  const selectedRental = room.rentals.find((entry) => entry.id === selectedRentalId);
  const selectedRentalStatus = selectedRental && chatRentalStatus(selectedRental);
  const rentalResponse = chatRentalResponse(selectedRental, memberId);
  const visibleSlots = slots.filter((slot) => slot.rentalId === selectedRentalId);
  const proposalKinds = visibleSlots.filter((slot) => slot.allowedActions.includes("PROPOSE"));
  const showApprovalCard = selectedRental?.status === "APPROVED" || selectedRental?.status === "ACTIVE";
  const timeline = chatTimeline(messages, selectedRental, showApprovalCard);
  const newMessageCount = countNewChatMessages(messages, seenSequence, room.otherMember.id);

  return (
    <section className={c("chat-room")} aria-label={`${other} 님과의 채팅`}>
      <div className={c("chat-room-head only-desk")}>
        <Avatar name={other} size={36} />
        <div className={c("chat-room-head-body")}><div className={c("t-label")}>{other}</div></div>
        <MoreMenu label="이웃 메뉴" items={safetyItems} />
      </div>
      <div className={c("chat-room-mobile-actions only-mobile")}>
        <span className={c("t-caption chat-room-muted")}>{other} 님과의 대화</span>
        <MoreMenu label="이웃 메뉴" items={safetyItems} />
      </div>
      <ContextBar room={room} selectedRentalId={selectedRentalId} />
      {connectionState === "reconnecting" ? <Alert tone="warning" title="실시간 연결을 다시 시도하고 있어요">
        새 메시지는 주기적으로 확인하고 있어요. 일시적으로 늦게 표시될 수 있어요.
      </Alert> : null}
      {connectionState === "unauthorized" ? <Alert tone="warning" title="로그인이 만료됐어요">
        다시 로그인한 뒤 채팅을 이용해 주세요.
      </Alert> : null}
      {selectableRentals.length > 1 ? <label className={c("chat-rental-picker")}>
        대여 건 <select value={selectedRentalId} onChange={(event) => {
          manualRentalSelectionRef.current = true;
          setAppointmentLoading(true);
          setSelectedRentalId(event.target.value);
        }}>
          {selectableRentals.map((rental) => <option key={rental.id} value={rental.id}>
            {rental.startDate} – {rental.endDate} · {STATUS_UI[chatRentalStatus(rental) ?? "expired"].label}
          </option>)}
        </select>
      </label> : null}
      <div ref={scrollRef} className={c("chat-room-scroll")} onScroll={onScroll}>
        <div ref={contentRef} className={c("chat-room-messages")} role="log" aria-live="polite" aria-label={`${other} 님과의 대화`}>
        {older ? <Button size="sm" variant="secondary" disabled={loadingOlder} onClick={() => void loadOlder()}>
          {loadingOlder ? "불러오는 중" : "이전 대화 보기"}
        </Button> : null}
        {messages.length === 0 && pending.length === 0 && !selectedRental && visibleSlots.every((slot) => !slot.pendingProposal && !slot.confirmedProposal) ?
          <EmptyState icon="chat" title="대화를 시작해 보세요" /> : null}
        {timeline.map((entry) => {
          if (entry.kind === "rental-request" && selectedRental) return <div key={`rental-request-${selectedRental.id}`}
            className={c("chat-rental-card")} role="group" aria-label={`${selectedRental.itemTitle} 대여 요청`}>
            <div className={c("chat-rental-card-head")}>
              <strong className={c("t-label")}>{selectedRental.itemTitle}</strong>
              {selectedRentalStatus ? <StatusBadge status={selectedRentalStatus} /> : null}
            </div>
            <p className={c("t-body chat-rental-card-dates")}>{chatDateRange(selectedRental)}</p>
            <p className={c("t-caption chat-rental-card-note")}>
              {rentalResponse ? rentalResponse.isOwner
                ? "대여 요청이 도착했어요. 승인하면 여기서 약속을 잡을 수 있어요."
                : "대여 요청을 보냈어요. 승인을 기다리고 있어요."
                : selectedRental.status === "APPROVED" || selectedRental.status === "ACTIVE"
                  ? "대여 요청이 승인됐어요."
                  : "처리된 대여 요청이에요."}
            </p>
            {rentalResponse?.isOwner ? <div className={c("chat-rental-card-actions")}>
              <Button size="sm" variant="secondary" disabled={rentalActionBusy || !rentalResponse.canReject}
                onClick={() => rejectRental(selectedRental.id)}>거절</Button>
              <Button size="sm" disabled={rentalActionBusy || !rentalResponse.canApprove}
                onClick={() => approveRental(selectedRental.id)}>승인</Button>
            </div> : null}
          </div>;
          if (entry.kind === "rental-approved" && selectedRental) return <div key={`rental-approved-${selectedRental.id}`}
            className={c("chat-rental-card")} role="group" aria-label={`${selectedRental.itemTitle} 약속 잡기`}>
            <div className={c("chat-rental-card-head")}>
              <strong className={c("t-label")}>전달·반납 약속</strong>
              {selectedRentalStatus ? <StatusBadge status={selectedRentalStatus} /> : null}
            </div>
            <p className={c("t-caption chat-rental-card-note")}>
              {proposalKinds.length > 0 ? "대여가 승인됐어요. 전달·반납 약속을 잡아 주세요."
                : "대여가 승인됐어요. 약속 제안을 확인해 주세요."}
            </p>
            {proposalKinds.length > 0 ? <div className={c("chat-rental-card-actions")}>
              {proposalKinds.map((slot) => <Button key={slot.kind} size="sm" variant="secondary" icon="clock"
                disabled={appointmentBusy} onClick={() => void openAppointmentSheet(slot.kind === "PICKUP" ? "give" : "back")}>
                {slot.kind === "PICKUP" ? "전달 약속 잡기" : "반납 약속 잡기"}
              </Button>)}
            </div> : null}
            {appointmentLoading ? <p className={c("t-caption chat-rental-card-note")}>약속을 확인하고 있어요…</p> : null}
          </div>;
          if (entry.kind !== "message") return null;
          const { message, index } = entry;
          const date = new Date(message.createdAt).toDateString();
          const divider = index === 0 || date !== new Date(messages[index - 1].createdAt).toDateString();
          return <div key={message.id} data-message-id={message.id} data-message-sequence={message.sequence} className={c("chat-message-group")}>
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
      </div>
      {!atBottom || newMessageCount > 0 ? <div className={c("chat-latest")} aria-live="polite">
        <Button size="sm" variant="secondary" onClick={jumpToLatest}>
          {newMessageCount > 0 ? `새 메시지 ${newMessageCount}개 · 최신 대화 보기` : "최신 대화 보기"}
        </Button>
      </div> : null}
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
