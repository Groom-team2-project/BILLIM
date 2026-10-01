import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useUi } from "@/components/overlay/UiContext";
import { Button } from "@/components/ui/button";
import { MoreMenu } from "@/components/ui/more-menu";
import { Avatar } from "@/components/ui/avatar";
import { AppointmentCard } from "@/components/custom/chat/AppointmentCard";
import { AppointmentSheet } from "@/components/custom/chat/AppointmentSheet";
import { Bubble, DateDivider, SystemMessage } from "@/components/custom/chat/Bubble";
import { Composer } from "@/components/custom/chat/Composer";
import { ContextBar } from "@/components/custom/chat/ContextBar";
import { MOCK_APPT } from "@/data/chat";
import styles from "@/components/custom/chat/ChatRoom.module.css";
import { classes } from "@/utils/classes";
import { useSearchParams } from "react-router-dom";
import { Alert } from "@/components/ui/alert";
import { Loading, Skeleton } from "@/components/ui/state";

const c = classes(styles);

/** 프로토타입 대화 목데이터 — 전동드릴 거래(승인 상태) 스크립트 */
const ROOM_RENTAL = {
  title: "충전식 전동드릴 12V",
  cat: "tool" as const,
  status: "approved" as const,
  start: [9, 26] as [number, number],
  end: [9, 27] as [number, number],
  place: "관리동 1층 무인택배함 앞",
};

const OTHER = "정우";

function nowLabel() {
  const d = new Date();
  const h = d.getHours();
  const mm = String(d.getMinutes()).padStart(2, "0");
  return `${h < 12 ? "오전" : "오후"} ${((h + 11) % 12) + 1}:${mm}`;
}

