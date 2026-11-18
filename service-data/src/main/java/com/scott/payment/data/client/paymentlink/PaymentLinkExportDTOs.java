package com.scott.payment.data.client.paymentlink;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 可靠导出分页边界，只承载冻结查询和授权安全行，不含分享 URL 或客户资料。
 */
public final class PaymentLinkExportDTOs {
    /**
     * 禁止实例化协议容器，字段均通过强类型嵌套对象传输。
     */
    private PaymentLinkExportDTOs() { }
    /**
     * 任务执行器冻结的来源分页请求，不接受浏览器任意商户范围。
     */
    @Data public static class PageRequest {
        @NotNull private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = Set.of();
        private String merchantId;
        private com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.PaymentLinkQuery query;
        @Min(1) private int pageNo = 1;
        @Min(1) @Max(100) private int pageSize = 100;
    }
    /**
     * 安全列白名单；不会随业务详情增加 URL 或客户信息。
     */
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
