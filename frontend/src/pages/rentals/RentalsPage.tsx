import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { getRentals, getRental, commandRental, type RentalAction } from "@/api/rentals";
import { ApiError } from "@/api/client";
import { getMyProfile } from "@/api/members";
import { Chip } from "@/components/ui/chip";
import { RentalCard } from "@/components/custom/rentals/RentalCard";
import { ChipScroller } from "@/components/ui/chip-scroller";
import { EmptyState, ErrorState, Loading, Skeleton } from "@/components/ui/state";
import { Button } from "@/components/ui/button";
import { useUi } from "@/components/overlay/UiContext";
import type { ApiRental, ApiRentalDetail, RentalRole } from "@/types/rental-api";
import { includeRental, matchesRental, rentalTab, toRental, type RentalTab, type RentalFilter } from "@/utils/rentalView";
import styles from "@/pages/rentals/RentalsPage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);
const FILTERS: Record<RentalTab, { id: RentalFilter; label: string }[]> = {
  borrowed: [
    { id: "all", label: "전체" },
    { id: "requested", label: "요청" },
    { id: "approved", label: "승인" },
    { id: "active", label: "대여 중" },
    { id: "overdue", label: "연체" },
    { id: "done", label: "완료·거절" },
  ],
  lent: [
    { id: "all", label: "전체" },
    { id: "requested", label: "요청" },
    { id: "approved", label: "승인" },
    { id: "active", label: "대여 중" },
    { id: "overdue", label: "연체" },
    { id: "done", label: "완료·거절" },
  ],
};


async function loadRentals(role: RentalRole) {
  const items: ApiRental[] = [];
  let page = 0;
  while (true) {
    const result = await getRentals(role, page++);
    items.push(...result.items);
    if (!result.hasNext) return items;
  }
}

export function RentalsPage() {
  const [params] = useSearchParams();
  const rentalId = params.get("rentalId") ?? "";
  return <RentalList key={rentalId} rentalId={rentalId} />;
}

