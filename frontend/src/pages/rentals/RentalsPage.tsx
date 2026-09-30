import { useState } from "react";
import { Chip } from "@/components/ui/chip";
import { RentalCard } from "@/components/custom/rentals/RentalCard";
import { BORROWED, LENT, type Rental } from "@/data/rentals";
import type { RentalStatusView } from "@/components/custom/rentals/status";
import styles from "@/pages/rentals/RentalsPage.module.css";
import { classes } from "@/utils/classes";
import { ChipScroller } from "@/components/ui/chip-scroller";
import { EmptyState, ErrorState, Loading, Skeleton } from "@/components/ui/state";
import { useScreenState } from "@/hooks/useScreenState";
import { Button } from "@/components/ui/button";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Alert } from "@/components/ui/alert";
import { useUi } from "@/components/overlay/UiContext";

const c = classes(styles);

type Tab = "borrowed" | "lent";
type Filter = "all" | "requested" | "approved" | "active" | "overdue" | "done";

/** 완료·거절 묶음 — 종료 상태들 (정책 및 상태 §2) */
const DONE_STATUSES: RentalStatusView[] = ["returned", "rejected", "canceled"];

function matches(rental: Rental, filter: Filter) {
  if (filter === "all") return true;
  if (filter === "done") return DONE_STATUSES.includes(rental.status);
  return rental.status === filter;
}

/** 탭별 필터 구성 — 빌려준 쪽에만 연체 칩 (프로토타입 기준) */
const FILTERS: Record<Tab, { id: Filter; label: string }[]> = {
  borrowed: [
    { id: "all", label: "전체" },
    { id: "requested", label: "요청" },
    { id: "approved", label: "승인" },
    { id: "active", label: "대여 중" },
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

export function RentalsPage() {
  const state = useScreenState();
  const navigate = useNavigate();
  const { toast } = useUi();
  const [params] = useSearchParams();
  /* 승인 충돌 — 같은 물건의 겹치는 기간은 한 건만 승인 (정책 및 상태.md) */
  const conflict = params.get("state") === "conflict";
  const [tab, setTab] = useState<Tab>("borrowed");
  const [filter, setFilter] = useState<Filter>("all");
  const data = tab === "lent" ? LENT : BORROWED;
  const visible = data.filter((r) => matches(r, filter));

  const selectTab = (next: Tab) => {
    setTab(next);
    setFilter("all");
  };

  return (
    <div className={c("rentals")}>
      <h1 className={c("t-title-lg only-desk rentals-title")}>내 대여</h1>

      <div className={c("rentals-tabs")} role="tablist" aria-label="대여 구분">
        <button type="button" role="tab" aria-selected={tab === "borrowed"} className={c("rentals-tab")} onClick={() => selectTab("borrowed")}>
          빌린 물건 <span className={c("rentals-muted")}>{BORROWED.length}</span>
        </button>
        <button type="button" role="tab" aria-selected={tab === "lent"} className={c("rentals-tab")} onClick={() => selectTab("lent")}>
          빌려준 물건 <span className={c("rentals-muted")}>{LENT.length}</span>
        </button>
      </div>

      <ChipScroller label="상태 필터">
        {FILTERS[tab].map((f) => (
          <Chip
            key={f.id}
            selected={filter === f.id}
            count={data.filter((r) => matches(r, f.id)).length}
            onClick={() => setFilter(f.id)}
          >
            {f.label}
          </Chip>
        ))}
      </ChipScroller>

      <div role="tabpanel" className={c("rentals-list")}>
        {state === "loading" ? (
          <Loading label="내 대여 불러오는 중">
            {[0, 1].map((i) => (
              <div key={i} className={c("rentals-skel")}>
                <div className={c("rentals-skel-top")}>
                  <Skeleton w={64} h={64} r={12} />
                  <div className={c("rentals-skel-lines")}>
                    <Skeleton w="60%" h={16} />
                    <Skeleton w="40%" />
                  </div>
                </div>
                <Skeleton h={64} r={12} />
                <Skeleton h={44} r={12} />
              </div>
            ))}
          </Loading>
        ) : state === "error" ? (
          <ErrorState title="대여 목록을 불러오지 못했어요">
            진행 중인 대여는 그대로 유지되고 있어요. 잠시 후 다시 시도해 주세요.
          </ErrorState>
        ) : state === "empty" || visible.length === 0 ? (
          tab === "lent" ? (
            <EmptyState
              icon="grid"
              title="빌려준 물건이 아직 없어요"
              actions={<Button icon="plus" onClick={() => navigate("/register")}>물건 등록하기</Button>}
            >
              가끔 쓰는 물건을 올려 두면 이웃이 요청을 보내요.
            </EmptyState>
          ) : (
            <EmptyState
              icon="swap"
              title="빌린 물건이 아직 없어요"
              actions={<Button icon="search" onClick={() => navigate("/search")}>물건 찾아보기</Button>}
            >
              필요한 날짜를 정하고 이웃의 물건을 찾아보세요.
            </EmptyState>
          )
        ) : (
          <>
            {conflict ? (
              <Alert tone="info" title="지민 님의 요청을 승인했어요">
                9/26(토) 정문 경비실 옆 벤치에서 전달해 주세요.
              </Alert>
            ) : null}
            {visible.map((r, i) => (
              <RentalCard
                key={r.id}
                rental={r}
                role={tab === "lent" ? "owner" : "borrower"}
                conflict={
                  conflict && i === 0 ? (
                    <Alert
                      tone="danger"
                      title="승인할 수 없어요 · 날짜가 겹쳐요"
                      actions={
                        <>
                          <Button size="sm" onClick={() => toast("거절 사유를 보냈어요")}>거절하고 이유 보내기</Button>
                          <Button size="sm" variant="secondary" onClick={() => toast("겹치는 대여 일정을 표시했어요")}>
                            겹치는 대여 보기
                          </Button>
                        </>
                      }
                    >
                      방금 승인한 지민 님의 대여(9/26 – 9/28)와 9/27 – 9/28이 겹쳐요. 같은 물건은 겹치는 기간에 한 건만
                      승인할 수 있어요.
                    </Alert>
                  ) : undefined
                }
              />
            ))}
          </>
        )}
      </div>
    </div>
  );
}
