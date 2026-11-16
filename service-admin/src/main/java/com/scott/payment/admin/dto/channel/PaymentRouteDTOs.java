package com.scott.payment.admin.dto.channel;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端支付路由配置接口模型。
 *
 * <p>这些模型只描述配置中心，不构成支付链路的运行时路由契约。</p>
 */
public final class PaymentRouteDTOs {

    private PaymentRouteDTOs() {
    }

    /** 支付路由规则分页查询条件。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RouteRuleQuery extends PageRequest {
        private String keyword;
        private String ruleType;
        private String scopeType;
        private String merchantId;
        @Size(max = 100, message = "paymentMethods size must not exceed 100")
        private List<String> paymentMethods = new ArrayList<>();
        @Valid
        @Size(max = 100, message = "paymentScopes size must not exceed 100")
        private List<RoutePaymentScope> paymentScopes = new ArrayList<>();
        @Size(max = 100, message = "transactionTypes size must not exceed 100")
        private List<String> transactionTypes = new ArrayList<>();
        /** 兼容阶段 1A 首版管理端查询；新调用方使用 paymentMethods。 */
        private String paymentMethod;
        /** 兼容阶段 1A 首版管理端查询；新调用方使用 transactionTypes。 */
        private String transactionType;
        private String ruleStatus;
        private String unavailablePolicy;
    }

    /** 支付路由规则保存请求。 */
    @Data
    public static class RouteRuleSaveRequest {
        @NotBlank(message = "ruleName is required")
        @Size(max = 128, message = "ruleName must not exceed 128 characters")
        private String ruleName;

        @NotBlank(message = "ruleType is required")
        private String ruleType;

        @NotBlank(message = "scopeType is required")
        private String scopeType;

        @Size(max = 32, message = "merchantId must not exceed 32 characters")
        private String merchantId;

        /** 指定商户范围的完整商户集合；merchantId 仅用于兼容迁移期旧调用方。 */
        @Size(max = 100, message = "merchantIds size must not exceed 100")
        private List<@NotBlank(message = "merchantId must not be blank")
                @Size(max = 32, message = "merchantId must not exceed 32 characters") String> merchantIds = new ArrayList<>();

        /** 阶段 1A 仅允许 ACQUIRING，但仍持久化业务域以避免后续语义混用。 */
        @NotBlank(message = "businessType is required")
        private String businessType = "ACQUIRING";

        /** 兼容阶段 1A 旧管理端；新调用方使用 paymentScopes。 */
        @Size(max = 100, message = "paymentMethods size must not exceed 100")
        private List<@Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "payment method must be an uppercase payment method code") String> paymentMethods = new ArrayList<>();
        /** 规则适用的统一支付维度；银行卡品牌作为 BANK_CARD 下的显式品牌保存。 */
        @Valid
        @Size(max = 100, message = "paymentScopes size must not exceed 100")
        private List<RoutePaymentScope> paymentScopes = new ArrayList<>();
        /** 空集合表示适用于全部收单交易类型。 */
        @Size(max = 100, message = "transactionTypes size must not exceed 100")
        private List<@Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "transaction type must be an uppercase transaction type code") String> transactionTypes = new ArrayList<>();

        @NotNull(message = "rulePriority is required")
        @Min(value = 1, message = "rulePriority must be at least 1")
        @Max(value = 10000, message = "rulePriority must not exceed 10000")
        private Integer rulePriority;

        @NotBlank(message = "unavailablePolicy is required")
        private String unavailablePolicy;

        @Valid
        @NotNull(message = "strategy is required")
        private RouteStrategyRequest strategy = new RouteStrategyRequest();

        @Valid
        @Size(max = 1000, message = "candidates size must not exceed 1000")
        private List<RouteCandidateRequest> candidates = new ArrayList<>();

        /** 更新时必须传入当前版本；新增时为空。 */
        @Min(value = 0, message = "version must not be negative")
        private Integer version;

        @Size(max = 500, message = "remark must not exceed 500 characters")
        private String remark;

        @NotBlank(message = "operationReason is required")
        @Size(max = 500, message = "operationReason must not exceed 500 characters")
        private String operationReason;
    }

    /** 路由类型专属配置；类型必须与规则类型一致。 */
    @Data
    public static class RouteStrategyRequest {
        @NotBlank(message = "strategyType is required")
        private String strategyType = "PRIORITY";

        /** 历史成功 MID 路由的回看月数，其他类型必须为空。 */
        @Min(value = 1, message = "historyLookbackMonths must be at least 1")
        @Max(value = 24, message = "historyLookbackMonths must not exceed 24")
        private Integer historyLookbackMonths;

        @NotNull(message = "strategyStatus is required")
        private Integer strategyStatus = 1;
    }

    /** 路由支付维度；非银行卡支付方式的 cardBrand 固定为 NONE。 */
    @Data
    public static class RoutePaymentScope {
        @NotBlank(message = "paymentMethod is required")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "paymentMethod must be an uppercase payment method code")
        private String paymentMethod;

        @NotBlank(message = "cardBrand is required")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "cardBrand must be an uppercase card brand code")
        private String cardBrand = "NONE";
    }

    /** 路由候选 MID 保存请求。 */
    @Data
    public static class RouteCandidateRequest {
        /** 候选所属支付方式；同一 MID 可在不同支付方式分组中独立配置。 */
        @NotBlank(message = "paymentMethod is required")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "paymentMethod must be an uppercase payment method code")
        private String paymentMethod;

        /** 银行卡品牌；非银行卡支付方式固定为 NONE。 */
        @NotBlank(message = "cardBrand is required")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "cardBrand must be an uppercase card brand code")
        private String cardBrand = "NONE";

        @NotNull(message = "channelId is required")
        private Long channelId;

        @NotNull(message = "midConfigId is required")
        private Long midConfigId;

        @NotNull(message = "candidatePriority is required")
        @Min(value = 1, message = "candidatePriority must be at least 1")
        @Max(value = 10000, message = "candidatePriority must not exceed 10000")
        private Integer candidatePriority;

        /** 权重路由中的分配比例；按支付方式分组后，启用候选合计必须为100.00。 */
        @DecimalMin(value = "0", inclusive = true, message = "candidateWeight must not be negative")
        private BigDecimal candidateWeight;

        @NotNull(message = "candidateStatus is required")
        private Integer candidateStatus = 1;
    }

    /** 发布、停用或删除请求，携带乐观锁版本和审计原因。 */
    @Data
    public static class RouteActionRequest {
        @NotNull(message = "version is required")
        @Min(value = 0, message = "version must not be negative")
        private Integer version;

        @NotBlank(message = "operationReason is required")
        @Size(max = 500, message = "operationReason must not exceed 500 characters")
        private String operationReason;
    }

    /** 复制路由规则请求；复制结果固定为草稿。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RouteCopyRequest extends RouteActionRequest {
        @NotBlank(message = "ruleName is required")
        @Size(max = 128, message = "ruleName must not exceed 128 characters")
        private String ruleName;
    }

    /** 管理端支付路由规则详情。 */
    @Data
    public static class RouteRuleResponse {
        private Long id;
        private String ruleCode;
        private String ruleName;
        private String ruleType;
        private String scopeType;
        private String merchantId;
        private List<String> merchantIds = new ArrayList<>();
        private String businessType;
        private List<String> paymentMethods = new ArrayList<>();
        private List<RoutePaymentScope> paymentScopes = new ArrayList<>();
        private List<String> transactionTypes = new ArrayList<>();
        private Integer rulePriority;
        private String unavailablePolicy;
        private String ruleStatus;
        private Integer version;
        private String remark;
        private RouteStrategyResponse strategy;
        private List<RouteCandidateResponse> candidates = new ArrayList<>();
        private String publishedBy;
        private LocalDateTime publishedTime;
        private String disabledBy;
        private LocalDateTime disabledTime;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
    }

    /** 批量检查指定商户已有路由配置。 */
    @Data
    public static class MerchantRouteConflictRequest {
        @NotEmpty(message = "merchantIds is required")
        @Size(max = 100, message = "merchantIds size must not exceed 100")
        private List<@NotBlank(message = "merchantId must not be blank")
                @Size(max = 32, message = "merchantId must not exceed 32 characters") String> merchantIds = new ArrayList<>();

        /** 编辑规则时排除当前规则自身。 */
        private Long excludeRuleId;
    }

    /** 一个商户及其已有的指定商户路由规则。 */
    @Data
    public static class MerchantRouteConflictResponse {
        private String merchantId;
        private List<MerchantRouteRuleBrief> rules = new ArrayList<>();
    }

    /** 冲突提示需要展示的最小路由信息。 */
    @Data
    public static class MerchantRouteRuleBrief {
        private Long id;
        private String ruleCode;
        private String ruleName;
        private String ruleType;
        private Integer rulePriority;
        private String ruleStatus;
    }

    /** 管理端路由策略详情。 */
    @Data
    public static class RouteStrategyResponse {
        private Long id;
        private String strategyType;
        private Integer historyLookbackMonths;
        private Integer strategyStatus;
    }

    /** 管理端候选 MID 详情，渠道名称仅用于平台运营展示。 */
    @Data
    public static class RouteCandidateResponse {
        private Long id;
        private String paymentMethod;
        private String cardBrand;
        private Long channelId;
        private String channelCode;
        private String channelName;
        private Long midConfigId;
        private String channelMid;
        private String midName;
        private String paymentMethodScope;
        private String currencyScope;
        private String defaultTransactionCurrency;
        private Integer midStatus;
        private Integer candidatePriority;
        /** 候选权重以十进制字符串返回，避免浏览器浮点与科学计数法。 */
        private String candidateWeight;
        private Integer candidateStatus;
    }

    /** 单项配置校验结果，使用稳定英文编码供双语前端映射。 */
    @Data
    public static class ValidationIssue {
        private String level;
        private String code;
        private String field;
        private String message;
    }

    /** 路由规则发布前校验汇总。 */
    @Data
    public static class RouteValidationResponse {
        private Long ruleId;
        private Integer version;
        private boolean valid;
        private List<ValidationIssue> issues = new ArrayList<>();
    }

    /** 路由变更日志分页查询条件。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RouteChangeLogQuery extends PageRequest {
        private String actionType;
    }

    /** 路由变更日志响应，快照不包含渠道密钥、MID 元数据等敏感配置。 */
    @Data
    public static class RouteChangeLogResponse {
        private Long id;
        private Long ruleId;
        private String ruleCode;
        private String actionType;
        private Integer beforeVersion;
        private Integer afterVersion;
        private String beforeSnapshotJson;
        private String afterSnapshotJson;
        private Long operatorAccountId;
        private String operatorName;
        private String operationReason;
        private LocalDateTime createTime;
    }
}