function RentalList({ rentalId }: { rentalId: string }) {
  const navigate = useNavigate();
  const { toast } = useUi();
  const [tab, setTab] = useState<RentalTab>("borrowed");
  const [filter, setFilter] = useState<RentalFilter>("all");
  const [lists, setLists] = useState<Record<RentalTab, ApiRental[]>>({ borrowed: [], lent: [] });
  const [details, setDetails] = useState<Record<string, ApiRentalDetail>>({});
  const [detailErrors, setDetailErrors] = useState<Record<string, boolean>>({});
  const [refreshing, setRefreshing] = useState<Record<string, boolean>>({});
  const [targetTab, setTargetTab] = useState<RentalTab>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [targetError, setTargetError] = useState<"missing" | "error">();
  const [retry, setRetry] = useState(0);
  const [busy, setBusy] = useState(false);
  const commandPending = useRef(false);
  const targetRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let active = true;
    const listRequest = Promise.all([loadRentals("BORROWER"), loadRentals("OWNER")])
      .then(async ([borrowed, lent]) => {
        if (!active) return;
        setLists({ borrowed, lent });
        const ids = [...new Set([...borrowed, ...lent].map((rental) => rental.id))];
        // 상세 조회 동시 실행 수 제한 및 개별 실패 격리.
        for (let offset = 0; active && offset < ids.length; offset += 6) {
          await Promise.all(ids.slice(offset, offset + 6).map(async (id) => {
            try {
              const detail = await getRental(id);
              if (active) {
                setDetails((previous) => ({ ...previous, [id]: detail }));
                setDetailErrors((previous) => ({ ...previous, [id]: false }));
              }
            } catch {
              if (active) setDetailErrors((previous) => ({ ...previous, [id]: true }));
            }
          }));
        }
      })
      .catch(() => { if (active) setError(true); });
    const targetRequest = rentalId ? Promise.all([getRental(rentalId), getMyProfile()])
      .then(([detail, profile]) => {
        if (!active) return;
        const nextTab = rentalTab(detail.rental, profile.member.id);
        setDetails((previous) => ({ ...previous, [rentalId]: detail }));
        setTargetTab(nextTab);
        setTab(nextTab);
        setFilter("all");
      }).catch((failure: unknown) => {
        if (active) setTargetError(failure instanceof ApiError && failure.status === 404 ? "missing" : "error");
      }) : Promise.resolve();
    void Promise.all([listRequest, targetRequest]).then(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [rentalId, retry]);

  useEffect(() => {
    if (!loading && targetTab === tab && filter === "all") {
      targetRef.current?.scrollIntoView({ block: "center" });
      targetRef.current?.focus({ preventScroll: true });
    }
  }, [loading, targetTab, tab, filter]);

  const retryLoad = () => {
    setLoading(true);
    setError(false);
    setTargetError(undefined);
    setRetry((value) => value + 1);
  };
  const target = !targetError && targetTab === tab ? details[rentalId]?.rental : undefined;
  const data = includeRental(lists[tab], target).map((rental) => toRental(rental, details[rental.id]));
  const visible = data.filter((rental) => matchesRental(rental, filter));
  const count = (value: RentalTab) => includeRental(lists[value], !targetError && targetTab === value ? details[rentalId]?.rental : undefined).length;
  const applyDetail = (detail: ApiRentalDetail) => {
    const id = detail.rental.id;
    setDetails((previous) => ({ ...previous, [id]: detail }));
    setDetailErrors((previous) => ({ ...previous, [id]: false }));
    setLists((previous) => ({
      borrowed: previous.borrowed.map((entry) => entry.id === id ? detail.rental : entry),
      lent: previous.lent.map((entry) => entry.id === id ? detail.rental : entry),
    }));
  };
  const refreshDetail = async (id: string) => {
    setRefreshing((previous) => ({ ...previous, [id]: true }));
    try {
      applyDetail(await getRental(id));
    } catch {
      setDetailErrors((previous) => ({ ...previous, [id]: true }));
    } finally {
      setRefreshing((previous) => ({ ...previous, [id]: false }));
    }
  };
  const onAction = async (id: string, action: RentalAction, reason?: string) => {
    if (commandPending.current || detailErrors[id] || refreshing[id]) return;
    const rental = details[id]?.rental ?? [...lists.borrowed, ...lists.lent].find((entry) => entry.id === id);
    if (!rental) return;
    commandPending.current = true;
    setBusy(true);
    try {
      const detail = await commandRental(id, action, rental.version, reason);
      applyDetail(detail);
      toast("대여 상태를 변경했어요");
    } catch (failure) {
      toast(failure instanceof ApiError ? failure.message : "처리하지 못했어요. 다시 시도해 주세요.");
      if (failure instanceof ApiError && failure.status === 409) await refreshDetail(id);
    } finally {
      commandPending.current = false;
      setBusy(false);
    }
  };

  return (
    <div className={c("rentals")}>
      <h1 className={c("t-title-lg only-desk rentals-title")}>내 대여</h1>
      <div className={c("rentals-tabs")} role="tablist" aria-label="대여 구분">
        {(["borrowed", "lent"] as const).map((value) => (
          <button key={value} type="button" role="tab" aria-selected={tab === value} className={c("rentals-tab")}
            onClick={() => { setTab(value); setFilter("all"); }}>
            {value === "borrowed" ? "빌린 물건" : "빌려준 물건"} <span className={c("rentals-muted")}>{loading || error ? "–" : count(value)}</span>
          </button>
        ))}
      </div>
      <ChipScroller label="상태 필터">
        {FILTERS[tab].map((entry) => <Chip key={entry.id} selected={filter === entry.id}
          count={loading || error ? undefined : data.filter((rental) => matchesRental(rental, entry.id)).length}
          onClick={() => setFilter(entry.id)}>{entry.label}</Chip>)}
      </ChipScroller>
      <div role="tabpanel" className={c("rentals-list")}>
        {loading ? <Loading label="내 대여 불러오는 중">{[0, 1].map((i) => (
          <div key={i} className={c("rentals-skel")}>
            <div className={c("rentals-skel-top")}><Skeleton w={64} h={64} r={12} />
              <div className={c("rentals-skel-lines")}><Skeleton w="60%" h={16} /><Skeleton w="40%" /></div>
            </div><Skeleton h={64} r={12} /><Skeleton h={44} r={12} />
          </div>))}</Loading> : <>
          {targetError === "missing" ? <EmptyState icon="alert" title="해당 거래를 찾을 수 없어요">삭제되었거나 조회할 수 없는 거래예요.</EmptyState> : null}
          {targetError === "error" ? <ErrorState title="해당 거래를 불러오지 못했어요" onRetry={retryLoad} /> : null}
          {error ? <ErrorState title="대여 목록을 불러오지 못했어요" onRetry={retryLoad} /> : null}
          {visible.map((rental) => <div key={rental.id} ref={rental.id === rentalId ? targetRef : undefined}
            tabIndex={rental.id === rentalId ? -1 : undefined} aria-label={rental.id === rentalId ? "알림에서 선택한 거래" : undefined}>
            <RentalCard rental={rental} role={tab === "lent" ? "owner" : "borrower"}
              busy={busy || refreshing[rental.id] || detailErrors[rental.id]}
              onAction={(action, reason) => { void onAction(rental.id, action, reason); }} />
            {detailErrors[rental.id] ? <ErrorState title="해당 거래를 불러오지 못했어요"
              onRetry={() => { if (!refreshing[rental.id]) void refreshDetail(rental.id); }} /> : null}
          </div>)}
          {!error && visible.length === 0 ? <EmptyState icon={tab === "lent" ? "grid" : "swap"}
            title={filter !== "all" ? "해당 상태의 거래가 없어요" : tab === "lent" ? "빌려준 물건이 아직 없어요" : "빌린 물건이 아직 없어요"}
            actions={filter !== "all" ? <Button onClick={() => setFilter("all")}>필터 초기화</Button> : <Button icon={tab === "lent" ? "plus" : "search"} onClick={() => navigate(tab === "lent" ? "/register" : "/search")}>
              {tab === "lent" ? "물건 등록하기" : "물건 찾아보기"}</Button>}>
            {filter !== "all" ? "다른 상태를 선택해 보세요." : tab === "lent" ? "가끔 쓰는 물건을 올려 두면 이웃이 요청을 보내요." : "필요한 날짜를 정하고 이웃의 물건을 찾아보세요."}
          </EmptyState> : null}
        </>}
      </div>
    </div>
  );
}
