package com.scott.payment.admin.client.data.dto;

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
 * Admin 调用 Data 异步导出内部接口的客户端模型，与浏览器契约保持隔离。
 */
public final class AdminDataExportTaskClientDTOs {

    private AdminDataExportTaskClientDTOs() {
    }

    /**
     * 创建统一待办导出任务时冻结的可信条件和商户范围。
     */
    @Data
    public static class CreateRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestId;
        private String taskType;
        private String taskName;
        private Long creatorAccountId;
        private String creatorAccountName;
        private String locale;
        private String timezone;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private WorkItemQuery workItemQuery;
        private TransactionOrderQuery transactionOrderQuery;
        private TransactionOrderQuery transactionOperationQuery;
        private MerchantNotificationQuery merchantNotificationQuery;
        private RefundQuery refundQuery;
        private SettlementTransactionQuery settlementTransactionQuery;
        private SettlementReserveQuery settlementReserveQuery;
        private CardBinQuery cardBinQuery;
        private CountryQuery countryQuery;
        private CurrencyQuery currencyQuery;
        private RegionCurrencyQuery regionCurrencyQuery;
        private MccCodeQuery mccCodeQuery;
        private DepartmentQuery departmentQuery;
        private PostQuery postQuery;
        private DictTypeQuery dictTypeQuery;
        private DictDataQuery dictDataQuery;
        private HolidayCalendarQuery holidayCalendarQuery;
        private JobRunLogQuery jobRunLogQuery;
        private ChannelMatchAbnormalQuery channelMatchAbnormalQuery;
        private SettlementReviewQuery settlementReviewQuery;
        private SettlementPostingQuery settlementPostingQuery;
        private SecurityInterceptEventQuery securityInterceptEventQuery;
        private FundAccountQuery fundAccountQuery;
        private FundLedgerQuery fundLedgerQuery;
        private FundRechargeQuery fundRechargeQuery;
        private FundDeductionQuery fundDeductionQuery;
        private ExchangeRawRateQuery exchangeRawRateQuery;
        private ExchangeBusinessRateQuery exchangeBusinessRateQuery;
        private ExchangeUsageSnapshotQuery exchangeUsageSnapshotQuery;
        private ExchangeSourceQuery exchangeSourceQuery;
        private ExchangeRuleQuery exchangeRuleQuery;
        private ExchangeVolatilityQuery exchangeVolatilityQuery;
        private ExchangeAlertRuleQuery exchangeAlertRuleQuery;
        private ExchangeAlertEventQuery exchangeAlertEventQuery;
        private FeePlanQuery feeTemplateQuery;
        private FeePlanQuery merchantFeeQuery;
        private FeePlanQuery feeReviewQuery;
        private FeeSimulationQuery feeSimulationQuery;
        private SystemUserQuery systemUserQuery;
        private SystemConfigQuery systemConfigQuery;
        private MerchantIpWhitelistQuery merchantIpWhitelistQuery;
        private RiskConfigQuery riskConfigQuery;
        private DatasourceSnapshotQuery datasourceSnapshotQuery;
    }

    /** 风控配置功能身份与公开筛选条件快照。 */
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

    /** 数据源快照空查询标记，实际数据只允许从服务端实时快照读取。 */
    @Data
    public static class DatasourceSnapshotQuery implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    /**
     * 冻结的统一待办筛选条件，不向 Data 传递页面当前页码。
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
     * 冻结的交易主单筛选条件，不向 Data 传递页面当前页码。
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
     * 冻结的商户通知筛选条件，不向 Data 传递页面当前页码。
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
     * 冻结的退款筛选条件，不向 Data 传递页面当前页码。
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
     * 冻结的结算交易明细筛选条件，不携带页面分页参数或可信商户范围。
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
     * 冻结的保证金结算明细筛选条件，不携带页面分页参数或可信商户范围。
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
     * 冻结的卡 BIN 筛选条件，不携带浏览器列表的页码和每页条数。
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

    /** 冻结的国家和地区筛选条件，不携带页面分页参数。 */
    @Data
    public static class CountryQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String continentCode;
        private Integer status;
    }

    /** 冻结的币种筛选条件，不携带页面分页参数。 */
    @Data
    public static class CurrencyQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private Integer status;
    }

    /** 冻结的国家默认币种关系筛选条件，不携带页面分页参数。 */
    @Data
    public static class RegionCurrencyQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String continentCode;
    }

    /** 冻结的 MCC 编码筛选条件，不携带页面树状态。 */
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

    /** 部门导出查询标记，防止任务类型与空查询快照错配。 */
    @Data
    public static class DepartmentQuery implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    /** 岗位导出冻结条件，不包含浏览器分页参数。 */
    @Data
    public static class PostQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String postCode;
        private String postName;
        private Integer status;
    }

    /** 字典类型导出冻结条件，不包含浏览器分页参数。 */
    @Data
    public static class DictTypeQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String dictName;
        private String dictType;
        private String bizDomain;
        private Integer status;
    }

    /** 字典数据导出冻结条件，不包含浏览器分页参数。 */
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

    /** 节假日日历导出冻结条件。 */
    @Data
    public static class HolidayCalendarQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private int year;
    }

    /**
     * 冻结的任务运行日志筛选条件，排除分页参数、任务参数和执行结果原文。
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

    /** 冻结的渠道勾兑异常筛选条件，不携带页面分页参数。 */
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

    /** 冻结的结算审核单筛选条件，不携带页面分页参数。 */
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

    /** 冻结的结算入账流水筛选条件，不携带页面分页参数。 */
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

    /** 冻结的安全拦截事件筛选条件，不携带页面分页参数。 */
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

    /** 冻结的资金账户筛选条件，不携带页面分页参数。 */
    @Data
    public static class FundAccountQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String accountStatus;
        private String settlementCurrency;
    }

    /** 冻结的余额流水筛选条件，账户主键为空时表示全局流水。 */
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

    /** 冻结的充值申请筛选条件。 */
    @Data
    public static class FundRechargeQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String merchantId;
        private String rechargeStatus;
    }

    /** 冻结的扣减申请筛选条件。 */
    @Data
    public static class FundDeductionQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String merchantId;
        private String deductionCategory;
        private String deductionStatus;
    }

    /** 冻结的原始汇率筛选条件，不携带页面分页参数。 */
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

    /** 冻结的业务汇率筛选条件，不携带页面分页参数。 */
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

    /** 冻结的汇率使用快照筛选条件，不携带页面分页参数。 */
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

    /** 冻结的汇率源筛选条件，不携带页面分页参数。 */
    @Data
    public static class ExchangeSourceQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String sourceType;
        private Integer sourceStatus;
    }

    /** 冻结的汇率规则筛选条件，不携带页面分页参数。 */
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

    /** 冻结的汇率波动筛选条件，币种对顺序保持页面提交时的语义。 */
    @Data
    public static class ExchangeVolatilityQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rateType;
        private List<ExchangeCurrencyPair> currencyPairs;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    /** 冻结的汇率波动币种对。 */
    @Data
    public static class ExchangeCurrencyPair implements Serializable {
        private static final long serialVersionUID = 1L;
        private String baseCurrency;
        private String quoteCurrency;
    }

    /** 冻结的汇率预警规则筛选条件，不携带页面分页参数。 */
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

    /** 冻结的汇率预警事件筛选条件，不携带页面分页参数。 */
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

    /** 冻结的费用方案筛选条件，不携带浏览器分页参数。 */
    @Data
    public static class FeePlanQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String status;
        private String versionStatus;
    }

    /** 冻结的费用试算记录筛选条件，不携带浏览器分页参数。 */
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
     * 查询当前创建人任务的内部请求。
     */
    @Data
    public static class SearchRequestDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private String taskType;
        private String status;
        private LocalDateTime createdStartTime;
        private LocalDateTime createdEndTime;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 详情和重试共用的任务归属请求。
     */
    @Data
    public static class TaskRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String taskNo;
        private Long creatorAccountId;
        private Long expectedVersion;
    }

    /**
     * 下载请求额外携带操作人和网络来源，仅用于 Data 安全审计。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DownloadRequest extends TaskRequest {
        private static final long serialVersionUID = 1L;
        private String clientIp;
        private String operatorName;
    }

}
