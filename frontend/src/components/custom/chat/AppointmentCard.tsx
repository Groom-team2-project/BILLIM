import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import type { Appointment } from "@/data/chat";
import styles from "@/components/custom/chat/AppointmentCard.module.css";
import badgeStyles from "@/components/custom/rentals/StatusBadge.module.css";
import { classes } from "@/utils/classes";

const c = classes({ ...styles, ...badgeStyles });

type AppointmentCardProps = {
  appt: Appointment;
  status?: "proposed" | "confirmed" | "change";
  /** 내가 보낸 제안인지 */
  mine?: boolean;
  other?: string;
  onAccept?: () => void;
  onWithdraw?: () => void;
  onChangeTime?: () => void;
  onCalendar?: () => void;
  onRequestChange?: () => void;
};

/** 전달/반납 약속 카드 — 약속 상태 3종 (DESIGN_SYSTEM.md §5-2) */
export function AppointmentCard({
  appt,
  status = "proposed",
  mine,
  other,
  onAccept,
  onWithdraw,
  onChangeTime,
  onCalendar,
  onRequestChange,
}: AppointmentCardProps) {
  const badge =
    status === "confirmed" ? (
      <span className={c("status-badge status-badge--approved t-micro")}>
        <Icon name="check" size={14} />약속 확정
      </span>
    ) : status === "change" ? (
      <span className={c("status-badge status-badge--overdue t-micro")}>
        <Icon name="refresh" size={14} />변경 요청
      </span>
    ) : (
      <span className={c("status-badge status-badge--requested t-micro")}>
        <Icon name="ring" size={14} />약속 제안
      </span>
    );

  return (
    <div className={c(`appt${mine ? " appt--me" : ""}`)} role="group" aria-label={`${appt.kind} 약속`}>
      <div className={c("appt-head")}>
        <span className={c("t-label appt-kind")}>
          <Icon name="clock" size={18} />
          {appt.kind} 약속
        </span>
        {badge}
      </div>
      <dl className={c("appt-rows t-body")}>
        <dt className={c("t-caption")}>날짜</dt>
        <dd>{appt.date}</dd>
        <dt className={c("t-caption")}>시간</dt>
        <dd>{appt.time}</dd>
        <dt className={c("t-caption")}>장소</dt>
        <dd>
          {appt.place}
          <span className={c("t-caption appt-muted")}>공용 장소</span>
        </dd>
      </dl>
      {(status === "proposed" || status === "change") && !mine && (onAccept || onChangeTime) ? (
        <div className={c("appt-actions")}>
          {onChangeTime ? <Button variant="secondary" size="sm" onClick={onChangeTime}>시간 바꾸기</Button> : null}
          {onAccept ? <Button size="sm" onClick={onAccept}>수락</Button> : null}
        </div>
      ) : null}
      {(status === "proposed" || status === "change") && mine ? (
        <>
          <p className={c("t-caption appt-muted appt-note")}>{other} 님이 수락하면 확정돼요.</p>
          {onWithdraw ? <div className={c("appt-actions")}><Button variant="secondary" size="sm" onClick={onWithdraw}>제안 철회</Button></div> : null}
        </>
      ) : null}
      {status === "confirmed" && (onCalendar || onRequestChange) ? (
        <div className={c("appt-actions")}>
          {onCalendar ? <Button variant="ghost" size="sm" icon="calendar" onClick={onCalendar}>캘린더에 추가</Button> : null}
          {onRequestChange ? <Button variant="secondary" size="sm" icon="refresh" onClick={onRequestChange}>변경 요청</Button> : null}
        </div>
      ) : null}
    </div>
  );
}
