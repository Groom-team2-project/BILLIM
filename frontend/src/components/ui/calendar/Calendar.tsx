import { useState } from "react";
import { Icon } from "@/components/ui/icon";
import styles from "@/components/ui/calendar/Calendar.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

const DOW = ["일", "월", "화", "수", "목", "금", "토"];

export type DayRange = [Date, Date];

type CalendarProps = {
  /** 기준일 — 이전 날짜는 선택 불가 */
  today: Date;
  /** 대여 가능 기간 경계 */
  from?: Date;
  to?: Date;
  booked?: DayRange[];
  value: DayRange | null;
  onChange?: (range: DayRange) => void;
  /** 조회용 범례(대여 가능·예약됨 등) 숨김 — 등록 화면처럼 선택 전용일 때 */
  hideLegend?: boolean;
  /** 대여 가능한 날을 회색 네모 칸으로 표시 — 물건 상세처럼 가능한 날을 보여 주는 화면용 (기본 꺼짐) */
  markAvailable?: boolean;
  /** 처음 보여 줄 달 — 선택값이 없을 때 쓴다(기본은 오늘이 있는 달) */
  initialMonth?: Date;
};

const dayN = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
const sameDay = (a: Date, b: Date) => dayN(a) === dayN(b);
const inRange = (d: Date, r: DayRange) => dayN(d) >= dayN(r[0]) && dayN(d) <= dayN(r[1]);

/** 월 달력 — 실제 날짜 계산 · 월 이동 · 기간 선택 (DESIGN_SYSTEM.md §6) */
export function Calendar({ today, from, to, booked = [], value, onChange, hideLegend, markAvailable, initialMonth }: CalendarProps) {
  const base = value?.[0] ?? initialMonth ?? today;
  const [month, setMonth] = useState(() => new Date(base.getFullYear(), base.getMonth(), 1));
  const y = month.getFullYear();
  const m = month.getMonth();
  const firstDay = new Date(y, m, 1).getDay();
  const lastDate = new Date(y, m + 1, 0).getDate();

  /* 시작일만 있으면 그날 이후 클릭 = 반납일 확정, 그 외 클릭 = 새 시작일 */
  const pick = (d: Date) => {
    if (!onChange) return;
    if (value && sameDay(value[0], value[1]) && dayN(d) >= dayN(value[0])) onChange([value[0], d]);
    else onChange([d, d]);
  };

  const cells = [];
  for (let i = 0; i < firstDay; i++) cells.push(<span key={`e${i}`} />);
  for (let n = 1; n <= lastDate; n++) {
    const d = new Date(y, m, n);
    const isBooked = booked.some((r) => inRange(d, r));
    const off =
      dayN(d) < dayN(today) || (from ? dayN(d) < dayN(from) : false) || (to ? dayN(d) > dayN(to) : false);
    const isSel = !!value && inRange(d, value);
    const isStart = !!value && sameDay(d, value[0]);
    const isEnd = !!value && sameDay(d, value[1]);
    const label = `${m + 1}월 ${n}일 ${
      isBooked && isSel ? "선택했지만 이미 예약됨" : isBooked ? "예약됨" : off ? "대여 불가" : isSel ? "선택됨" : "대여 가능"
    }`;
    const cls = [
      "cal-day",
      off && "cal-day--off",
      markAvailable && !off && !isBooked && !isSel && "cal-day--ok",
      isBooked && "cal-day--booked",
      sameDay(d, today) && "cal-day--today",
      isSel && value && !sameDay(value[0], value[1]) && "cal-day--in",
      isStart && "cal-day--start",
      isEnd && "cal-day--end",
      isSel && isBooked && "cal-day--clash",
    ]
      .filter(Boolean)
      .join(" ");
    cells.push(
      <button
        key={n}
        type="button"
        disabled={off || isBooked}
        aria-label={label}
        aria-pressed={isSel ? "true" : undefined}
        className={c(cls)}
        onClick={() => pick(d)}
      >
        <span>{n}</span>
      </button>,
    );
  }

  return (
    <div className={c("cal")}>
      <div className={c("cal-head")}>
        <button type="button" className={c("cal-nav")} aria-label="이전 달" onClick={() => setMonth(new Date(y, m - 1, 1))}>
          <Icon name="chevronL" size={18} />
        </button>
        <strong className={c("t-body-lg")}>{y}년 {m + 1}월</strong>
        <button type="button" className={c("cal-nav")} aria-label="다음 달" onClick={() => setMonth(new Date(y, m + 1, 1))}>
          <Icon name="chevronR" size={18} />
        </button>
      </div>
      <div className={c("cal-grid")}>
        {DOW.map((w) => (
          <span key={w} className={c("cal-dow t-micro")}>{w}</span>
        ))}
        {cells}
      </div>
      {hideLegend ? null : <div className={c("cal-legend t-micro")}>
        <span><i className={c("cal-swatch cal-swatch--sel")} />선택</span>
        <span><i className={c(`cal-swatch cal-swatch--ok${markAvailable ? " cal-swatch--ok-fill" : ""}`)} />대여 가능</span>
        <span><i className={c("cal-swatch cal-swatch--booked")} />예약됨</span>
        <span><i className={c("cal-swatch cal-swatch--off")} />지난 날짜·기간 외</span>
      </div>}
    </div>
  );
}
