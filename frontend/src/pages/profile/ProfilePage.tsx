import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { logout, syncDisplayName } from "@/stores/session";
import { ApiError } from "@/api/client";
import {
  type ApiMyProfile,
  getMyItems,
  getMyProfile,
  updateDisplayName,
} from "@/api/members";
import { getMyBlocks } from "@/api/blocks";
import { type ApiMySanction, getMyReports, getMySanctions } from "@/api/reports";
import { type ApiItemSummary, changeItemVisibility, deleteItem } from "@/api/items";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Alert } from "@/components/ui/alert";
import { useUi } from "@/components/overlay/UiContext";
import { LiveTrustLines } from "@/components/custom/profile/LiveTrustLines";
import { ErrorState, Loading, Skeleton, SkeletonItem } from "@/components/ui/state";
import { PageHeader } from "@/components/layout/header";
import { ProfileHeading, ProfileHeadingSkeleton } from "@/components/custom/profile/ProfileHeading";
import { ProfileItemRow } from "@/components/custom/profile/ProfileItemRow";
import { ProfileLinkRow } from "@/components/custom/profile/ProfileLinkRow";
import { describeMembership } from "@/utils/profileView";
import shared from "@/components/custom/profile/profileBlocks.module.css";
import styles from "@/pages/profile/ProfilePage.module.css";
import { classes } from "@/utils/classes";
import { objectParticle } from "@/utils/korean";

const c = classes({ ...shared, ...styles });

/** getMyItems 기본 size와 동일 — 다음 페이지 번호 계산 기준 */
const PAGE_SIZE = 20;

/** 적용 중인 조치 문구. 조치 알림과 같은 말을 쓴다 */
const RESTRICTION_LABEL: Record<ApiMySanction["action"], string> = {
  WARN: "경고",
  SUSPEND_7D: "7일 이용 정지",
  SUSPEND_30D: "30일 이용 정지",
  BAN: "영구 정지",
};

function untilLabel(iso: string): string {
  const at = new Date(iso);
  return `${at.getMonth() + 1}월 ${at.getDate()}일`;
}


