package com.scott.payment.data.client.admin.dto;

import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SettlementReserveQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SettlementTransactionQuery;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Data 调用 Admin 结算导出来源的强类型内部模型。
 */
public final class AdminSettlementExportSourceDTOs {

    /**
     * 阻止内部模型容器被实例化。
     */
    private AdminSettlementExportSourceDTOs() {
    }

    /**
     * 一页结算交易导出来源请求。
     */
    @Data
    public static class SettlementTransactionPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private SettlementTransactionQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页保证金结算导出来源请求。
     */
    @Data
    public static class SettlementReservePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private SettlementReserveQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页结算交易安全投影及总行数。
     */
    @Data
    public static class SettlementTransactionPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<SettlementTransactionRow> records = new ArrayList<>();
    }

    /**
     * 一页保证金结算安全投影及总行数。
     */
    @Data
    public static class SettlementReservePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<SettlementReserveRow> records = new ArrayList<>();
    }

    /**
     * 结算交易导出安全行，不包含渠道或底层组件原文。
     */
    @Data
    public static class SettlementTransactionRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private LocalDate businessDate;
        private String batchStatus;
        private String merchantId;
        private String merchantOrderNo;
        private String sourceTransactionId;
        private LocalDateTime sourceTransactionDateTime;
        private String paymentType;
        private String paymentMethod;
        private String transactionType;
        private BigDecimal sourceAmount;
        private String sourceCurrency;
        private Long componentCount;
        private String netDirection;
        private BigDecimal netTargetAmount;
        private String targetCurrency;
        private LocalDateTime postedTime;
    }

    /**
     * 保证金结算导出安全行，不包含账户或内部来源信息。
     */
    @Data
    public static class SettlementReserveRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private LocalDate businessDate;
        private String merchantId;
        private String reserveNo;
        private String sourceTransactionId;
        private LocalDateTime sourceTransactionDateTime;
        private String actionType;
        private String direction;
        private String currency;
        private BigDecimal amount;
        private BigDecimal remainingAmount;
        private String reserveStatus;
        private LocalDate expectedReleaseDate;
        private LocalDateTime actionTime;
    }
}
