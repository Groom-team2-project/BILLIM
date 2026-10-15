import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { PageHeader } from "@/components/layout/header";
import { SearchField } from "@/components/ui/search-field";
import { Alert } from "@/components/ui/alert";
import { EmptyState, ErrorState, Loading, SkeletonItem } from "@/components/ui/state";
import { ApiError } from "@/api/client";
import { type ApiCommunity, findNearbyCommunities, searchCommunities } from "@/api/communities";
import { GeoError, getCurrentCoords } from "@/utils/geolocation";
import styles from "@/pages/onboarding/JoinPage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type Result =
  | { state: "loading" }
  | { state: "error"; message: string }
  | { state: "ok"; items: ApiCommunity[]; truncated: boolean };

/** 목록 문구: "표시 주소 · 이웃 N명" */
function describe(community: ApiCommunity): string {
  return `${community.displayAddress} · 이웃 ${community.verifiedMemberCount}명`;
}

export function JoinPage() {
  const [keyword, setKeyword] = useState("");
  const [query, setQuery] = useState("");
  const [result, setResult] = useState<Result>({ state: "loading" });
  const [locating, setLocating] = useState(false);
  const [locateError, setLocateError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);

  // 이름 검색 — 처음에는 전체 활성 동네를 보여준다 (위치 없이 검색 가능, E_009)
  // 로딩 표시는 요청을 일으킨 이벤트에서 세운다. 효과 안에서 setState하면 렌더가 한 번 더 돈다
  useEffect(() => {
    let alive = true;
    searchCommunities(query || undefined)
      .then((page) => {
        if (alive) setResult({ state: "ok", items: page.items, truncated: false });
      })
      .catch((e: unknown) => {
        if (!alive) return;
        const message = e instanceof ApiError ? e.message : "동네를 불러오지 못했어요.";
        setResult({ state: "error", message });
      });
    return () => {
      alive = false;
    };
  }, [query, retry]);

  /** 현재 위치로 주변 동네 찾기 (E_071). 이 조회 자체는 인증이 아니다 */
  const onLocate = async () => {
    setLocating(true);
    setLocateError(null);
    try {
      const coords = await getCurrentCoords();
      const nearby = await findNearbyCommunities(coords);
      setKeyword("");
      setQuery("");
      setResult({ state: "ok", items: nearby.items, truncated: nearby.truncated });
    } catch (e: unknown) {
      if (e instanceof GeoError) setLocateError(e.message);
      else if (e instanceof ApiError) setLocateError(e.message);
      else setLocateError("주변 동네를 찾지 못했어요.");
    } finally {
      setLocating(false);
    }
  };

  return (
    <main className={c("join")}>
      <PageHeader title="우리 동네 찾기" backTo="/login" standalone />
      <div className={c("join-body")}>
        <div>
          <h1 className={c("t-title-lg join-title")}>어느 동네에 사세요?</h1>
          <p className={c("t-body join-muted join-desc")}>
            실제 사는 동네(단지)에서만 이웃과 물건을 주고받아요. 먼저 우리 동네를 찾아 주세요.
          </p>
        </div>

        <SearchField
          placeholder="동네·단지 이름으로 검색"
          label="동네 검색"
          value={keyword}
          onChange={setKeyword}
          onSubmit={() => {
            setResult({ state: "loading" });
            setQuery(keyword.trim());
          }}
        />

        <button type="button" className={c("join-locate t-label")} onClick={onLocate} disabled={locating}>
          <Icon name="pin" size={18} />
          {locating ? "위치 확인 중…" : "현재 위치로 우리 동네 찾기"}
        </button>

        {locateError && (
          <Alert tone="warning" title="위치로 찾지 못했어요">
            {locateError}
          </Alert>
        )}

        <p className={c("t-label join-results-label")}>검색 결과</p>

        {result.state === "loading" && (
          <Loading label="동네를 불러오는 중">
            <div className={c("join-card")}>
              <SkeletonItem layout="row" />
              <SkeletonItem layout="row" />
            </div>
          </Loading>
        )}

        {result.state === "error" && (
          <ErrorState
            onRetry={() => {
              setResult({ state: "loading" });
              setRetry((n) => n + 1);
            }}
          >
            {result.message}
          </ErrorState>
        )}

        {result.state === "ok" && result.items.length === 0 && (
          <EmptyState icon="search" title="찾는 동네가 없어요">
            이름을 다시 확인하거나 현재 위치로 찾아 주세요.
          </EmptyState>
        )}

        {result.state === "ok" && result.items.length > 0 && (
          <div className={c("join-card")}>
            {result.items.map((community) => (
              <Link
                key={community.id}
                to={`/verify?communityId=${community.id}`}
                className={c("join-candidate")}
              >
                <span className={c("join-candidate-icon")}>
                  <Icon name="pin" size={20} />
                </span>
                <span className={c("join-candidate-body")}>
                  <span className={c("t-body join-candidate-name")}>{community.name}</span>
                  <span className={c("t-caption join-muted")}>{describe(community)}</span>
                </span>
                <Icon name="chevronR" size={18} />
              </Link>
            ))}
          </div>
        )}

        {result.state === "ok" && result.truncated && (
          <p className={c("t-caption join-muted")}>
            가까운 동네 20개만 보여줘요. 찾는 동네가 없으면 이름으로 검색해 주세요.
          </p>
        )}

        <Alert tone="info" title="동네는 실제 사는 곳으로">
          다음 단계에서 위치로 한 번 확인해요. 정확한 위치나 집 주소는 저장하지 않아요.
        </Alert>
      </div>
    </main>
  );
}
