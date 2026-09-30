import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useUi } from "@/components/overlay/UiContext";
import { Button } from "@/components/ui/button";
import { Calendar, type DayRange } from "@/components/ui/calendar";
import { Icon } from "@/components/ui/icon";
import { Alert } from "@/components/ui/alert";
import { DateRange } from "@/components/ui/date-range";
import { MOCK_ITEMS } from "@/data/items";
import { MOCK_TODAY } from "@/constants/mock";
import { fmtDate, md, rangeDays } from "@/utils/date";
import styles from "@/pages/items/RequestPage.module.css";
import { classes } from "@/utils/classes";
import { PageHeader } from "@/components/layout/header";

const c = classes(styles);

/** 목 예약 구간 — 물건 상세와 동일 기준 */
const AVAIL_FROM = new Date(2026, 8, 1);
const AVAIL_TO = new Date(2026, 9, 31);
const BOOKED: DayRange[] = [[new Date(2026, 8, 23), new Date(2026, 8, 24)]];

/** 대여 요청 — 날짜를 확정하고 요청을 보내는 화면 */
export function RequestPage() {
  const { itemId } = useParams();
  const navigate = useNavigate();
  const { toast } = useUi();
  const item = MOCK_ITEMS.find((i) => String(i.id) === itemId) ?? MOCK_ITEMS[0];
  const [range, setRange] = useState<DayRange>([new Date(2026, 8, 26), new Date(2026, 8, 27)]);

  const start = md(range[0]);
  const end = md(range[1]);
  const label = `${fmtDate(start)} – ${fmtDate(end)} · ${rangeDays(start, end)}일`;

  return (
    <div className={c("request page-window")}>
      <PageHeader title="대여 요청" backTo={`/items/${item.id}`} />
      <div className={c("request-card")}>
        <div className={c("t-body-lg")}>{item.title}</div>
        <div className={c("t-caption request-muted")}>{item.owner} 님 · 무료</div>
      </div>

      <h2 className={c("t-title request-heading")}>대여 날짜</h2>
      <Calendar today={MOCK_TODAY} from={AVAIL_FROM} to={AVAIL_TO} booked={BOOKED} value={range} onChange={setRange} />

      <Alert tone="warning" title="9/23(수) – 9/24(목)은 이미 예약됐어요">
        다른 이웃이 빌리기로 한 날짜예요. 9/25(금)부터 다시 빌릴 수 있어요. 지금 고른 {fmtDate(start)} –{" "}
        {fmtDate(end)}은 가능해요.
      </Alert>

      <div className={c("request-card")}>
        <div className={c("request-row")}>
          <Icon name="calendar" size={16} />
          <DateRange start={start} end={end} icon={false} />
        </div>
        <div className={c("request-row request-muted t-caption")}>
          <Icon name="pin" size={16} />
          {item.place} · 집 주소는 공유되지 않아요
        </div>
      </div>

      <div className={c("request-actionbar")}>
        <Button
          size="lg"
          block
          onClick={() => {
            toast("대여 요청을 보냈어요 · 승인되면 알려드려요");
            navigate("/rentals");
          }}
        >
          {label}로 요청하기
        </Button>
      </div>
    </div>
  );
}
