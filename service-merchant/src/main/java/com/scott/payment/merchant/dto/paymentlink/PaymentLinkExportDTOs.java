package com.scott.payment.merchant.dto.paymentlink;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.Set;

/** 可靠导出分页边界，只承载冻结查询和授权安全行，不含分享 URL 或客户资料。 */
public final class PaymentLinkExportDTOs {
    private PaymentLinkExportDTOs() { }
    @Data public static class PageRequest {
        @NotNull private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = Set.of();
        private String merchantId;
        private PaymentLinkDTOs.Query query;
        @Min(1) private int pageNo = 1;
        @Min(1) @Max(100) private int pageSize = 100;
    }
    @Data public static class Row {
        private String linkId;
        private String merchantId;
        private String merchantOrderNo;
        private String linkName;
        private String currency;
        private String amount;
        private String status;
        private String paymentStatus;
        private LocalDateTime expireTime;
        private LocalDateTime createTime;
        private LocalDateTime paidTime;
        private String createdAppCode;
        private String createdBy;
    }
}
