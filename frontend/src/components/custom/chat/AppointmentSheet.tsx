import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Chip } from "@/components/ui/chip";
import { Icon } from "@/components/ui/icon";
import { Segmented } from "@/components/ui/segmented";
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
};

export function AppointmentSheet({ onClose, initialKind = "give", onSubmit }: AppointmentSheetProps) {
  const [kind, setKind] = useState<Kind>(initialKind);
  const [time, setTime] = useState("오전 10:00");
  const [place, setPlace] = useState(0);

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
          options={[
            { value: "give", label: "전달 · 9/26(토)" },
            { value: "back", label: "반납 · 9/27(일)" },
          ]}
        />
        <p className={c("t-caption appt-muted appt-sheet-note")}>
          날짜는 대여 기간의 시작일·반납일로 정해져요. 시간과 장소만 고르면 돼요.
        </p>

        <div className={c("appt-sheet-group")}>
          <span className={c("t-label")}>시간</span>
          <div className={c("appt-sheet-times")}>
            {TIMES.map((t) => (
              <Chip key={t} selected={t === time} onClick={() => setTime(t)}>{t}</Chip>
            ))}
            <Chip icon="clock">직접 입력</Chip>
          </div>
        </div>

        <div className={c("appt-sheet-group")}>
          <span className={c("t-label")}>장소 · 공용 장소</span>
          <div className={c("appt-radio-list")} role="radiogroup" aria-label="거래 장소">
            {PLACES.map((pl, i) => (
              <button
                key={pl.name}
                type="button"
                role="radio"
                aria-checked={i === place ? "true" : "false"}
                className={c("appt-radio")}
                onClick={() => setPlace(i)}
              >
                <span className={c("appt-radio-dot")} />
                <span className={c("appt-radio-body")}>
                  {pl.name}
                  {pl.registered ? (
                    <span className={c("t-caption appt-muted appt-radio-sub")}>물건에 등록된 장소</span>
                  ) : null}
                </span>
                <span className={c("t-caption appt-muted")}>{pl.dist}</span>
              </button>
            ))}
          </div>
          <span className={c("t-caption appt-muted appt-sheet-lock")}>
            <Icon name="lock" size={14} />
            집 주소 대신 커뮤니티 공용 장소에서 만나요
          </span>
        </div>

        <Button
          size="lg"
          block
          onClick={() => {
            onSubmit?.(`${KIND_DATE[kind]} ${time}`);
            onClose();
          }}
        >
          {KIND_DATE[kind]} {time} 약속 보내기
        </Button>
      </div>
    </div>
  );
}
