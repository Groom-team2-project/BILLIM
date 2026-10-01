import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useUi } from "@/components/overlay/UiContext";
import { Button } from "@/components/ui/button";
import { Calendar, type DayRange } from "@/components/ui/calendar";
import { Chip } from "@/components/ui/chip";
import { Icon } from "@/components/ui/icon";
import { TextField } from "@/components/ui/text-field";
import { CATEGORIES } from "@/data/items";
import styles from "@/pages/register/RegisterPage.module.css";
import { classes } from "@/utils/classes";
import { MOCK_TODAY } from "@/constants/mock";
import { PageHeader } from "@/components/layout/header";

const c = classes(styles);

/** 공용 거래 장소 — 커뮤니티 기준 정보 목데이터 */
const PLACES = [
  "관리동 1층 무인택배함 앞",
  "정문 경비실 옆 벤치",
  "B동 커뮤니티센터 입구",
  "놀이터 옆 정자",
  "C동 자전거 보관소",
];


export function RegisterPage() {
  const { toast } = useUi();
  const navigate = useNavigate();
  const [avail, setAvail] = useState<DayRange>([new Date(2026, 8, 25), new Date(2026, 8, 30)]);
  return (
    <div className={c("register page-window")}>
      <PageHeader title="물건 등록" backTo="/" />

      <div className={c("register-field")}>
        <span className={c("t-label")}>
          사진 <span className={c("register-muted t-caption")}>최대 5장</span>
        </span>
        <div className={c("register-photos")}>
          <button type="button" className={c("register-photo-tile t-caption")}>
            <Icon name="camera" size={24} />
            0 / 5
          </button>
          <span className={c("register-photo-tile register-photo-tile--ghost")}>
            <Icon name="camera" size={20} />
          </span>
          <span className={c("register-photo-tile register-photo-tile--ghost")}>
            <Icon name="camera" size={20} />
          </span>
        </div>
      </div>

      <TextField label="제목" defaultValue="아이스박스 25L" hint="물건 이름과 규격을 적어 주세요" />

      <div className={c("register-field")}>
        <span className={c("t-label")}>카테고리</span>
        <div className={c("register-chips")}>
          {CATEGORIES.map((cat) => (
            <Chip key={cat.id} icon={cat.icon} selected={cat.id === "camp"}>
              {cat.label}
            </Chip>
          ))}
        </div>
      </div>

      <TextField label="설명" optional multiline placeholder="함께 빌려주는 구성품, 사용할 때 주의할 점" />

      <div className={c("register-field")}>
        <span className={c("t-label")}>거래 장소</span>
        <span className={c("t-caption register-muted")}>
          집 주소 대신 커뮤니티 공용 장소에서 만나요. 주소는 서로 공유되지 않아요.
        </span>
        <div className={c("register-places")} role="radiogroup" aria-label="거래 장소">
          {PLACES.map((place, i) => (
            <label key={place} className={c("register-place t-body")}>
              <input type="radio" name="place" defaultChecked={i === 1} />
              {place}
            </label>
          ))}
        </div>
      </div>

      <div className={c("register-field")}>
        <span className={c("t-label")}>대여 가능 기간</span>
        <div className={c("register-cal")}>
          <Calendar today={MOCK_TODAY} value={avail} onChange={setAvail} />
        </div>
        <span className={c("t-caption register-muted")}>
          빌려줄 수 있는 기간을 정해요. 이웃은 이 안에서만 날짜를 고를 수 있어요.
        </span>
      </div>

      <Button
        size="lg"
        block
        onClick={() => {
          toast("물건을 등록했어요");
          navigate("/");
        }}
      >
        등록하기
      </Button>
    </div>
  );
}
