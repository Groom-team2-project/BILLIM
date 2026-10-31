import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { getRentals, getRental, getRentalSummary, commandRental, type RentalAction } from "@/api/rentals";
import { ApiError } from "@/api/client";
import { getMyProfile } from "@/api/members";
import { Chip } from "@/components/ui/chip";
import { RentalCard } from "@/components/custom/rentals/RentalCard";
import { ChipScroller } from "@/components/ui/chip-scroller";
import { EmptyState, ErrorState, Loading, Skeleton } from "@/components/ui/state";
import { Button } from "@/components/ui/button";
import { useUi } from "@/components/overlay/UiContext";
import type { ApiRental, ApiRentalDetail, ApiMyRentalSummary, RentalRole } from "@/types/rental-api";
import { includeRental, matchesRental, rentalTab, rentalFilterCount, toRental, type RentalTab, type RentalFilter } from "@/utils/rentalView";
import { loadRentalPage, rentalCursor } from "@/utils/rentalPaging";
import styles from "@/pages/rentals/RentalsPage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);
const FILTERS: { id: RentalFilter; label: string }[] = [
  { id: "all", label: "전체" }, { id: "requested", label: "요청" },
  { id: "approved", label: "승인" }, { id: "active", label: "대여 중" },
  { id: "overdue", label: "연체" }, { id: "done", label: "완료·거절" },
];

export function RentalsPage() {
  const [params] = useSearchParams();
  const rentalId = params.get("rentalId") ?? "";
  return <RentalList key={rentalId} rentalId={rentalId} />;
}

