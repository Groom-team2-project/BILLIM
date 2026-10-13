import { useEffect, useRef, useState } from "react";
import { MapView } from "@/components/custom/items/MapView";
import styles from "@/components/custom/items/KakaoMapView.module.css";
import { classes } from "@/utils/classes";
import type { MapPin, PlaceMarker } from "@/utils/itemView";
import { KAKAO_MAP_KEY, type KakaoMap, type KakaoMaps, type KakaoOverlay, loadKakaoMaps } from "@/utils/kakaoMap";

const c = classes(styles);

/** 표시할 장소가 없을 때의 중심(서울시청)과 확대 수준 */
const DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 };
const DEFAULT_LEVEL = 5;
const SINGLE_LEVEL = 3;

type KakaoMapViewProps = {
  /** 공용 장소별 마커 (좌표 기준) */
  markers: readonly PlaceMarker[];
  /** 지도를 쓸 수 없을 때 도식 지도에 올릴 핀 */
  fallbackPins: readonly MapPin[];
  selected?: string;
  onSelect?: (id: string) => void;
  height?: number;
};

type MapState = "loading" | "ready" | "failed";

/**
 * 물건 찾기 지도 (P12). 공용 장소 좌표에 장소별 물건 수 마커를 올린다.
 * 키가 없거나 지도를 불러오지 못하면 도식 지도로 대체하고, 목록은 그대로 쓸 수 있다 (P13).
 */
export function KakaoMapView({ markers, fallbackPins, selected, onSelect, height = 360 }: KakaoMapViewProps) {
  const [state, setState] = useState<MapState>(KAKAO_MAP_KEY ? "loading" : "failed");
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<{ maps: KakaoMaps; map: KakaoMap } | null>(null);
  const overlaysRef = useRef<KakaoOverlay[]>([]);
  const buttonsRef = useRef(new Map<string, HTMLButtonElement>());
  const onSelectRef = useRef(onSelect);
  const markersRef = useRef(markers);

  useEffect(() => {
    onSelectRef.current = onSelect;
    markersRef.current = markers;
  });

  // 마커 내용이 실제로 바뀔 때만 다시 그린다 (지도를 움직인 뒤 다른 이유로 다시 렌더돼도 범위를 되돌리지 않음)
  const markersKey = markers.map((m) => `${m.id}:${m.lat}:${m.lng}:${m.n}:${m.label}`).join("|");

  // SDK를 불러와 지도를 한 번 만든다
  useEffect(() => {
    if (!KAKAO_MAP_KEY) return;
    let alive = true;
    loadKakaoMaps()
      .then((maps) => {
        const el = containerRef.current;
        if (!alive || !el) return;
        const map = new maps.Map(el, {
          center: new maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
          level: DEFAULT_LEVEL,
        });
        mapRef.current = { maps, map };
        setState("ready");
      })
      .catch(() => alive && setState("failed"));
    return () => {
      alive = false;
    };
  }, []);

  // 마커가 바뀌면 다시 그리고, 모든 장소가 보이도록 범위를 맞춘다
  useEffect(() => {
    const ctx = mapRef.current;
    if (state !== "ready" || !ctx) return;
    const { maps, map } = ctx;
    const list = markersRef.current;

    for (const o of overlaysRef.current) o.setMap(null);
    buttonsRef.current.clear();
    overlaysRef.current = list.map((m) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = c("kmap-pin t-caption");
      button.setAttribute("aria-label", `${m.label} 물건 ${m.n}개`);
      const count = document.createElement("span");
      count.className = c("kmap-pin-count t-micro");
      count.textContent = String(m.n);
      button.append(count, document.createTextNode(m.label));
      button.addEventListener("click", () => onSelectRef.current?.(m.id));
      buttonsRef.current.set(m.id, button);

      const overlay = new maps.CustomOverlay({
        position: new maps.LatLng(m.lat, m.lng),
        content: button,
        yAnchor: 1.2,
        clickable: true,
      });
      overlay.setMap(map);
      return overlay;
    });

    map.relayout();
    if (list.length === 1) {
      map.setCenter(new maps.LatLng(list[0].lat, list[0].lng));
      map.setLevel(SINGLE_LEVEL);
    } else if (list.length > 1) {
      const bounds = new maps.LatLngBounds();
      for (const m of list) bounds.extend(new maps.LatLng(m.lat, m.lng));
      map.setBounds(bounds, 48, 48, 48, 48);
    }
  }, [state, markersKey]);

  // 선택한 장소 표시만 바꾼다 (다시 그리지 않음)
  useEffect(() => {
    for (const [id, button] of buttonsRef.current) {
      button.setAttribute("aria-pressed", id === selected ? "true" : "false");
    }
  }, [state, markersKey, selected]);

  // 화면을 떠날 때 마커를 지운다
  useEffect(
    () => () => {
      for (const o of overlaysRef.current) o.setMap(null);
      overlaysRef.current = [];
      buttonsRef.current.clear();
    },
    [],
  );

  if (state === "failed") {
    return (
      <div>
        <MapView selected={selected} height={height} onSelect={onSelect} pins={fallbackPins} />
        {KAKAO_MAP_KEY ? (
          <p className={c("kmap-fallback-note t-caption")}>
            지도를 불러오지 못해 간단한 지도로 보여 드려요. 아래 목록은 그대로 쓸 수 있어요.
          </p>
        ) : null}
      </div>
    );
  }

  return (
    <div className={c("kmap")} style={{ height }}>
      <div ref={containerRef} className={c("kmap-canvas")} />
      {state === "loading" ? <span className={c("kmap-note t-micro")}>지도 불러오는 중</span> : null}
      {state === "ready" && markers.length === 0 ? (
        <span className={c("kmap-note t-micro")}>지도에 표시할 거래 장소가 없어요</span>
      ) : null}
    </div>
  );
}