export function ProfilePage() {
  const { confirm, reason, toast } = useUi();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<ApiMyProfile | null>(null);

  // 조회·이름 변경 모두 profile 갱신을 거침 — 헤더 아바타와 맞춤
  useEffect(() => {
    if (profile) syncDisplayName(profile.member.displayName);
  }, [profile]);
  const [failed, setFailed] = useState(false);
  /** 조회 실패와 0건 구분 — 빈 목록 표시는 물건이 사라진 것으로 오해 */
  const [itemList, setItemList] = useState<
    { state: "loading" } | { state: "ok"; rows: ApiItemSummary[]; hasNext: boolean } | { state: "error" }
  >({ state: "loading" });
  const [loadingMore, setLoadingMore] = useState(false);
  /** null은 하위 데이터 로딩 중 — 0건과 구분해야 빈 화면 오해 방지 */
  const [details, setDetails] = useState<{
    blocks: number;
    reports: number;
    sanctions: number;
  } | null>(null);
  /** 지금 이용을 제한하는 조치. 제한된 이유를 프로필에서 바로 알 수 있어야 한다 (E_072) */
  const [restriction, setRestriction] = useState<ApiMySanction | null>(null);

  /** 하위 데이터 실패는 프로필 표시에 영향 없음 — 지표·목록만 비움 */
  const load = useCallback(() => {
    let canceled = false;

    getMyProfile()
      .then(async (loaded) => {
        if (canceled) return;
        setProfile(loaded);
        setFailed(false);

        const [loadedItems, blocks, reports, sanctions] = await Promise.all([
          getMyItems()
            .then((page) => ({ state: "ok" as const, rows: page.items, hasNext: page.hasNext }))
            .catch(() => ({ state: "error" as const })),
          getMyBlocks(0, 1).then((page) => page.totalElements).catch(() => 0),
          getMyReports(0, 1).then((page) => page.totalElements).catch(() => 0),
          // 건수와 함께 적용 중인 조치를 찾아야 해서 첫 페이지를 받는다
          getMySanctions().catch(() => null),
        ]);
        if (canceled) return;
        setItemList(loadedItems);
        setDetails({ blocks, reports, sanctions: sanctions?.totalElements ?? 0 });
        setRestriction(sanctions?.items.find((row) => row.isEffective) ?? null);
      })
      .catch(() => {
        if (!canceled) setFailed(true);
      });

    return () => {
      canceled = true;
    };
  }, []);

  useEffect(() => load(), [load]);

  /** 다음 페이지 — 20개 초과 물건의 프로필 내 관리용 */
  const loadMore = async () => {
    if (itemList.state !== "ok" || loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await getMyItems(Math.ceil(itemList.rows.length / PAGE_SIZE));
      setItemList((prev) =>
        prev.state === "ok"
          ? { state: "ok", rows: [...prev.rows, ...next.items], hasNext: next.hasNext }
          : prev,
      );
    } catch (e: unknown) {
      toast(e instanceof ApiError ? e.message : "더 불러오지 못했어요");
    } finally {
      setLoadingMore(false);
    }
  };

  /** A_006. reason 모달이 textarea라 줄바꿈·연속 공백은 한 칸으로 정리 */
  const editName = (current: ApiMyProfile) =>
    reason({
      title: "표시 이름을 바꿀까요?",
      body: "2~30자로 입력해 주세요. 이웃에게 보이는 이름이에요.",
      placeholder: current.member.displayName,
      confirm: "저장",
      required: true,
      onConfirm: async (input) => {
        const next = input.replace(/\s+/g, " ").trim();
        if (next === current.member.displayName) return;
        try {
          setProfile(await updateDisplayName(next, current.version));
          toast("이름을 바꿨어요");
        } catch (e: unknown) {
          // 충돌 응답은 선검사·@Version 두 경로로 갈리고 후자는 currentVersion 미포함 — code로 판정
          if (e instanceof ApiError && e.code === "VERSION_CONFLICT") {
            toast("다른 곳에서 먼저 수정되었어요. 새로 불러옵니다");
            load();
            return;
          }
          toast(e instanceof ApiError ? e.message : "이름을 바꾸지 못했어요");
        }
      },
    });

  /* 논리 삭제 — 진행 중 요청이 있으면 막음 (정책 및 상태.md DELETED) */
  const removeItem = (item: ApiItemSummary) => {
    const requests = item.pendingRequestCount ?? 0;
    if (requests > 0) {
      toast(`진행 중인 요청 ${requests}건을 먼저 정리해 주세요`);
      return;
    }
    confirm({
      title: `${item.title}${objectParticle(item.title)} 삭제할까요?`,
      body: "삭제하면 목록에서 사라져요. 지난 거래 기록은 그대로 남아요.",
      confirm: "삭제",
      danger: true,
      onConfirm: async () => {
        try {
          await deleteItem(item.id, item.version);
          setItemList((prev) =>
            prev.state === "ok" ? { ...prev, rows: prev.rows.filter((it) => it.id !== item.id) } : prev,
          );
          toast("삭제했어요");
        } catch (e: unknown) {
          toast(e instanceof ApiError ? e.message : "삭제하지 못했어요");
        }
      },
    });
  };

  /** 공개 전환 — 응답의 새 version 반영으로 연속 조작 시 409 방지 */
  const applyVisibility = async (item: ApiItemSummary, next: "PUBLIC" | "HIDDEN", done: string) => {
    try {
      const updated = await changeItemVisibility(item.id, next, item.version);
      setItemList((prev) =>
        prev.state === "ok"
          ? {
              ...prev,
              rows: prev.rows.map((it) =>
                it.id === item.id ? { ...it, visibility: next, version: updated.version } : it,
              ),
            }
          : prev,
      );
      toast(done);
    } catch (e: unknown) {
      toast(e instanceof ApiError ? e.message : "상태를 바꾸지 못했어요");
    }
  };

  /* 공개 중지는 검색·신규 요청을 막으므로 확인 후 전환 (정책 및 상태.md) */
  const toggleVisibility = (item: ApiItemSummary) => {
    if (item.visibility === "PUBLIC") {
      confirm({
        title: `${item.title}${objectParticle(item.title)} 공개 중지할까요?`,
        body: "검색과 새 요청이 멈춰요. 진행 중인 대여는 그대로 유지돼요.",
        confirm: "공개 중지",
        onConfirm: () => void applyVisibility(item, "HIDDEN", "공개를 중지했어요"),
      });
      return;
    }
    void applyVisibility(item, "PUBLIC", "다시 공개했어요");
  };

  return (
    <div className={c("profile-page page-window")}>
      <PageHeader title="내 프로필" />

      {failed ? (
        <ErrorState title="프로필을 불러오지 못했어요" onRetry={load} />
      ) : profile === null ? (
        <ProfileHeadingSkeleton />
      ) : (
        <ProfileHeading
          name={profile.member.displayName}
          action={
            <Button variant="secondary" onClick={() => editName(profile)}>
              이름 수정
            </Button>
          }
          summary={describeMembership({
            communityName: profile.activeMembership?.communityName,
            joinedAt: profile.member.joinedAt,
            verified: profile.activeMembership !== undefined,
            restricted: profile.isRestricted,
            unverifiedText: "동네 인증 필요",
          })}
        />
      )}

      {restriction ? (
        <Alert tone="danger" title={`${RESTRICTION_LABEL[restriction.action]} 조치가 적용되어 있어요`}>
          {restriction.publicReason}
          {restriction.endsAt ? ` · ${untilLabel(restriction.endsAt)}까지` : ""}
        </Alert>
      ) : null}

      <section className={c("profile-section")}>
        <p className={c("t-label profile-muted profile-section-label")}>신뢰 지표</p>
        <div className={c("profile-card")}>
          {profile ? <LiveTrustLines memberId={profile.member.id} joinedAt={profile.member.joinedAt} /> : failed ? (
            <ErrorState title="프로필을 먼저 불러와 주세요" onRetry={load} />
          ) : <Loading label="거래 기록 불러오는 중"><Skeleton w="70%" /></Loading>}
        </div>
        <p className={c("t-caption profile-muted profile-note")}>
          점수나 별점 대신, 실제 거래 기록을 그대로 보여줘요.
        </p>
      </section>

      <section className={c("profile-section")}>
        <p className={c("t-label profile-muted profile-section-label")}>등록한 물건</p>
        <div className={c("profile-card profile-card--list")}>
          {details === null ? (
            <Loading label="등록한 물건을 불러오는 중">
              <SkeletonItem layout="row" />
              <SkeletonItem layout="row" />
            </Loading>
          ) : itemList.state === "error" ? (
            <ErrorState title="물건 목록을 불러오지 못했어요" onRetry={load} />
          ) : itemList.state !== "ok" || itemList.rows.length === 0 ? (
            <p className={c("t-caption profile-muted profile-empty")}>등록한 물건이 없어요.</p>
          ) : (
            <>
              {itemList.rows.map((it) => (
                <ProfileItemRow
                  key={it.id}
                  item={it}
                  subtitle={`${it.place.name ?? "거래 장소 미정"}${
                    (it.pendingRequestCount ?? 0) > 0 ? ` · 요청 ${it.pendingRequestCount}건` : ""
                  }`}
                  onOpen={() => navigate(`/items/${it.id}?state=own`)}
                  actions={
                    <>
                      <button
                        type="button"
                        className={c("profile-row-delete")}
                        aria-label={`${it.title} 삭제`}
                        onClick={() => removeItem(it)}
                      >
                        <Icon name="x" size={16} />
                      </button>
                      <button
                        type="button"
                        className={c(`profile-pill${it.visibility === "HIDDEN" ? " profile-pill--off" : ""}`)}
                        onClick={() => toggleVisibility(it)}
                      >
                        {it.visibility === "PUBLIC" ? "공개 중" : "공개 중지"}
                      </button>
                    </>
                  }
                />
              ))}
              {itemList.hasNext ? (
                <button type="button" className={c("profile-more t-label")} onClick={loadMore} disabled={loadingMore}>
                  {loadingMore ? "불러오는 중" : "더보기"}
                </button>
              ) : null}
            </>
          )}
        </div>
      </section>

      <section className={c("profile-section")}>
        <p className={c("t-label profile-muted profile-section-label")}>관리</p>
        <div className={c("profile-card profile-card--list")}>
          <ProfileLinkRow
            icon="shield"
            title="차단한 이웃"
            trailing={details === null ? undefined : `${details.blocks}명`}
            onClick={() => navigate("/blocked")}
          />
          <ProfileLinkRow
            icon="flag"
            title="신고 내역"
            trailing={details === null ? undefined : `${details.reports}건`}
            onClick={() => navigate("/reports")}
          />
          {/* 받은 조치가 없는 사람에게는 보이지 않는다 */}
          {details !== null && details.sanctions > 0 ? (
            <ProfileLinkRow
              icon="alert"
              title="받은 운영 조치"
              trailing={`${details.sanctions}건`}
              onClick={() => navigate("/sanctions")}
            />
          ) : null}
        </div>
      </section>

      <Button
        variant="secondary"
        block
        onClick={() =>
          confirm({
            title: "로그아웃할까요?",
            body: "다시 로그인하면 이어서 이용할 수 있어요.",
            confirm: "로그아웃",
            onConfirm: () => {
              logout();
              toast("로그아웃되었어요");
              navigate("/login");
            },
          })
        }
      >
        로그아웃
      </Button>
    </div>
  );
}