export function ChatRoom() {
  const { confirm, report, toast } = useUi();
  const navigate = useNavigate();
  const [sheetOpen, setSheetOpen] = useState(false);
  const [sheetKind, setSheetKind] = useState<"give" | "back">("give");
  const [confirmed, setConfirmed] = useState(false);
  const [sent, setSent] = useState<{ text: string; time: string }[]>([]);
  const [params] = useSearchParams();
  /* 프로토타입 채팅방 상태: 안전 안내 · 전송 실패 · 차단 · 로딩 */
  const variant = params.get("state");
  const blocked = variant === "blocked";

  const openSheet = (kind: "give" | "back") => {
    setSheetKind(kind);
    setSheetOpen(true);
  };

  const moreItems = [
    { label: "이웃 프로필", onSelect: () => navigate(`/neighbors/${OTHER}`) },
    {
      label: "신고하기",
      danger: true,
      onSelect: () => report({ name: OTHER, onConfirm: () => toast("신고를 접수했어요") }),
    },
    {
      label: "차단",
      danger: true,
      onSelect: () =>
        confirm({
          title: `${OTHER} 님을 차단할까요?`,
          body: "차단하면 서로 대화할 수 없고 물건도 볼 수 없어요.",
          confirm: "차단",
          danger: true,
          onConfirm: () => {
            toast("차단했어요");
            navigate("/");
          },
        }),
    },
  ];

  return (
    <section className={c("chat-room")} aria-label={`${OTHER} 님과의 채팅`}>
      <div className={c("chat-room-head only-desk")}>
        <Avatar name={OTHER} size={36} />
        <div className={c("chat-room-head-body")}>
          <div className={c("t-label")}>{OTHER}</div>
          <div className={c("t-caption chat-room-muted")}>반납 약속 12/12 지킴 · 거래 완료 18회</div>
        </div>
        <MoreMenu label="신고 · 차단" items={moreItems} />
      </div>

      <ContextBar
        rental={ROOM_RENTAL}
        action={
          confirmed ? null : (
            <Button size="sm" variant="secondary" icon="clock" onClick={() => openSheet("give")}>
              약속 잡기
            </Button>
          )
        }
      />

      <div className={c("chat-room-scroll")} role="log" aria-live="polite" aria-label={`${OTHER} 님과의 대화`}>
        {variant === "loading" ? (
          <Loading label="대화 불러오는 중">
            <Skeleton w="60%" h={40} r={16} />
            <Skeleton w="45%" h={40} r={16} style={{ alignSelf: "flex-end" }} />
            <Skeleton w="70%" h={120} r={16} />
            <Skeleton w="40%" h={40} r={16} style={{ alignSelf: "flex-end" }} />
          </Loading>
        ) : (
        <>
        <DateDivider>9월 22일 화요일</DateDivider>
        <SystemMessage icon="ring">대여 요청을 보냈어요 · 9/26(토) – 9/27(일)</SystemMessage>
        <Bubble time="오후 2:58">안녕하세요! 드릴 요청 확인했어요.</Bubble>
        <SystemMessage icon="check">{OTHER} 님이 대여 요청을 승인했어요</SystemMessage>
        <Bubble time="오후 3:01">토요일 오전에 택배함 앞에서 드리면 될까요?</Bubble>
        <AppointmentCard
          appt={MOCK_APPT}
          status={confirmed ? "confirmed" : "proposed"}
          other={OTHER}
          onAccept={() => setConfirmed(true)}
          onChangeTime={() => openSheet("give")}
          onCalendar={() => toast("캘린더에 추가했어요")}
          onRequestChange={() => openSheet("give")}
        />
        <Bubble me time="오후 3:10" read>네 좋아요! 비트 케이스도 같이 부탁드려요</Bubble>
        <Bubble time="오후 3:12">넵, 비트 케이스도 챙겨둘게요.</Bubble>
        {confirmed ? (
          <>
            <SystemMessage icon="clock">약속이 확정됐어요 · 하루 전과 1시간 전에 알려드려요</SystemMessage>
            <div className={c("chat-suggest t-caption")}>
              <span>반납 약속도 미리 잡아 둘까요?</span>
              <Button size="sm" variant="secondary" icon="clock" onClick={() => openSheet("back")}>
                9/27(일) 반납 약속 잡기
              </Button>
            </div>
          </>
        ) : null}
        {variant === "safety" ? (
          <>
            <Bubble time="오후 3:20">그리고 보증금으로 만 원만 계좌로 보내주실 수 있을까요?</Bubble>
            <div className={c("chat-safety")}>
              <Alert
                tone="danger"
                title="돈이나 보증금을 보내지 마세요"
                actions={
                  <>
                    <Button size="sm" variant="secondary" icon="flag" onClick={() => report({ name: OTHER, onConfirm: () => toast("신고를 접수했어요") })}>
                      신고하기
                    </Button>
                    <Button size="sm" variant="ghost">괜찮아요</Button>
                  </>
                }
              >
                BILLIM의 대여는 모두 무료예요. 입금·보증금 요청은 커뮤니티 규칙 위반이에요.
              </Alert>
            </div>
          </>
        ) : null}
        {variant === "error" ? (
          <>
            <Bubble me failed>혹시 충전기도 같이 있나요?</Bubble>
            <div className={c("chat-retry")}>
              <Button size="sm" variant="secondary" icon="refresh" onClick={() => toast("다시 보냈어요")}>다시 보내기</Button>
              <Button size="sm" variant="ghost">삭제</Button>
            </div>
          </>
        ) : null}
        {sent.map((m, i) => (
          <Bubble key={i} me time={m.time}>{m.text}</Bubble>
        ))}
        </>
        )}
      </div>

      <Composer
        blocked={blocked}
        onPlus={() => openSheet("give")}
        onSend={(text) => setSent((prev) => [...prev, { text, time: nowLabel() }])}
      />

      {sheetOpen ? (
        <AppointmentSheet
          initialKind={sheetKind}
          onClose={() => setSheetOpen(false)}
          onSubmit={(when) => toast(`${when} 약속을 보냈어요`)}
        />
      ) : null}
    </section>
  );
}
