import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useUi } from "@/components/overlay/UiContext";
import { Button } from "@/components/ui/button";
import { Calendar, type DayRange } from "@/components/ui/calendar";
import { Icon } from "@/components/ui/icon";
import { Alert } from "@/components/ui/alert";
import { DateRange } from "@/components/ui/date-range";
import { getItem, type ApiItemDetail } from "@/api/items";
import { createRental, getAvailability } from "@/api/rentals";
import { ApiError, newIdempotencyKey } from "@/api/client";
import { ErrorState, Loading } from "@/components/ui/state";
import { fillPlace, parseIsoDate, UNKNOWN_OWNER, UNKNOWN_PLACE } from "@/utils/itemView";
import { useCommunityPlaces } from "@/hooks/useCommunityPlaces";
import { validateRequestRange } from "@/utils/requestRange";
import { toIsoDate } from "@/utils/registerForm";
import { fmtDate, rangeDays } from "@/utils/date";
import styles from "@/pages/items/RequestPage.module.css";
import { classes } from "@/utils/classes";
import { PageHeader } from "@/components/layout/header";

const c = classes(styles);

/** 대여 요청 — 실제 물건과 예약 구간 확인 후 생성 */
export function RequestPage() {
  const { itemId = "" } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { toast } = useUi();
  const places = useCommunityPlaces();
  const [itemData, setItem] = useState<ApiItemDetail | null>(null);
  const [booked, setBooked] = useState<DayRange[]>([]);
  const [blocked, setBlocked] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [retry, setRetry] = useState(0);
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const submission = useRef({ body: "", key: "" });
  const [today] = useState(() => parseIsoDate(new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date())));
  const [range, setRange] = useState<DayRange | null>(() => {
    const start = params.get("startDate") ?? "";
    const end = params.get("endDate") ?? "";
    if (![start, end].every((d) => /^\d{4}-\d{2}-\d{2}$/.test(d) && toIsoDate(parseIsoDate(d)) === d)) return null;
    return [parseIsoDate(start), parseIsoDate(end)];
  });

  useEffect(() => {
    let alive = true;
    async function load() {
      try {
        const item = await getItem(itemId);
        const ranges: DayRange[] = [];
        let overdue = false;
        let from = item.availableStartDate > toIsoDate(today) ? item.availableStartDate : toIsoDate(today);
        while (from <= item.availableEndDate) {
          const last = parseIsoDate(from);
          last.setDate(last.getDate() + 92);
          const to = toIsoDate(last) < item.availableEndDate ? toIsoDate(last) : item.availableEndDate;
          const availability = await getAvailability(itemId, from, to);
          overdue ||= availability.overdueBlocked;
          ranges.push(...availability.occupiedRanges.map((r): DayRange => [parseIsoDate(r.startDate), parseIsoDate(r.endDate)]));
          last.setTime(parseIsoDate(to).getTime());
          last.setDate(last.getDate() + 1);
          from = toIsoDate(last);
        }
        if (alive) { setItem(item); setBooked(ranges); setBlocked(overdue); setError(""); setLoaded(true); }
      } catch (e) {
        if (!alive) return;
        if (e instanceof ApiError && e.unauthenticated) { navigate("/login"); return; }
        setError(e instanceof ApiError ? e.message : "물건과 예약 정보를 불러오지 못했어요.");
      }
    }
    void load();
    return () => { alive = false; };
  }, [itemId, retry, navigate, today]);

  if (error) return <ErrorState onRetry={() => { setError(""); setRetry((n) => n + 1); }}>{error}</ErrorState>;
  if (!loaded || !itemData) return <Loading label="물건과 예약 정보 불러오는 중"><div className={c("request page-window")}>물건과 예약 정보를 불러오는 중이에요.</div></Loading>;
  const item = fillPlace(itemData, places);
  const start = range ? [range[0].getMonth() + 1, range[0].getDate(), range[0].getFullYear()] : [];
  const end = range ? [range[1].getMonth() + 1, range[1].getDate(), range[1].getFullYear()] : [];
  const label = range ? `${fmtDate(start)} – ${fmtDate(end)} · ${rangeDays(start, end)}일` : "";
  const { invalid, conflict } = validateRequestRange(range, today, parseIsoDate(item.availableStartDate), parseIsoDate(item.availableEndDate), booked);
  const unavailable = blocked || item.visibility !== "PUBLIC" || item.allowedActions.includes("EDIT");
  const submit = async () => {
    if (lock.current || invalid || conflict || unavailable || !range) return;
    lock.current = true;
    setBusy(true);
    setSubmitError("");
    const body = { itemId, startDate: toIsoDate(range[0]), endDate: toIsoDate(range[1]) };
    const signature = JSON.stringify(body);
    if (submission.current.body !== signature) submission.current = { body: signature, key: newIdempotencyKey() };
    try {
      const result = await createRental(body, submission.current.key);
      toast("대여 요청을 보냈어요 · 승인되면 알려드려요");
      navigate(result.chatRoomId
        ? `/chat/${encodeURIComponent(result.chatRoomId)}`
        : `/rentals?rentalId=${encodeURIComponent(result.rental.id)}`);
    } catch (e) {
      if (e instanceof ApiError && e.unauthenticated) navigate("/login");
      else setSubmitError(e instanceof ApiError ? e.message : "요청을 보내지 못했어요. 다시 시도해 주세요.");
    } finally { lock.current = false; setBusy(false); }
  };

  return (
    <div className={c("request page-window")}>
      <PageHeader title="대여 요청" backTo={`/items/${item.id}`} />
      <div className={c("request-card")}>
        <div className={c("t-body-lg")}>{item.title}</div>
        <div className={c("t-caption request-muted")}>{item.owner.displayName ?? UNKNOWN_OWNER} 님 · 무료</div>
      </div>

      <h2 className={c("t-title request-heading")}>대여 날짜</h2>
      <Calendar today={today} from={parseIsoDate(item.availableStartDate)} to={parseIsoDate(item.availableEndDate)} booked={booked} value={range} onChange={(value) => { if (!lock.current) { setRange(value); setSubmitError(""); } }} />

      {conflict ? <Alert tone="warning" title="선택한 날짜는 이미 예약됐어요">다른 날짜를 선택해 주세요.</Alert> : null}
      {invalid ? <Alert tone="info" title="대여 날짜를 선택해 주세요">오늘부터 대여 가능 기간 내 최대 30일까지 요청할 수 있어요.</Alert> : null}
      {unavailable ? <Alert tone="warning" title="지금은 대여 요청을 보낼 수 없어요">내 물건이거나 공개 중지·반납 지연 상태인 물건은 요청할 수 없어요.</Alert> : null}
      {submitError ? <Alert tone="warning" title="대여 요청을 보내지 못했어요">{submitError}</Alert> : null}

      <div className={c("request-card")}>
        <div className={c("request-row")}>
          <Icon name="calendar" size={16} />
          {range ? <DateRange start={start} end={end} icon={false} /> : "날짜를 선택해 주세요"}
        </div>
        <div className={c("request-row request-muted t-caption")}>
          <Icon name="pin" size={16} />
          {item.place.name ?? UNKNOWN_PLACE} · 집 주소는 공유되지 않아요
        </div>
      </div>

      <div className={c("request-actionbar")}>
        <Button
          size="lg"
          block
          disabled={busy || invalid || conflict || unavailable}
          onClick={submit}
        >
          {busy ? "요청 보내는 중" : range ? `${label}로 요청하기` : "대여 날짜 선택"}
        </Button>
      </div>
    </div>
  );
}
