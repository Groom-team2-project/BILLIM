/** C_008 회원 거래 신뢰 지표 응답 */
export type ApiTrustMetrics = {
  completedCount: number;
  lentCount: number;
  returnPromiseKept: number;
  returnPromiseTotal: number;
  basis: string;
};

