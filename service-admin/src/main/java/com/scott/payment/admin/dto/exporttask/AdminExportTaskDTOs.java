package com.scott.payment.admin.dto.exporttask;

import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchRequest;
import com.scott.payment.admin.dto.base.CardBinDTOs.CardBinQueryRequest;
import com.scott.payment.admin.dto.base.MccRequests.MccTreeQueryRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogQueryRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDeductionQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDetailQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeQuery;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.admin.dto.SysConfigQueryRequest;
import com.scott.payment.admin.dto.SysUserAccountQueryRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistQuery;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端异步导出与下载中心契约，浏览器只能提交查询条件和幂等键。
 */
public final class AdminExportTaskDTOs {

    private AdminExportTaskDTOs() {
    }

    /** 风控配置异步导出提交命令，模块和功能码必须命中服务端受控定义。 */
    @Data
    public static class RiskConfigSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @NotBlank
        @Size(max = 16)
        private String moduleType;
        @NotBlank
        @Size(max = 64)
        private String functionCode;
        @Valid
        @NotNull
        private RiskConfigQuery query;
    }

    /** 数据源运行快照异步导出提交命令，不接受浏览器指定数据源连接信息。 */
    @Data
    public static class DatasourceSnapshotSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
    }

    /** 风控配置公开筛选条件并集；执行时由服务端功能定义决定允许使用的字段。 */
    @Data
    public static class RiskConfigQuery implements Serializable {
        private static final long serialVersionUID = 1L;
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

    /**
     * 统一待办导出提交命令，商户范围和操作人由服务端可信上下文补齐。
     */
    @Data
    public static class WorkItemSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private SearchRequest query;
    }

    /**
     * 交易主单异步导出提交命令，账号和商户范围由服务端可信上下文补齐。
     */
    @Data
    public static class TransactionOrderSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private TransactionPageQuery query;
    }

    /**
     * 交易操作流水异步导出提交命令，账号和商户范围由服务端可信上下文补齐。
     */
    @Data
    public static class TransactionOperationSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private TransactionPageQuery query;
    }

    /**
     * 商户通知记录异步导出提交命令，账号和商户范围由服务端可信上下文补齐。
     */
    @Data
    public static class MerchantNotificationSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private MerchantNotificationQuery query;
    }

    /**
     * 退款异步导出提交命令，账号和商户范围由服务端可信上下文补齐。
     */
    @Data
    public static class RefundSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private RefundQuery query;
    }

    /**
     * 结算交易明细异步导出提交命令，账号和商户范围由服务端可信上下文补齐。
     */
    @Data
    public static class SettlementTransactionSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private ResultItemSearchRequest query;
    }

    /**
     * 保证金结算明细异步导出提交命令，账号和商户范围由服务端可信上下文补齐。
     */
    @Data
    public static class SettlementReserveSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private ReserveItemSearchRequest query;
    }

    /**
     * 卡 BIN 异步导出提交命令，账号、语言和数据范围由服务端可信上下文补齐。
     */
    @Data
    public static class CardBinSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private CardBinQueryRequest query;
    }

    /**
     * 国家和地区异步导出提交命令，只接受列表页公开筛选条件。
     */
    @Data
    public static class CountrySubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private CountryQuery query;
    }

    /**
     * 币种异步导出提交命令，只接受列表页公开筛选条件。
     */
    @Data
    public static class CurrencySubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private CurrencyQuery query;
    }

    /**
     * 国家默认币种关系异步导出提交命令，只接受列表页公开筛选条件。
     */
    @Data
    public static class RegionCurrencySubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private RegionCurrencyQuery query;
    }

    /**
     * MCC 编码异步导出提交命令，完整冻结当前树查询条件。
     */
    @Data
    public static class MccCodeSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private MccTreeQueryRequest query;
    }

    /**
     * 部门异步导出提交命令；当前部门树没有公开筛选条件，仍使用空查询对象固定契约。
     */
    @Data
    public static class DepartmentSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private DepartmentQuery query;
    }

    /**
     * 岗位异步导出提交命令，完整冻结列表页公开筛选条件。
     */
    @Data
    public static class PostSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private PostQuery query;
    }

    /**
     * 字典类型异步导出提交命令，完整冻结列表页公开筛选条件。
     */
    @Data
    public static class DictTypeSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private DictTypeQuery query;
    }

    /**
     * 字典数据异步导出提交命令，完整冻结当前字典和列表页公开筛选条件。
     */
    @Data
    public static class DictDataSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private DictDataQuery query;
    }

    /**
     * 节假日日历异步导出提交命令，只冻结用户选择的自然年。
     */
    @Data
    public static class HolidayCalendarSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private HolidayCalendarQuery query;
    }

    /**
     * 国家和地区导出筛选条件，不包含列表分页参数。
     */
    @Data
    public static class CountryQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String continentCode;
        private Integer status;
    }

    /**
     * 币种导出筛选条件，不包含列表分页参数。
     */
    @Data
    public static class CurrencyQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private Integer status;
    }

    /**
     * 国家默认币种关系导出筛选条件，不包含列表分页参数。
     */
    @Data
    public static class RegionCurrencyQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String continentCode;
    }

    /**
     * 部门导出查询标记；保留独立类型防止任务类型与查询快照错配。
     */
    @Data
    public static class DepartmentQuery implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    /**
     * 岗位导出筛选条件，不包含列表分页参数。
     */
    @Data
    public static class PostQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        @Size(max = 64)
        private String postCode;
        @Size(max = 100)
        private String postName;
        private Integer status;
    }

    /**
     * 字典类型导出筛选条件，不包含列表分页参数。
     */
    @Data
    public static class DictTypeQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        @Size(max = 100)
        private String dictName;
        @Size(max = 100)
        private String dictType;
        @Size(max = 64)
        private String bizDomain;
        private Integer status;
    }

    /**
     * 字典数据导出筛选条件，不包含列表分页参数。
     */
    @Data
    public static class DictDataQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        @Size(max = 100)
        private String dictType;
        @Size(max = 100)
        private String dictLabel;
        @Size(max = 100)
        private String dictValue;
        @Size(max = 100)
        private String parentValue;
        @Size(max = 16)
        private String locale;
        private Integer status;
    }

    /**
     * 节假日日历导出筛选条件，年份与日历维护契约保持一致。
     */
    @Data
    public static class HolidayCalendarQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        @Min(2000)
        @Max(2100)
        private int year;
    }

    /**
     * 任务运行日志异步导出提交命令，只允许冻结公开筛选字段，不保存参数快照。
     */
    @Data
    public static class JobRunLogSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private JobRunLogQueryRequest query;
    }

    /** 渠道勾兑异常异步导出提交命令。 */
    @Data
    public static class ChannelMatchAbnormalSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private AbnormalQuery query;
    }

    /** 结算审核单异步导出提交命令。 */
    @Data
    public static class SettlementReviewSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private ReviewSearchRequest query;
    }

    /** 结算入账流水异步导出提交命令。 */
    @Data
    public static class SettlementPostingSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private PostingSearchRequest query;
    }

    /** 安全拦截事件异步导出提交命令。 */
    @Data
    public static class SecurityInterceptEventSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private SecurityInterceptEventQuery query;
    }

    /** 资金账户异步导出提交命令。 */
    @Data
    public static class FundAccountSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private FundAccountQuery query;
    }

    /** 余额流水异步导出提交命令，账户主键为空时表示全局流水。 */
    @Data
    public static class FundLedgerSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        private Long accountId;
        @Valid
        @NotNull
        private FundDetailQuery query;
    }

    /** 充值申请异步导出提交命令。 */
    @Data
    public static class FundRechargeSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private FundRechargeQuery query;
    }

    /** 扣减申请异步导出提交命令。 */
    @Data
    public static class FundDeductionSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private FundDeductionQuery query;
    }

    /** 原始汇率事实异步导出提交命令。 */
    @Data
    public static class ExchangeRawRateSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private RawRateQuery query;
    }

    /** 业务汇率事实异步导出提交命令。 */
    @Data
    public static class ExchangeBusinessRateSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private BusinessRateQuery query;
    }

    /** 汇率使用快照事实异步导出提交命令。 */
    @Data
    public static class ExchangeUsageSnapshotSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private UsageSnapshotQuery query;
    }

    /** 汇率源配置异步导出提交命令。 */
    @Data
    public static class ExchangeSourceSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private SourceQuery query;
    }

    /** 汇率规则配置异步导出提交命令。 */
    @Data
    public static class ExchangeRuleSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private RuleQuery query;
    }

    /** 汇率波动摘要异步导出提交命令。 */
    @Data
    public static class ExchangeVolatilitySubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private VolatilityQuery query;
    }

    /** 汇率预警规则异步导出提交命令。 */
    @Data
    public static class ExchangeAlertRuleSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private AlertRuleQuery query;
    }

    /** 汇率预警事件异步导出提交命令。 */
    @Data
    public static class ExchangeAlertEventSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private AlertEventQuery query;
    }

    /** 费用模板异步导出提交命令。 */
    @Data
    public static class FeeTemplateSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private FeePlanQuery query;
    }

    /** 商户费率异步导出提交命令。 */
    @Data
    public static class MerchantFeeSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private FeePlanQuery query;
    }

    /** 费率复核异步导出提交命令。 */
    @Data
    public static class FeeReviewSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private FeePlanQuery query;
    }

    /** 费用试算记录异步导出提交命令。 */
    @Data
    public static class FeeSimulationSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private FeeSimulationRecordQuery query;
    }

    /** 系统账户异步导出提交命令，只接受页面公开筛选条件。 */
    @Data
    public static class SystemUserSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private SysUserAccountQueryRequest query;
    }

    /** 系统参数异步导出提交命令，敏感值由服务端导出边界统一脱敏。 */
    @Data
    public static class SystemConfigSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private SysConfigQueryRequest query;
    }

    /** 商户 IP 白名单异步导出提交命令，商户范围由可信账号上下文冻结。 */
    @Data
    public static class MerchantIpWhitelistSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private MerchantIpWhitelistQuery query;
    }

    /**
     * 当前操作人下载中心查询条件。
     */
    @Data
    public static class SearchRequestDTO implements Serializable {
        private static final long serialVersionUID = 1L;
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
     * 人工重试命令，预期版本用于阻止重复点击覆盖并发状态。
     */
    @Data
    public static class RetryRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long expectedVersion;
    }

    /**
     * 导出任务摘要，不包含对象键、查询快照和商户数据范围快照。
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
}
