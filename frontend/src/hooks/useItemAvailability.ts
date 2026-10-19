import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getAvailabilityRange } from "@/api/rentals";
import { ApiError } from "@/api/client";
import { parseIsoDate } from "@/utils/itemView";
import type { DayRange } from "@/components/ui/calendar";

type State = { key: string; booked: DayRange[]; blocked: boolean; error?: string };

export function useItemAvailability(itemId: string, from: string, to: string) {
  const navigate = useNavigate();
  const key = `${itemId}:${from}:${to}`;
  const [result, setResult] = useState<State | null>(null);
  const [retry, setRetry] = useState(0);
  const [pending, setPending] = useState(false);
  useEffect(() => {
    if (!itemId || !from || !to) return;
    let alive = true;
    getAvailabilityRange(itemId, from, to).then((response) => {
      if (!alive) return;
      setResult({ key, blocked: response.overdueBlocked, booked: response.occupiedRanges.map((r) => [parseIsoDate(r.startDate), parseIsoDate(r.endDate)]) });
      setPending(false);
    }).catch((e: unknown) => {
      if (!alive) return;
      if (e instanceof ApiError && e.unauthenticated) { navigate("/login"); return; }
      setResult({ key, booked: [], blocked: false, error: e instanceof ApiError ? e.message : "예약 정보를 불러오지 못했어요." });
      setPending(false);
    });
    return () => { alive = false; };
  }, [itemId, from, to, key, retry, navigate]);
  const current = result?.key === key ? result : null;
  return { ...current, ready: !!current && !current.error && !pending,
    loading: !current || pending, retry: () => { setPending(true); setRetry((n) => n + 1); } };
}
