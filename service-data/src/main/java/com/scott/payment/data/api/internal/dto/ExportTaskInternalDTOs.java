package com.scott.payment.data.api.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 异步导出任务内部接口模型，承载经过来源服务鉴权的数据范围快照和任务状态。
 *
 * <p>浏览器不能直接访问本契约；查询条件和数据范围只能由已认证的管理服务创建。</p>
 */
public final class ExportTaskInternalDTOs {

    private ExportTaskInternalDTOs() {
    }

    /**
     * 统一运营待办导出条件快照，与页面查询字段保持同义但不携带分页参数。
     */
    @Data
    public static class WorkItemQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String workItemNo;
        private String keyword;
        private String sourceType;
        private String workStatus;
        private String priority;
        private Long ownerAccountId;
        private Boolean mineOnly;
        private Boolean unassignedOnly;
        private Boolean overdueOnly;
        private LocalDateTime detectedStartTime;
        private LocalDateTime detectedEndTime;
    }

    /**
     * 管理端交易主单导出条件快照，不包含页面分页参数和服务端商户范围。
     */
    @Data
    public static class TransactionOrderQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String merchantOrderNo;
        private String transactionId;
        private String sourceTransactionId;
        private String transactionType;
        private String transactionStatus;
        private String channelCode;
        private String paymentMethod;
        private String paymentBrand;
        private String cardBin;
        private String channelOrderNo;
        private String merchantResponseCode;
        private String channelResponseCode;
        private String authCode;
        private String acquirerReferenceNo;
        private String channelMatchStatus;
        private String reconciliationStatus;
        private String settlementStatus;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private String queryTimeZone;
    }

    /**
     * 管理端商户通知导出条件快照，不包含页面分页参数和服务端商户范围。
     */
    @Data
    public static class MerchantNotificationQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String transactionId;
        private String notifyStatus;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private String queryTimeZone;
    }

    /**
     * 管理端退款导出条件快照，完整保留筛选语义但不携带分页参数。
     */
    @Data
    public static class RefundQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String refundTransactionId;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private String merchantOperationNo;
        private String transactionType;
        private String refundScope;
        private String approvalStatus;
        private String transactionStatus;
        private String requestSource;
        private String channelCode;
        private String channelOrderNo;
        private String acquirerReferenceNo;
        private String paymentMethod;
        private String paymentBrand;
        private String labelCurrency;
        private String transactionCurrency;
        private BigDecimal minimumTransactionAmount;
        private BigDecimal maximumTransactionAmount;
        private String applicantId;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private LocalDateTime completeBeginTime;
        private LocalDateTime completeEndTime;
        private String queryTimeZone;
    }

    /**
     * 管理端结算交易明细导出条件快照，不包含页面分页参数和可信商户范围。
     */
    @Data
    public static class SettlementTransactionQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String merchantId;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private LocalDateTime beginTransactionTime;
        private LocalDateTime endTransactionTime;
        private String resultItemType;
        private String resultRole;
        private String direction;
        private String targetCurrency;
        private String sourceDetailType;
        private LocalDate beginBusinessDate;
        private LocalDate endBusinessDate;
    }

    /**
     * 管理端保证金结算明细导出条件快照，不包含页面分页参数和可信商户范围。
     */
    @Data
    public static class SettlementReserveQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String merchantId;
        private String reserveNo;
        private String reserveActionNo;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private String reserveStatus;
        private String actionType;
        private String currency;
        private LocalDateTime beginTransactionTime;
        private LocalDateTime endTransactionTime;
        private LocalDate beginExpectedReleaseDate;
        private LocalDate endExpectedReleaseDate;
        private LocalDate beginBusinessDate;
        private LocalDate endBusinessDate;
    }

    /**
     * 管理端卡 BIN 导出条件快照，不包含列表页码和每页条数。
     */
    @Data
    public static class CardBinQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String cardBin;
        private String cardBrand;
        private String cardType;
        private String issuerCountryAlpha2;
        private String issuerBank;
        private Integer status;
        private String dataSource;
    }

    /** 管理端国家和地区导出条件快照，不包含列表分页参数。 */
    @Data
    public static class CountryQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String continentCode;
        private Integer status;
    }

    /** 管理端币种导出条件快照，不包含列表分页参数。 */
    @Data
    public static class CurrencyQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private Integer status;
    }

    /** 管理端国家默认币种关系导出条件快照，不包含列表分页参数。 */
    @Data
    public static class RegionCurrencyQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String continentCode;
    }

    /** 管理端 MCC 编码导出条件快照，不包含页面树展开状态。 */
    @Data
    public static class MccCodeQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String mccCode;
        private String nameCn;
        private String nameEn;
        private String nodeType;
        private String riskLevel;
        private Integer status;
    }

    /**
     * 管理端部门导出条件快照；独立空类型用于校验任务与查询的一一对应。
     */
    @Data
    public static class DepartmentQuery implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    /**
     * 管理端岗位导出条件快照，不包含页面分页参数。
     */
    @Data
    public static class PostQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String postCode;
        private String postName;
        private Integer status;
    }

    /**
     * 管理端字典类型导出条件快照，不包含页面分页参数。
     */
    @Data
    public static class DictTypeQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String dictName;
        private String dictType;
        private String bizDomain;
        private Integer status;
    }

    /**
     * 管理端字典数据导出条件快照，不包含页面分页参数。
     */
    @Data
    public static class DictDataQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String dictType;
        private String dictLabel;
        private String dictValue;
        private String parentValue;
        private String locale;
        private Integer status;
    }

    /**
     * 管理端节假日日历导出条件快照，仅冻结所选自然年。
     */
    @Data
    public static class HolidayCalendarQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private int year;
    }

    /**
     * 管理端任务运行日志导出条件快照，不保存任务参数、结果原文或页面分页参数。
     */
    @Data
    public static class JobRunLogQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long jobId;
        private String jobCode;
        private String runStatus;
        private String triggerType;
        private String executorNode;
    }

    /** 管理端渠道勾兑异常导出条件快照，不包含页面分页参数。 */
    @Data
    public static class ChannelMatchAbnormalQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String eventId;
        private String transactionId;
        private String merchantId;
        private String merchantOrderNo;
        private String abnormalType;
        private String abnormalLevel;
        private String eventStatus;
        private String transactionType;
        private String platformStatus;
        private String channelCode;
        private String channelOrderNo;
        private String assignedToId;
        private String detectSource;
        private Integer minimumOccurrenceCount;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private String queryTimeZone;
    }

    /** 管理端结算审核单导出条件快照，不包含页面分页参数。 */
    @Data
    public static class SettlementReviewQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String reviewOrderNo;
        private String merchantId;
        private String reviewType;
        private String reviewStatus;
        private LocalDate beginBusinessDate;
        private LocalDate endBusinessDate;
    }

    /** 管理端结算入账流水导出条件快照，不包含页面分页参数。 */
    @Data
    public static class SettlementPostingQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String merchantId;
        private String ledgerNo;
        private String direction;
        private String operationMode;
        private String currency;
        private LocalDateTime beginPostedTime;
        private LocalDateTime endPostedTime;
    }

    /** 管理端安全拦截事件导出条件快照，不包含页面分页参数。 */
    @Data
    public static class SecurityInterceptEventQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String eventNo;
        private String sourceLayer;
        private String eventType;
        private String riskLevel;
        private String action;
        private String merchantId;
        private String clientIp;
        private String requestPath;
        private String traceId;
        private String requestId;
        private String hitRuleCode;
        private Integer processStatus;
        private String queryTimeZone;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
    }

    /** 管理端资金账户导出条件快照，不包含页面分页参数。 */
    @Data
    public static class FundAccountQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String accountStatus;
        private String settlementCurrency;
    }

    /** 管理端余额流水导出条件快照，账户主键为空时表示全局流水。 */
    @Data
    public static class FundLedgerQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long accountId;
        private String keyword;
        private String merchantId;
        private String accountNo;
        private String currency;
        private String balanceType;
        private String businessType;
        private String direction;
        private LocalDateTime postedStartTime;
        private LocalDateTime postedEndTime;
    }

    /** 管理端充值申请导出条件快照，不包含页面分页参数。 */
    @Data
    public static class FundRechargeQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String merchantId;
        private String rechargeStatus;
    }

    /** 管理端扣减申请导出条件快照，不包含页面分页参数。 */
    @Data
    public static class FundDeductionQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String merchantId;
        private String deductionCategory;
        private String deductionStatus;
    }

    /** 管理端原始汇率事实导出条件快照，不包含页面分页参数。 */
    @Data
    public static class ExchangeRawRateQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private String rateStatus;
        private String createMethod;
        private LocalDateTime publishStartTime;
        private LocalDateTime publishEndTime;
        private LocalDateTime fetchStartTime;
        private LocalDateTime fetchEndTime;
        private LocalDateTime effectiveStartTime;
        private LocalDateTime effectiveEndTime;
    }

    /** 管理端业务汇率事实导出条件快照，不包含页面分页参数。 */
    @Data
    public static class ExchangeBusinessRateQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rateType;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private String rateStatus;
        private String generateMethod;
        private LocalDateTime effectiveStartTime;
        private LocalDateTime effectiveEndTime;
        private LocalDateTime createStartTime;
        private LocalDateTime createEndTime;
    }

    /** 管理端汇率使用快照事实导出条件快照，不包含页面分页参数。 */
    @Data
    public static class ExchangeUsageSnapshotQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rateType;
        private String usageScene;
        private String businessType;
        private String businessNo;
        private String baseCurrency;
        private String quoteCurrency;
        private LocalDateTime appliedStartTime;
        private LocalDateTime appliedEndTime;
    }

    /** 管理端汇率源配置导出条件快照。 */
    @Data
    public static class ExchangeSourceQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String sourceType;
        private Integer sourceStatus;
    }

    /** 管理端汇率规则配置导出条件快照。 */
    @Data
    public static class ExchangeRuleQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rateType;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private String adjustDirection;
        private String adjustMethod;
        private Integer ruleStatus;
    }

    /** 管理端汇率波动导出条件快照。 */
    @Data
    public static class ExchangeVolatilityQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rateType;
        private List<ExchangeCurrencyPair> currencyPairs;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    /** 汇率波动固定方向币种对快照。 */
    @Data
    public static class ExchangeCurrencyPair implements Serializable {
        private static final long serialVersionUID = 1L;
        private String baseCurrency;
        private String quoteCurrency;
    }

    /** 管理端汇率预警规则导出条件快照。 */
    @Data
    public static class ExchangeAlertRuleQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private Integer ruleStatus;
        private Integer systemMandatory;
    }

    /** 管理端汇率预警事件导出条件快照。 */
    @Data
    public static class ExchangeAlertEventQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String eventNo;
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private String eventStatus;
        private String acknowledgedBy;
        private LocalDateTime triggerStartTime;
        private LocalDateTime triggerEndTime;
    }

    /** 管理端费用方案导出条件快照，不包含浏览器分页参数。 */
    @Data
    public static class FeePlanQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String status;
        private String versionStatus;
    }

    /** 管理端费用试算记录导出条件快照，不包含浏览器分页参数。 */
    @Data
    public static class FeeSimulationQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String merchantId;
        private String transactionType;
    }

    /** 系统账户导出冻结条件，不包含浏览器分页参数。 */
    @Data
    public static class SystemUserQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String loginAccount;
        private String mobile;
        private String email;
        private Long deptId;
        private Integer status;
        private String emailVerificationStatus;
        private String activationStatus;
    }

    /** 系统参数导出冻结条件，不包含浏览器分页参数。 */
    @Data
    public static class SystemConfigQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String configName;
        private String configKey;
        private String configGroup;
        private Integer status;
    }

    /** 商户 IP 白名单导出冻结条件，不包含浏览器分页参数。 */
    @Data
    public static class MerchantIpWhitelistQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String merchantKeyword;
        private String ipValue;
        private String ipType;
        private Integer status;
        private Integer approvalStatus;
        private String submitSource;
    }

    /**
     * 商户端交易查询异步导出条件快照，只保存商户页面可提交的筛选条件。
     *
     * <p>商户号由认证上下文单独冻结，渠道、交易汇率和渠道金额均不进入本模型。</p>
     */
    @Data
    public static class MerchantTransactionQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantOrderNo;
        private String transactionId;
        private String sourceTransactionId;
        private String transactionType;
        private String transactionStatus;
        private String paymentMethod;
        private String paymentBrand;
        private String merchantResponseCode;
        private String settlementStatus;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private String queryTimeZone;
    }

    /**
     * 商户端退款异步导出条件快照，只保存商户可见的标签金额和状态筛选条件。
     */
    @Data
    public static class MerchantRefundQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String refundTransactionId;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private String merchantOperationNo;
        private String transactionType;
        private String refundScope;
        private String approvalStatus;
        private String transactionStatus;
        private String requestSource;
        private String labelCurrency;
        private BigDecimal minimumTransactionAmount;
        private BigDecimal maximumTransactionAmount;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private LocalDateTime completeBeginTime;
        private LocalDateTime completeEndTime;
        private String queryTimeZone;
    }

    /**
     * 商户结算批次异步导出条件快照，不保存分页和商户身份字段。
     */
    @Data
    public static class MerchantSettlementBatchQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String batchType;
        private String batchStatus;
        private LocalDate beginBusinessDate;
        private LocalDate endBusinessDate;
    }

    /**
     * 商户批次结算汇总异步导出条件快照。
     */
    @Data
    public static class MerchantSettlementSummaryQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
    }

    /**
     * 商户交易结算异步导出条件快照，不保存渠道成本筛选字段。
     */
    @Data
    public static class MerchantSettlementTransactionQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private LocalDateTime beginTransactionTime;
        private LocalDateTime endTransactionTime;
        private String paymentType;
        private String paymentMethod;
        private String transactionType;
        private String feeCategory;
        private LocalDate beginBusinessDate;
        private LocalDate endBusinessDate;
    }

    /**
     * 商户保证金结算异步导出条件快照，币种保持原标签责任语义。
     */
    @Data
    public static class MerchantSettlementReserveQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String reserveNo;
        private String reserveActionNo;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private String reserveStatus;
        private String actionType;
        private String currency;
        private LocalDateTime beginTransactionTime;
        private LocalDateTime endTransactionTime;
        private LocalDate beginExpectedReleaseDate;
        private LocalDate endExpectedReleaseDate;
        private LocalDate beginBusinessDate;
        private LocalDate endBusinessDate;
    }

    /**
     * 商户余额流水异步导出条件快照，账户主键由认证商户当前资金账户冻结。
     */
    @Data
    public static class MerchantFundLedgerQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long accountId;
        private String keyword;
        private String balanceType;
        private String businessType;
        private LocalDateTime postedStartTime;
        private LocalDateTime postedEndTime;
    }

    /**
     * 创建导出任务命令；商户范围由管理服务根据当前账号重新计算后写入。
     */
    @Data
    public static class CreateRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @NotBlank
        @Size(max = 64)
        private String taskType;
        @NotBlank
        @Size(max = 128)
        private String taskName;
        @Size(max = 32)
        private String creatorAppCode;
        @NotNull
        private Long creatorAccountId;
        @Size(max = 64)
        private String merchantId;
        @NotBlank
        @Size(max = 128)
        private String creatorAccountName;
        @Size(max = 16)
        private String locale;
        @Size(max = 64)
        private String timezone;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        private WorkItemQuery workItemQuery;
        @Valid
        private TransactionOrderQuery transactionOrderQuery;
        @Valid
        private TransactionOrderQuery transactionOperationQuery;
        @Valid
        private MerchantNotificationQuery merchantNotificationQuery;
        @Valid
        private RefundQuery refundQuery;
        @Valid
        private SettlementTransactionQuery settlementTransactionQuery;
        @Valid
        private SettlementReserveQuery settlementReserveQuery;
        @Valid
        private CardBinQuery cardBinQuery;
        @Valid
        private CountryQuery countryQuery;
        @Valid
        private CurrencyQuery currencyQuery;
        @Valid
        private RegionCurrencyQuery regionCurrencyQuery;
        @Valid
        private MccCodeQuery mccCodeQuery;
        @Valid
        private DepartmentQuery departmentQuery;
        @Valid
        private PostQuery postQuery;
        @Valid
        private DictTypeQuery dictTypeQuery;
        @Valid
        private DictDataQuery dictDataQuery;
        @Valid
        private HolidayCalendarQuery holidayCalendarQuery;
        @Valid
        private JobRunLogQuery jobRunLogQuery;
        @Valid
        private ChannelMatchAbnormalQuery channelMatchAbnormalQuery;
        @Valid
        private SettlementReviewQuery settlementReviewQuery;
        @Valid
        private SettlementPostingQuery settlementPostingQuery;
        @Valid
        private SecurityInterceptEventQuery securityInterceptEventQuery;
        @Valid
        private FundAccountQuery fundAccountQuery;
        @Valid
        private FundLedgerQuery fundLedgerQuery;
        @Valid
        private FundRechargeQuery fundRechargeQuery;
        @Valid
        private FundDeductionQuery fundDeductionQuery;
        @Valid
        private ExchangeRawRateQuery exchangeRawRateQuery;
        @Valid
        private ExchangeBusinessRateQuery exchangeBusinessRateQuery;
        @Valid
        private ExchangeUsageSnapshotQuery exchangeUsageSnapshotQuery;
        @Valid
        private ExchangeSourceQuery exchangeSourceQuery;
        @Valid
        private ExchangeRuleQuery exchangeRuleQuery;
        @Valid
        private ExchangeVolatilityQuery exchangeVolatilityQuery;
        @Valid
        private ExchangeAlertRuleQuery exchangeAlertRuleQuery;
        @Valid
        private ExchangeAlertEventQuery exchangeAlertEventQuery;
        @Valid
        private FeePlanQuery feeTemplateQuery;
        @Valid
        private FeePlanQuery merchantFeeQuery;
        @Valid
        private FeePlanQuery feeReviewQuery;
        @Valid
        private FeeSimulationQuery feeSimulationQuery;
        @Valid
        private SystemUserQuery systemUserQuery;
        @Valid
        private SystemConfigQuery systemConfigQuery;
        @Valid
        private MerchantIpWhitelistQuery merchantIpWhitelistQuery;
        @Valid
        private RiskConfigQuery riskConfigQuery;
        @Valid
        private DatasourceSnapshotQuery datasourceSnapshotQuery;
        @Valid
        private MerchantTransactionQuery merchantTransactionQuery;
        @Valid
        private MerchantRefundQuery merchantRefundQuery;
        @Valid
        private MerchantSettlementBatchQuery merchantSettlementBatchQuery;
        @Valid
        private MerchantSettlementSummaryQuery merchantSettlementSummaryQuery;
        @Valid
        private MerchantSettlementTransactionQuery merchantSettlementTransactionQuery;
        @Valid
        private MerchantSettlementReserveQuery merchantSettlementReserveQuery;
        @Valid
        private MerchantFundLedgerQuery merchantFundLedgerQuery;
    }

    /** 风控配置受控功能身份与公开筛选条件快照。 */
    @Data
    public static class RiskConfigQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String moduleType;
        private String functionCode;
        private String merchantScope;
        private String merchantId;
        private String ruleName;
        private String matchValue;
        private String countryAlpha2;
        private String sourceUrl;
        private String sourceHost;
        private String limitType;
        private String ruleType;
        private String channelCode;
        private String paymentMethod;
        private String cardBrand;
        private String currency;
        private String triggerAction;
        private Integer status;
        private Integer approvalStatus;
        private String submitSource;
    }

    /** 数据源快照空查询标记，禁止冻结连接凭据或其他基础设施秘密。 */
    @Data
    public static class DatasourceSnapshotQuery implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    /**
     * 查询当前创建人的导出任务，第一批不提供跨账号查看能力。
     */
    @Data
    public static class SearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @Size(max = 32)
        private String creatorAppCode;
        @NotNull
        private Long creatorAccountId;
        @Size(max = 64)
        private String merchantId;
        @Size(max = 64)
        private String taskType;
        @Size(max = 32)
        private String status;
        private LocalDateTime createdStartTime;
        private LocalDateTime createdEndTime;
        @Min(1)
        private Integer pageNo;
        @Min(1)
        @Max(100)
        private Integer pageSize;
    }

    /**
     * 单任务身份请求，用于详情和重试时的归属校验。
     */
    @Data
    public static class TaskRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 64)
        private String taskNo;
        @Size(max = 32)
        private String creatorAppCode;
        @NotNull
        private Long creatorAccountId;
        @Size(max = 64)
        private String merchantId;
        private Long expectedVersion;
    }

    /**
     * 下载命令，客户端 IP 仅写入安全审计，不参与对象键生成。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DownloadRequest extends TaskRequest {
        private static final long serialVersionUID = 1L;
        @Size(max = 64)
        private String clientIp;
        @Size(max = 128)
        private String operatorName;
    }

    /**
     * Job 触发的有界领取和过期清理请求。
     */
    @Data
    public static class DispatchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Min(1)
        @Max(50)
        private Integer batchSize;
        @NotNull
        private LocalDateTime dispatchTime;
    }

    /**
     * 导出任务列表摘要，不返回对象键、条件 JSON 或数据范围 JSON。
     */
    @Data
    public static class TaskSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private String taskNo;
        private String taskType;
        private String taskName;
        private String status;
        private Integer progressPercent;
        private Long processedRows;
        private Long totalRows;
        private String fileName;
        private Long fileSizeBytes;
        private String fileSha256;
        private LocalDateTime expiresAt;
        private String failureCode;
        private String failureReason;
        private Integer retryCount;
        private Integer maxRetryCount;
        private Long version;
        private LocalDateTime createTime;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
    }

    /**
     * 单轮调度结果，只汇总领取、提交、延后和恢复数量，不把条件或失败堆栈写入 Job 日志。
     */
    @Data
    public static class DispatchResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestId;
        private Integer claimedCount;
        private Integer submittedCount;
        private Integer deferredCount;
        private Integer recoveredCount;
        private Integer expiredCount;
    }
}
