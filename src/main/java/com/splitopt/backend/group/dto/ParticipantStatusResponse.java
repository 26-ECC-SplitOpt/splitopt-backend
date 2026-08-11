package com.splitopt.backend.group.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class ParticipantStatusResponse {
    private Long userId;
    private String name;
    private BigDecimal paidAmount;
    private BigDecimal burdenAmount;
    private BigDecimal balance;
    private List<SettlementLeg> toSend;
    private List<SettlementLeg> toReceive;
    private String settledStatus; // NOT_STARTED / IN_PROGRESS / DONE

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SettlementLeg {
        private Long settlementId;
        private Long toUserId;
        private String toName;
        private Long fromUserId;
        private String fromName;
        private BigDecimal amount;
        private String status;
    }
}
