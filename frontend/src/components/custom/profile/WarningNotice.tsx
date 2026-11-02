import { useEffect } from "react";
import { getMySanctions, type ApiMySanction } from "@/api/reports";
import { useUi } from "@/components/overlay/UiContext";
import { confirmWarning, nextWarning, warningSummary } from "@/utils/warning-notice";

function browserStorage(): Storage | null {
  try {
    return localStorage;
  } catch {
    return null;
  }
}

/** 로그인 화면에서 새 경고를 안내한다. 확인 기록은 이 브라우저에만 남긴다. */
export function WarningNotice({ memberId }: { memberId: string }) {
  const { confirm } = useUi();

  useEffect(() => {
    let alive = true;
    let loading = false;
    let dialogOpen = false;
    const shown = new Set<string>();

    const showNext = (rows: ApiMySanction[]) => {
      if (!alive) return;
      const warning = nextWarning(rows, memberId, shown, browserStorage());
      if (!warning) {
        dialogOpen = false;
        return;
      }
      shown.add(warning.id);
      dialogOpen = true;
      confirm({
        title: "운영팀에서 경고 조치를 했어요",
        body: `사유: ${warningSummary(warning.publicReason)} 자세한 내용은 프로필의 받은 운영 조치에서 확인할 수 있어요.`,
        cancel: "나중에",
        confirm: "확인했어요",
        onConfirm: () => {
          confirmWarning(memberId, warning.id, browserStorage());
          showNext(rows);
        },
      });
    };

    const check = async () => {
      if (!alive || loading || dialogOpen) return;
      loading = true;
      try {
        const page = await getMySanctions(0, 50);
        if (alive) showNext(page.items);
      } catch {
        // 일시적인 조회 실패는 다음 화면 방문 또는 탭 복귀 시 재시도한다.
      } finally {
        loading = false;
      }
    };

    void check();
    const onFocus = () => { if (!document.hidden) void check(); };
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onFocus);
    return () => {
      alive = false;
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onFocus);
    };
  }, [memberId, confirm]);

  return null;
}
