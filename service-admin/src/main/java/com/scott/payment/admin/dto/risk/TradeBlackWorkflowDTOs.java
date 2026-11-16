package com.scott.payment.admin.dto.risk;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 系统交易加黑新版工作流的请求与响应。 */
public final class TradeBlackWorkflowDTOs {

    public static final int MAX_MANUAL_ORDERS = 500;
    public static final int MAX_FILE_ORDERS = 5000;
    public static final int MAX_SELECTED_CANDIDATES = 20000;

    private TradeBlackWorkflowDTOs() {
    }

    /** 候选预览请求。客户端不得提交脱敏值或哈希。 */
    @Data
    public static class PreviewRequest {
        /** SINGLE、PASTE、FILE。 */
        @NotBlank(message = "请选择录入方式")
        private String inputMode;

        /** GLOBAL 或 MERCHANT。 */
        @NotBlank(message = "请选择黑名单作用域")
        private String scopeType;

        /** MERCHANT 作用域下的目标商户号列表。 */
        private List<String> merchantIds;

        /** ADD 或 RELEASE。 */
        @NotBlank(message = "请选择操作")
        private String actionType;

        /** 选中的黑名单功能编码。 */
        @NotEmpty(message = "请至少选择一个黑名单功能")
        @Size(max = 18, message = "黑名单功能数量不正确")
        private List<String> functionCodes;

        /** 操作原因。 */
        @Size(max = 500, message = "操作原因不能超过500个字符")
        private String actionReason;

        /** 交易定位列表。 */
        @Valid
        @NotEmpty(message = "请输入交易订单号")
        @Size(max = MAX_FILE_ORDERS, message = "一次最多处理5000笔交易")
        private List<OrderReference> orders;
    }

    /** 单笔交易定位条件。 */
    @Data
    public static class OrderReference {
        private Integer rowNo;

        /** MERCHANT_ORDER_NO、SYSTEM_ORDER_NO。 */
        @NotBlank(message = "请选择订单号类型")
        private String identifierType;

        /** 商户订单号查询时必填，与目标黑名单作用域相互独立。 */
        private String lookupMerchantId;

        @NotBlank(message = "订单号不能为空")
        @Size(max = 128, message = "订单号不能超过128个字符")
        private String orderNo;
    }

    /** 执行请求；服务端会重新解析交易并重新生成候选项。 */
    @Data
    public static class ExecuteRequest {
        @NotBlank(message = "请求幂等号不能为空")
        @Size(max = 64, message = "请求幂等号不能超过64个字符")
        private String requestId;

        @Valid
        private PreviewRequest workflow;

        @NotEmpty(message = "请选择需要执行的候选项")
        @Size(max = MAX_SELECTED_CANDIDATES, message = "一次最多执行20000个候选项")
        private List<String> selectedCandidateKeys;
    }

    /** 预览响应。 */
    @Data
    public static class PreviewResponse {
        private int orderCount;
        private int resolvedOrderCount;
        private int candidateCount;
        private int selectableCount;
        private int unavailableCount;
        private List<Candidate> candidates = new ArrayList<>();
        private List<OrderError> orderErrors = new ArrayList<>();
    }

    /** 前端可见的脱敏候选项。 */
    @Data
    public static class Candidate {
        private String candidateKey;
        private Integer rowNo;
        private String inputOrderNo;
        private String transactionId;
        private String sourceMerchantId;
        private String merchantOrderNo;
        private String functionCode;
        private String functionName;
        private String sourceField;
        private String sourceLabel;
        private String maskedValue;
        private String targetScope;
        private String targetMerchantId;
        /** READY、ALREADY_EXISTS、ALREADY_MISSING、NOT_AVAILABLE。 */
        private String state;
        private String stateMessage;
        private boolean selectable;
        private boolean selected;
    }

    /** 无法定位或无法解析的订单行。 */
    @Data
    public static class OrderError {
        private Integer rowNo;
        private String orderNo;
        private String code;
        private String message;
    }

    /** 执行结果。 */
    @Data
    public static class ExecuteResponse {
        private String batchNo;
        private String status;
        private int totalCount;
        private int successCount;
        private int skippedCount;
        private int failureCount;
        private List<ExecuteItem> failures = new ArrayList<>();
    }

    /** 失败明细；前端通过受控批次接口下载国际化 Excel，不直接拼接文件。 */
    @Data
    public static class ExecuteItem {
        private String candidateKey;
        private Integer rowNo;
        private String orderNo;
        private String functionCode;
        private String functionName;
        private String sourceField;
        private String targetScope;
        private String targetMerchantId;
        private String maskedValue;
        private String resultCode;
        private String message;
    }
}
