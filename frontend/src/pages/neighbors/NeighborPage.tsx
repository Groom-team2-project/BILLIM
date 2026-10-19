import { type ReactNode, useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ApiError } from "@/api/client";
import {
  type ApiPublicProfile,
  type ApiTrustMetrics,
  getMemberTrust,
  getMyProfile,
  getNeighborItems,
  getPublicProfile,
} from "@/api/members";
import { useSession } from "@/stores/session";
import { blockMember } from "@/api/blocks";
import { createReport } from "@/api/reports";
import type { ApiItemSummary } from "@/api/items";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { useUi } from "@/components/overlay/UiContext";
import { TrustLines } from "@/components/custom/profile/OwnerCard";
import { EmptyState, ErrorState } from "@/components/ui/state";
import { PageHeader } from "@/components/layout/header";
import { ProfileHeading, ProfileHeadingSkeleton } from "@/components/custom/profile/ProfileHeading";
import { ProfileItemRow } from "@/components/custom/profile/ProfileItemRow";
import { describeMembership, toTrust } from "@/utils/profileView";
import { toReasonCode, toReasonDetail } from "@/utils/reportReason";
import shared from "@/components/custom/profile/profileBlocks.module.css";
import styles from "@/pages/neighbors/NeighborPage.module.css";
import { classes } from "@/utils/classes";

const c = classes({ ...shared, ...styles });

type Loaded = {
  profile: ApiPublicProfile;
  trust: ApiTrustMetrics | null;
  items: ApiItemSummary[];
};

/** 라우트가 me·문자열도 받으므로 회원 ID 형식만 통과 — "me"는 GET /members/me로 해석돼 응답 형태가 다름 */
const MEMBER_ID = /^[1-9][0-9]*$/;

/** 열람 불가(404)와 일시 장애 구분 — 404는 존재 은닉 결과 */
type State = { kind: "loading" } | { kind: "ok"; data: Loaded } | { kind: "hidden" } | { kind: "error" };


export function NeighborPage() {
  const { memberId = "" } = useParams();
  const navigate = useNavigate();
  const { confirm, report, toast } = useUi();
  const { memberId: myId } = useSession();
  const [state, setState] = useState<State>({ kind: "loading" });

  const load = useCallback(() => {
    let canceled = false;

    if (!MEMBER_ID.test(memberId)) {
      // "me"는 내 프로필로, 그 밖의 형식은 렌더에서 열람 불가로 처리
      if (memberId === "me") navigate("/profile", { replace: true });
      return () => {};
    }

    // 열람 권한 판정은 프로필 응답이 대표 — 지표·물건 실패는 무시
    Promise.all([
      getPublicProfile(memberId),
      getMemberTrust(memberId).catch(() => null),
      getNeighborItems(memberId).then((page) => page.items).catch(() => []),
      // 세션 캐시에 없을 때만 조회. 실패 시 이웃으로 간주 — 자기 신고·차단은 서버가 거부
      myId ?? getMyProfile().then((me) => me.member.id).catch(() => null),
    ])
      .then(([profile, trust, items, resolvedMyId]) => {
        if (canceled) return;
        if (resolvedMyId === memberId) {
          navigate("/profile", { replace: true });
          return;
        }
        setState({ kind: "ok", data: { profile, trust, items } });
      })
      .catch((e: unknown) => {
        if (canceled) return;
        setState({ kind: e instanceof ApiError && e.status === 404 ? "hidden" : "error" });
      });

    return () => {
      canceled = true;
    };
  }, [memberId, myId, navigate]);

  useEffect(() => load(), [load]);

  const name = state.kind === "ok" ? state.data.profile.member.displayName : "";

  const onReport = () =>
    report({
      name,
      onConfirm: async (reason) => {
        try {
          await createReport({
            targetType: "MEMBER",
            targetId: memberId,
            reasonCode: toReasonCode(reason),
            detail: toReasonDetail(reason),
          });
          toast("신고를 접수했어요");
        } catch (e: unknown) {
          toast(e instanceof ApiError ? e.message : "신고를 접수하지 못했어요");
        }
      },
    });

  const onBlock = () =>
    confirm({
      title: `${name} 님을 차단할까요?`,
      body: "차단하면 서로 대화할 수 없고 물건도 볼 수 없어요.",
      confirm: "차단",
      danger: true,
      onConfirm: async () => {
        try {
          await blockMember(memberId);
          toast("차단했어요");
          navigate("/", { replace: true });
        } catch (e: unknown) {
          toast(e instanceof ApiError ? e.message : "차단하지 못했어요");
        }
      },
    });

  /** 분기별 헤더 반복 방지용 껍데기 */
  const shell = (body: ReactNode) => (
    <div className={c("profile-page page-window")}>
      <PageHeader title="이웃 프로필" />
      {body}
    </div>
  );

  if (!MEMBER_ID.test(memberId)) {
    return shell(
      <EmptyState icon="info" title="볼 수 없는 프로필이에요">
        <p className={c("t-caption profile-muted")}>주소를 다시 확인해 주세요.</p>
      </EmptyState>,
    );
  }

  if (state.kind === "loading") {
    return shell(<ProfileHeadingSkeleton />);
  }

  if (state.kind === "hidden") {
    return shell(
      <EmptyState icon="info" title="볼 수 없는 프로필이에요">
        <p className={c("t-caption profile-muted")}>같은 동네 이웃이거나 거래한 적이 있어야 볼 수 있어요.</p>
      </EmptyState>,
    );
  }

  if (state.kind === "error") {
    return shell(<ErrorState onRetry={load} />);
  }

  const { profile, trust, items } = state.data;

  return shell(
    <>
      <ProfileHeading
        name={profile.member.displayName}
        summary={describeMembership({
          communityName: profile.communityName,
          joinedAt: profile.member.joinedAt,
          verified: profile.isVerified,
        })}
      />

      <section className={c("profile-section")}>
        <p className={c("t-label profile-muted profile-section-label")}>신뢰 지표</p>
        <div className={c("profile-card")}>
          <TrustLines trust={toTrust(trust, profile.member.joinedAt)} />
        </div>
        <p className={c("t-caption profile-muted profile-note")}>
          {trust === null
            ? "거래 기록을 불러오지 못했어요."
            : "초대 코드는 소속을 인증하지 않아요. 거래 기록으로만 판단해요."}
        </p>
      </section>

      <section className={c("profile-section")}>
        <p className={c("t-label profile-muted profile-section-label")}>등록한 물건</p>
        <div className={c("profile-card profile-card--list")}>
          {items.length === 0 ? (
            <p className={c("t-caption profile-muted profile-empty")}>공개된 물건이 없어요.</p>
          ) : (
            items.map((it) => (
              <ProfileItemRow
                key={it.id}
                item={it}
                subtitle={it.place.name ?? "거래 장소 미정"}
                onOpen={() => navigate(`/items/${it.id}`)}
              />
            ))
          )}
        </div>
      </section>

      <div className={c("neighbor-actions")}>
        <button type="button" className={c("neighbor-report t-label")} onClick={onReport}>
          <Icon name="flag" size={18} />
          신고
        </button>
        <Button
          variant="secondary"
          icon="shield"
          disabled={profile.blockedByMe}
          onClick={profile.blockedByMe ? undefined : onBlock}
        >
          {profile.blockedByMe ? "차단한 이웃" : "차단"}
        </Button>
      </div>
      <p className={c("t-caption profile-muted")}>
        신고·차단은 되돌리기 어려운 행동이에요. 차단하면 이 이웃과 더는 대화할 수 없어요.
      </p>
    </>,
  );
}
