import type { ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { StatusBadge } from "@/components/custom/rentals/StatusBadge";
import { useUi } from "@/components/overlay/UiContext";
import { DateRange } from "@/components/ui/date-range";
import { DDay } from "@/components/ui/d-day";
import { Photo } from "@/components/custom/items/ItemCard";
import { MOCK_ITEMS } from "@/data/items";
import { fmtDate } from "@/utils/date";
import type { Rental } from "@/data/rentals";
import { Steps } from "@/components/custom/rentals/Steps";
import styles from "@/components/custom/rentals/RentalCard.module.css";
import badgeStyles from "@/components/custom/rentals/StatusBadge.module.css";
import { classes } from "@/utils/classes";

const c = classes({ ...styles, ...badgeStyles });

type RentalCardProps = {
  rental: Rental;
  /** owner = 빌려주는 사람(소유자) 관점, borrower = 빌리는 사람 관점 */
  role: "owner" | "borrower";
  conflict?: ReactNode;
};

/** 대여 카드 — 역할·상태별 행동 버튼과 안내 (DESIGN_SYSTEM.md §5-1) */
export function RentalCard({ rental: r, role, conflict }: RentalCardProps) {
  const { confirm, reason, toast } = useUi();
  const navigate = useNavigate();
  const item = MOCK_ITEMS.find((i) => i.title === r.title);
  const isOwner = role === "owner";
  const who = (isOwner ? r.borrower : r.owner) ?? "";

  // 확인 모달·토스트 문구 — 프로토타입 rentals 행동 맵 그대로
  const onApprove = () =>
    confirm({
      title: "대여 요청을 승인할까요?",
      body: "승인하면 채팅에서 전달 약속을 잡아요.",
      confirm: "승인",
      onConfirm: () => {
        toast("요청을 승인했어요");
        navigate("/chat");
      },
    });
  const onHandOver = () =>
    confirm({
      title: "물건을 전달했나요?",
      body: "전달을 확인하면 대여가 시작돼요.",
      confirm: "전달 확인",
      onConfirm: () => toast("전달을 확인했어요"),
    });
  const onReturn = () =>
    confirm({
      title: "물건을 돌려받았나요?",
      body: "반납을 확인하면 대여가 완료되고, 두 사람의 신뢰 지표(거래 완료·반납 약속)에 반영돼요.",
      confirm: "반납 확인",
      onConfirm: () => toast("반납을 확인했어요 · 신뢰 지표에 반영돼요"),
    });
  const onReject = () =>
    reason({
      title: "대여 요청을 거절할까요?",
      body: "거절 사유를 남기면 이웃에게 함께 전달돼요.",
      placeholder: "거절 사유 (선택) · 예: 그 주말엔 제가 쓸 예정이에요",
      confirm: "거절",
      danger: true,
      onConfirm: () => toast("요청을 거절했어요"),
    });
  const onCancel = () =>
    confirm({
      title: "대여 요청을 취소할까요?",
      body: "취소하면 이웃에게 취소 알림이 가요.",
      confirm: "요청 취소",
      danger: true,
      onConfirm: () => toast("요청을 취소했어요"),
    });

  let actions: ReactNode = null;
  let note: string | null = null;
  if (isOwner) {
    if (r.status === "requested") {
      actions = (
        <>
          <Button variant="secondary" onClick={onReject}>거절</Button>
          <Button onClick={onApprove}>승인</Button>
        </>
      );
    }
    if (r.status === "approved") {
      actions = <Button icon="check" onClick={onHandOver}>전달 확인</Button>;
      note = `${fmtDate(r.start)}에 ${r.place}에서 ${who} 님에게 건네고 눌러 주세요.`;
    }
    if (r.status === "active") {
      actions = <Button variant="secondary" icon="check" onClick={onReturn}>반납 확인</Button>;
      note = "물건을 돌려받은 뒤에 눌러 주세요. 자동으로 반납 처리되지 않아요.";
    }
    if (r.status === "overdue") {
      actions = <Button icon="check" onClick={onReturn}>반납 확인</Button>;
      note = "반납 전까지 이 물건의 새 승인·전달이 잠시 막혀요.";
    }
  } else {
    if (r.status === "requested") {
      actions = <Button variant="secondary" onClick={onCancel}>요청 취소</Button>;
      note = `${who} 님이 승인하면 알림으로 알려드려요. 승인 시점에 날짜를 다시 확인해요.`;
    }
    if (r.status === "approved") {
      actions = (
        <>
          <Button variant="ghost" onClick={onCancel}>요청 취소</Button>
          <Button variant="secondary" icon="pin" onClick={() => navigate("/chat")}>장소 보기</Button>
        </>
      );
      note = `${fmtDate(r.start)}에 ${r.place}에서 받아요. 전달은 ${who} 님이 확인해요.`;
    }
    if (r.status === "active") note = `${fmtDate(r.end)}까지 ${r.place}에 돌려주세요. 반납은 ${who} 님이 확인해요.`;
    if (r.status === "overdue") note = `반납일이 지났어요. ${who} 님에게 돌려준 뒤 확인을 부탁하세요.`;
    if (r.status === "rejected") {
      actions = (
        <Button
          variant="secondary"
          icon="calendar"
          onClick={() => navigate(item ? `/items/${item.id}` : "/search")}
        >
          다른 날짜로 다시 요청
        </Button>
      );
    }
  }

  return (
    <article className={c("rental-card")} aria-label={`${r.title}, ${r.status}`}>
      {/* 물건 상세로 이동 — 소유자면 내 물건 상태로 */}
      <Link
        to={item ? `/items/${item.id}${isOwner ? "?state=own" : ""}` : "/search"}
        className={c("rental-top")}
        aria-label={`${r.title} 상세 보기`}
      >
        <Photo cat={r.cat} iconSize={24} className={c("rental-photo")} />
        <div className={c("rental-head")}>
          <div className={c("rental-title")}>
            <strong>{r.title}</strong>
            <StatusBadge status={r.status} />
          </div>
          <span className={c("t-caption rental-muted")}>
            {(isOwner ? "빌리는 사람 " : "빌려주는 사람 ") + who + (r.trustLine ? ` · ${r.trustLine}` : "")}
          </span>
        </div>
        <Icon name="chevronR" size={18} />
      </Link>

      <div className={c("rental-facts")}>
        <Icon name="calendar" size={18} />
        <span className={c("rental-fact-line")}>
          <DateRange start={r.start} end={r.end} icon={false} />
          {r.dday ? <DDay late={r.status === "overdue"}>{r.dday}</DDay> : null}
        </span>
        <Icon name="pin" size={18} />
        <span className={c("rental-fact-strong")}>{r.place}</span>
        {r.appt ? (
          <>
            <Icon name="clock" size={18} />
            <span className={c("rental-fact-line")}>
              <span className={c("rental-fact-strong")}>{r.appt}</span>
              <span className={c("status-badge status-badge--approved t-micro")}>약속 확정</span>
            </span>
          </>
        ) : null}
      </div>

      <Steps status={r.status} />

      {r.reason ? (
        <div className={c("rental-note t-caption")}>
          <Icon name="info" size={16} />
          거절 사유: “{r.reason}”
        </div>
      ) : null}
      {note ? (
        <div className={c("rental-note t-caption")}>
          <Icon name="info" size={16} />
          {note}
        </div>
      ) : null}

      {conflict}
      {actions && !conflict ? <div className={c("rental-actions")}>{actions}</div> : null}
    </article>
  );
}