function RentalList({ rentalId }: { rentalId: string }) {
  const [tab, setTab] = useState<RentalTab>("borrowed");
  const [filter, setFilter] = useState<RentalFilter>("all");
  const [summary, setSummary] = useState<ApiMyRentalSummary>();
  const [summaryError, setSummaryError] = useState(false);
  const summaryGeneration = useRef(0);
  const [target, setTarget] = useState<ApiRentalDetail>();
  const [targetTab, setTargetTab] = useState<RentalTab>();
  const [targetLoading, setTargetLoading] = useState(Boolean(rentalId));
  const [targetError, setTargetError] = useState<"missing" | "error">();
  const [targetRetry, setTargetRetry] = useState(0);
  const reloadSummary = useCallback(async () => {
    const generation = ++summaryGeneration.current;
    try {
      const result = await getRentalSummary();
      if (generation !== summaryGeneration.current) return;
      setSummary(result);
      setSummaryError(false);
    } catch {
      if (generation === summaryGeneration.current) setSummaryError(true);
    }
  }, []);

  useEffect(() => {
    const epoch = ++summaryGeneration.current;
    let active = true;
    void getRentalSummary().then((result) => {
      if (active && epoch === summaryGeneration.current) setSummary(result);
    }).catch(() => {
      if (active && epoch === summaryGeneration.current) setSummaryError(true);
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!rentalId) return;
    let active = true;
    void Promise.all([getRental(rentalId), getMyProfile()]).then(([detail, profile]) => {
      if (!active) return;
      setTarget(detail);
      const selectedTab = rentalTab(detail.rental, profile.member.id);
      setTargetTab(selectedTab);
      setTab(selectedTab);
      setFilter("all");
    }).catch((failure: unknown) => {
      if (active) setTargetError(failure instanceof ApiError && failure.status === 404 ? "missing" : "error");
    }).finally(() => { if (active) setTargetLoading(false); });
    return () => { active = false; };
  }, [rentalId, targetRetry]);

  const count = (value: RentalTab, selected: RentalFilter) => summary && !summaryError
    ? rentalFilterCount(summary[value], selected) : undefined;
  const changed = (detail: ApiRentalDetail) => {
    if (detail.rental.id === rentalId) setTarget(detail);
    void reloadSummary();
  };
  return (
    <div className={c("rentals")}>
      <h1 className={c("t-title-lg only-desk rentals-title")}>내 대여</h1>
      <div className={c("rentals-tabs")} role="tablist" aria-label="대여 구분">
        {(["borrowed", "lent"] as const).map((value) => (
          <button key={value} type="button" role="tab" aria-selected={tab === value} className={c("rentals-tab")}
            onClick={() => { setTab(value); setFilter("all"); }}>
            {value === "borrowed" ? "빌린 물건" : "빌려준 물건"} <span className={c("rentals-muted")}>{count(value, "all") ?? "–"}</span>
          </button>
        ))}
      </div>
      <ChipScroller label="상태 필터">
        {FILTERS.map((entry) => <Chip key={entry.id} selected={filter === entry.id} count={count(tab, entry.id)}
          onClick={() => setFilter(entry.id)}>{entry.label}</Chip>)}
      </ChipScroller>
      {summaryError ? <ErrorState title="대여 건수를 불러오지 못했어요" onRetry={() => { void reloadSummary(); }} /> : null}
      {targetError === "missing" ? <EmptyState icon="alert" title="해당 거래를 찾을 수 없어요">삭제되었거나 조회할 수 없는 거래예요.</EmptyState> : null}
      {targetError === "error" ? <ErrorState title="해당 거래를 불러오지 못했어요" onRetry={() => {
        setTargetLoading(true); setTargetError(undefined); setTargetRetry((value) => value + 1);
      }} /> : null}
      {targetLoading ? <RentalSkeleton /> : <RentalResults key={`${tab}:${filter}`} role={tab === "lent" ? "OWNER" : "BORROWER"}
        filter={filter} target={!targetError && targetTab === tab ? target : undefined}
        onResetFilter={() => setFilter("all")} onChanged={changed} />}
    </div>
  );
}

function RentalSkeleton() {
  return <Loading label="내 대여 불러오는 중">{[0, 1].map((i) => (
    <div key={i} className={c("rentals-skel")}>
      <div className={c("rentals-skel-top")}><Skeleton w={64} h={64} r={12} />
        <div className={c("rentals-skel-lines")}><Skeleton w="60%" h={16} /><Skeleton w="40%" /></div>
      </div><Skeleton h={64} r={12} /><Skeleton h={44} r={12} />
    </div>))}</Loading>;
}

function RentalResults({ role, filter, target, onChanged, onResetFilter }: {
  role: RentalRole; filter: RentalFilter; target?: ApiRentalDetail;
  onChanged: (detail: ApiRentalDetail) => void;
  onResetFilter: () => void;
}) {
  const navigate = useNavigate();
  const { toast } = useUi();
  const [items, setItems] = useState<ApiRental[]>([]);
  const [details, setDetails] = useState<Record<string, ApiRentalDetail>>(target ? { [target.rental.id]: target } : {});
  const [detailErrors, setDetailErrors] = useState<Record<string, boolean>>({});
  const [refreshing, setRefreshing] = useState<Record<string, boolean>>({});
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(false);
  const [hasNext, setHasNext] = useState(false);
  const [busy, setBusy] = useState(false);
  const cursor = useRef(rentalCursor(filter));
  const generation = useRef({ active: true });
  const pagePending = useRef(false);
  const commandPending = useRef(false);
  const targetRef = useRef<HTMLDivElement>(null);

  const hydrate = async (rentals: ApiRental[], epoch: { active: boolean }) => {
    for (let offset = 0; epoch.active && generation.current === epoch && offset < rentals.length; offset += 6) {
      await Promise.all(rentals.slice(offset, offset + 6).filter((rental) => rental.id !== target?.rental.id).map(async (rental) => {
        try {
          const detail = await getRental(rental.id);
          if (epoch.active && generation.current === epoch) setDetails((previous) => ({ ...previous, [rental.id]: detail }));
        } catch {
          if (epoch.active && generation.current === epoch) setDetailErrors((previous) => ({ ...previous, [rental.id]: true }));
        }
      }));
    }
  };
  const loadNext = async (epoch = generation.current) => {
    if (pagePending.current || !epoch.active) return;
    pagePending.current = true;
    setLoadingMore(true);
    setError(false);
    try {
      const page = await loadRentalPage(role, filter, cursor.current, getRentals);
      if (!epoch.active || generation.current !== epoch) return;
      cursor.current = page.cursor;
      setItems((previous) => [...previous, ...page.items.filter((rental) => !previous.some((entry) => entry.id === rental.id))]);
      setHasNext(page.hasNext);
      setLoading(false);
      await hydrate(page.items, epoch);
    } catch {
      if (epoch.active && generation.current === epoch) setError(true);
    } finally {
      if (epoch.active && generation.current === epoch) {
        pagePending.current = false;
        setLoading(false);
        setLoadingMore(false);
      }
    }
  };
  useEffect(() => {
    const epoch = { active: true };
    generation.current = epoch;
    pagePending.current = false;
    void Promise.resolve().then(() => loadNext(epoch));
    return () => { epoch.active = false; };
    // 탭·필터별 key로 결과 상태를 분리. 이전 화면의 늦은 응답 반영 차단.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!loading && filter === "all") {
      targetRef.current?.scrollIntoView({ block: "center" });
      targetRef.current?.focus({ preventScroll: true });
    }
  }, [loading, filter]);

  const applyDetail = (detail: ApiRentalDetail) => {
    const id = detail.rental.id;
    setDetails((previous) => ({ ...previous, [id]: detail }));
    setDetailErrors((previous) => ({ ...previous, [id]: false }));
    setItems((previous) => previous.map((entry) => entry.id === id ? detail.rental : entry));
  };
  const refreshDetail = async (id: string) => {
    const epoch = generation.current;
    setRefreshing((previous) => ({ ...previous, [id]: true }));
    try {
      const detail = await getRental(id);
      if (epoch.active && generation.current === epoch) {
        applyDetail(detail);
        onChanged(detail);
      }
    } catch {
      if (epoch.active && generation.current === epoch) setDetailErrors((previous) => ({ ...previous, [id]: true }));
    } finally {
      if (epoch.active && generation.current === epoch) setRefreshing((previous) => ({ ...previous, [id]: false }));
    }
  };
  const onAction = async (id: string, action: RentalAction, reason?: string) => {
    if (commandPending.current || !details[id] || detailErrors[id] || refreshing[id]) return;
    const epoch = generation.current;
    commandPending.current = true;
    setBusy(true);
    try {
      const detail = await commandRental(id, action, details[id].rental.version, reason);
      // 탭 전환 뒤 완료된 명령도 전체 건수와 알림 대상 거래에는 반영.
      onChanged(detail);
      if (!epoch.active || generation.current !== epoch) return;
      applyDetail(detail);
      toast("대여 상태를 변경했어요");
    } catch (failure) {
      if (!epoch.active || generation.current !== epoch) return;
      toast(failure instanceof ApiError ? failure.message : "처리하지 못했어요. 다시 시도해 주세요.");
      if (failure instanceof ApiError && failure.status === 409) await refreshDetail(id);
    } finally {
      if (epoch.active && generation.current === epoch) {
        commandPending.current = false;
        setBusy(false);
      }
    }
  };
  const visible = includeRental(items, target?.rental).map((rental) => toRental(rental, details[rental.id])).filter((rental) => matchesRental(rental, filter));
  return <div role="tabpanel" className={c("rentals-list")}>
    {loading && !target ? <RentalSkeleton /> : null}
    {visible.map((rental) => <div key={rental.id} ref={rental.id === target?.rental.id ? targetRef : undefined}
      tabIndex={rental.id === target?.rental.id ? -1 : undefined} aria-label={rental.id === target?.rental.id ? "알림에서 선택한 거래" : undefined}>
      <RentalCard rental={rental} role={role === "OWNER" ? "owner" : "borrower"}
        busy={busy || !details[rental.id] || refreshing[rental.id] || detailErrors[rental.id]}
        onAction={(action, reason) => { void onAction(rental.id, action, reason); }} />
      {!details[rental.id] && !detailErrors[rental.id] ? <Loading label="거래 상세 불러오는 중"><Skeleton h={16} /></Loading> : null}
      {detailErrors[rental.id] ? <ErrorState title="해당 거래를 불러오지 못했어요"
        onRetry={() => { if (!refreshing[rental.id]) void refreshDetail(rental.id); }} /> : null}
    </div>)}
    {error ? <ErrorState title="대여 목록을 불러오지 못했어요" onRetry={() => { void loadNext(); }} /> : null}
    {!loading && !error && hasNext ? <Button variant="secondary" disabled={loadingMore}
      onClick={() => { void loadNext(); }}>{loadingMore ? "불러오는 중…" : "더 보기"}</Button> : null}
    {!loading && !error && visible.length === 0 ? <EmptyState icon={role === "OWNER" ? "grid" : "swap"}
      title={filter !== "all" ? "해당 상태의 거래가 없어요" : role === "OWNER" ? "빌려준 물건이 아직 없어요" : "빌린 물건이 아직 없어요"}
      actions={filter !== "all" ? <Button onClick={onResetFilter}>필터 초기화</Button> : <Button icon={role === "OWNER" ? "plus" : "search"} onClick={() => navigate(role === "OWNER" ? "/register" : "/search")}>
        {role === "OWNER" ? "물건 등록하기" : "물건 찾아보기"}</Button>}>
      {filter !== "all" ? "다른 상태를 선택해 보세요." : role === "OWNER" ? "가끔 쓰는 물건을 올려 두면 이웃이 요청을 보내요." : "필요한 날짜를 정하고 이웃의 물건을 찾아보세요."}
    </EmptyState> : null}
  </div>;
}
