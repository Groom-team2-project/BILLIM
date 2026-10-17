import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Chip } from "@/components/ui/chip";
import { Icon } from "@/components/ui/icon";
import { Segmented } from "@/components/ui/segmented";
import type { ApiCommunityPlace } from "@/api/communities";
import { appointmentDateLabel, appointmentInstant } from "@/utils/appointment-view";
import styles from "@/components/custom/chat/AppointmentSheet.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 시간 후보 — 프로토타입 고정 목록 */
const TIMES = ["오전 9:00", "오전 10:00", "오전 11:00", "오후 2:00", "오후 7:00", "오후 8:00"];

/** 공용 장소 후보 — 물건 등록 장소 우선 */
const PLACES = [
  { name: "관리동 1층 무인택배함 앞", dist: "180m", registered: true },
  { name: "B동 커뮤니티센터 입구", dist: "320m", registered: false },
  { name: "정문 경비실 옆 벤치", dist: "90m", registered: false },
];

type Kind = "give" | "back";

const KIND_DATE: Record<Kind, string> = { give: "9/26(토)", back: "9/27(일)" };

type AppointmentSheetProps = {
  onClose: () => void;
  /** 전달/반납 초기 선택 */
  initialKind?: Kind;
  /** 보내기 확정 — "9/26(토) 오전 10:00" 형태 라벨 전달 */
  onSubmit?: (when: string) => void;
  live?: {
    dates: Record<Kind, string>;
    places: ApiCommunityPlace[];
    kinds: Kind[];
    onSubmit: (kind: Kind, scheduledAt: string, placeId: string) => Promise<boolean>;
  };
};

const clockTime: Record<string, string> = {
  "오전 9:00": "09:00", "오전 10:00": "10:00", "오전 11:00": "11:00",
  "오후 2:00": "14:00", "오후 7:00": "19:00", "오후 8:00": "20:00",
};

export function AppointmentSheet({ onClose, initialKind = "give", onSubmit, live }: AppointmentSheetProps) {
  const [kind, setKind] = useState<Kind>(initialKind);
  const [time, setTime] = useState("오전 10:00");
  const [place, setPlace] = useState(0);
  const [customTime, setCustomTime] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30000);
    return () => window.clearInterval(timer);
  }, []);
  const placeOptions = live?.places ?? PLACES;
  const date = live ? live.dates[kind] : undefined;
  const label = date ? appointmentDateLabel(date) : KIND_DATE[kind];
  const timeValue = customTime || clockTime[time];
  const scheduledAt = live && date && timeValue ? appointmentInstant(date, timeValue) : undefined;
  const canSend = !live || (Boolean(placeOptions.length) && Boolean(scheduledAt) && new Date(scheduledAt!).getTime() > now);

  const submit = async () => {
    if (!live) {
      onSubmit?.(`${KIND_DATE[kind]} ${time}`);
      onClose();
      return;
    }
    const selected = live.places[place];
    if (!selected || !scheduledAt || !canSend || submitting) return;
    setSubmitting(true);
    try {
      if (await live.onSubmit(kind, scheduledAt, selected.id)) onClose();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={c("appt-overlay")}>
      <div className={c("appt-scrim")} aria-hidden="true" onClick={onClose} />
      <div className={c("appt-sheet")} role="dialog" aria-label="약속 잡기">
        <div className={c("appt-sheet-grab")} aria-hidden="true" />
        <div className={c("appt-sheet-head")}>
          <h2 className={c("t-title")}>약속 잡기</h2>
          <button type="button" className={c("appt-sheet-close")} aria-label="닫기" onClick={onClose}>
            <Icon name="x" size={18} />
          </button>
        </div>

        <Segmented
          label="약속 종류"
          value={kind}
          onChange={setKind}
          options={(live?.kinds ?? (["give", "back"] as Kind[])).map((value) => ({
            value, label: `${value === "give" ? "전달" : "반납"} · ${live ? appointmentDateLabel(live.dates[value]) : KIND_DATE[value]}`,
          }))}
        />
        <p className={c("t-caption appt-muted appt-sheet-note")}>
          날짜는 대여 기간의 시작일·반납일로 정해져요. 시간과 장소만 고르면 돼요.
        </p>

        <div className={c("appt-sheet-group")}>
          <span className={c("t-label")}>시간</span>
          <div className={c("appt-sheet-times")}>
            {TIMES.map((t) => (
              <Chip key={t} selected={!customTime && t === time} onClick={() => { setTime(t); setCustomTime(""); }}>{t}</Chip>
            ))}
            {live ? <label className={c("appt-sheet-custom t-caption")}>
              직접 입력 <input type="time" aria-label="약속 시간 직접 입력" value={customTime} onChange={(event) => setCustomTime(event.target.value)} />
            </label> : <Chip icon="clock">직접 입력</Chip>}
          </div>
        </div>

        <div className={c("appt-sheet-group")}>
          <span className={c("t-label")}>장소 · 공용 장소</span>
          <div className={c("appt-radio-list")} role="radiogroup" aria-label="거래 장소">
            {placeOptions.map((pl, i) => (
              <button
                key={"id" in pl ? pl.id : pl.name}
                type="button"
                role="radio"
                aria-checked={i === place ? "true" : "false"}
                className={c("appt-radio")}
                onClick={() => setPlace(i)}
              >
                <span className={c("appt-radio-dot")} />
                <span className={c("appt-radio-body")}>
                  {pl.name}
                  {"registered" in pl && pl.registered ? (
                    <span className={c("t-caption appt-muted appt-radio-sub")}>물건에 등록된 장소</span>
                  ) : null}
                </span>
                {"dist" in pl ? <span className={c("t-caption appt-muted")}>{pl.dist}</span> : null}
              </button>
            ))}
          </div>
          {live && placeOptions.length === 0 ? <p className={c("t-caption appt-muted")}>선택할 수 있는 공용 장소가 없어요.</p> : null}
          <span className={c("t-caption appt-muted appt-sheet-lock")}>
            <Icon name="lock" size={14} />
            집 주소 대신 커뮤니티 공용 장소에서 만나요
          </span>
        </div>

        <Button
          size="lg"
          block
          disabled={!canSend || submitting}
          onClick={() => void submit()}
        >
          {submitting ? "보내는 중" : `${label} ${customTime || time} 약속 보내기`}
        </Button>
      </div>
    </div>
  );
}
