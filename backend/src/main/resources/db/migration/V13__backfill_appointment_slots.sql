-- 기존 거래의 누락된 전달·반납 슬롯 보충
INSERT INTO appointment_slots (rental_id, kind, version, created_at, updated_at)
SELECT r.id, kinds.kind, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM rentals r
CROSS JOIN (SELECT 'PICKUP' AS kind UNION ALL SELECT 'RETURN') kinds
WHERE r.status IN ('APPROVED', 'ACTIVE', 'RETURNED')
  AND NOT EXISTS (
      SELECT 1 FROM appointment_slots s WHERE s.rental_id = r.id AND s.kind = kinds.kind
  );
