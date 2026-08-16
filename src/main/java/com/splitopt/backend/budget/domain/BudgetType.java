package com.splitopt.backend.budget.domain;

/**
 * 예산을 어떤 단위로 잡았는지 (API 38).
 *
 * <p>저장하는 금액은 한 개지만 그 금액이 무엇을 뜻하는지가 달라진다. 사용률·잔여·초과 판정은
 * 언제나 <b>모임 전체 기준 총예산</b>으로 하며, {@code PER_PERSON}이면 총예산을 인원수로 곱해
 * 구한다.
 */
public enum BudgetType {

    /** 저장된 금액이 곧 모임 전체 예산. */
    TOTAL,

    /** 저장된 금액은 1인당 예산. 총예산 = 금액 × 활성 참여자 수. */
    PER_PERSON
}
