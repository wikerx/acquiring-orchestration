package com.scott.payment.data.client.admin;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.data.client.admin.dto.AdminWorkItemExportSourceDTOs.PageRequest;
import com.scott.payment.data.client.admin.dto.AdminWorkItemExportSourceDTOs.PageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.OrderPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.OrderPageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.OperationPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.OperationPageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.MerchantNotificationPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.MerchantNotificationPageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.RefundPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.RefundPageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.ChannelMatchAbnormalPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.ChannelMatchAbnormalPageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReservePageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReservePageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementTransactionPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementTransactionPageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementPostingPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementPostingPageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReviewPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReviewPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CardBinPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CardBinPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CountryPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CountryPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CurrencyPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CurrencyPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DepartmentPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DepartmentPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictDataPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictDataPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictTypePageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictTypePageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.HolidayCalendarPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.HolidayCalendarPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.JobRunLogPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.JobRunLogPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MccCodePageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MccCodePageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.PostPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.PostPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.RegionCurrencyPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.RegionCurrencyPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SecurityInterceptEventPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SecurityInterceptEventPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemUserPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemUserPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemConfigPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemConfigPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MerchantIpWhitelistPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MerchantIpWhitelistPageResponse;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.RiskPageRequest;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.RiskPageResponse;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.DatasourcePageRequest;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.DatasourcePageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundAccountPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundAccountPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundDeductionPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundDeductionPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundLedgerPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundLedgerPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundRechargePageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundRechargePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.BusinessRatePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.BusinessRatePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RawRatePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RawRatePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.UsageSnapshotPageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.UsageSnapshotPageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.SourcePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.SourcePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RulePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RulePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.VolatilityPageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.VolatilityPageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertRulePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertRulePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertEventPageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertEventPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeePlanPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeReviewPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeSimulationPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeSimulationPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeTemplatePageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.MerchantFeePageResponse;
import com.scott.payment.data.config.export.DataAdminClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admin 导出源 REST 客户端，使用 HMAC 请求签名并统一解包内部响应。
 */
@Slf4j
@Service
public class AdminWorkItemExportSourceRestClient
        implements AdminWorkItemExportSourceClient, AdminTransactionExportSourceClient,
        AdminSettlementExportSourceClient, AdminPlatformExportSourceClient,
        AdminFundExportSourceClient, AdminExchangeExportSourceClient,
        AdminFeeExportSourceClient {
    private static final String WORK_ITEM_PATH = "/internal/admin/operations/work-items/export-page";
    private static final String ORDER_PATH = "/internal/admin/transactions/orders/export-page";
    private static final String OPERATION_PATH = "/internal/admin/transactions/operations/export-page";
    private static final String MERCHANT_NOTIFICATION_PATH =
            "/internal/admin/transactions/merchant-notifications/export-page";
    private static final String REFUND_PATH = "/internal/admin/transactions/refunds/export-page";
    private static final String SETTLEMENT_TRANSACTION_PATH =
            "/internal/admin/settlement/result-items/export-page";
    private static final String SETTLEMENT_RESERVE_PATH =
            "/internal/admin/settlement/reserve-items/export-page";
    private static final String CARD_BIN_PATH = "/internal/admin/base/card-bin/export-page";
    private static final String COUNTRY_PATH = "/internal/admin/base/countries/export-page";
    private static final String CURRENCY_PATH = "/internal/admin/base/currencies/export-page";
    private static final String REGION_CURRENCY_PATH =
            "/internal/admin/base/region-currencies/export-page";
    private static final String MCC_CODE_PATH = "/internal/admin/base/mcc-codes/export-page";
    private static final String DEPARTMENT_PATH = "/internal/admin/system/departments/export-page";
    private static final String POST_PATH = "/internal/admin/system/posts/export-page";
    private static final String DICT_TYPE_PATH = "/internal/admin/system/dict-types/export-page";
    private static final String DICT_DATA_PATH = "/internal/admin/system/dict-data/export-page";
    private static final String HOLIDAY_CALENDAR_PATH =
            "/internal/admin/system/holiday-calendar/export-page";
    private static final String JOB_RUN_LOG_PATH = "/internal/admin/monitor/job-logs/export-page";
    private static final String CHANNEL_MATCH_ABNORMAL_PATH =
            "/internal/admin/transactions/channel-match-abnormalities/export-page";
    private static final String SETTLEMENT_REVIEW_PATH =
            "/internal/admin/settlement/review-orders/export-page";
    private static final String SETTLEMENT_POSTING_PATH =
            "/internal/admin/settlement/postings/export-page";
    private static final String SECURITY_INTERCEPT_EVENT_PATH =
            "/internal/admin/security/intercept-events/export-page";
    private static final String FUND_ACCOUNT_PATH = "/internal/admin/fund/accounts/export-page";
    private static final String FUND_LEDGER_PATH = "/internal/admin/fund/ledgers/export-page";
    private static final String FUND_RECHARGE_PATH = "/internal/admin/fund/recharges/export-page";
    private static final String FUND_DEDUCTION_PATH = "/internal/admin/fund/deductions/export-page";
    private static final String EXCHANGE_RAW_RATE_PATH =
            "/internal/admin/exchange/raw-rates/export-page";
    private static final String EXCHANGE_BUSINESS_RATE_PATH =
            "/internal/admin/exchange/business-rates/export-page";
    private static final String EXCHANGE_USAGE_SNAPSHOT_PATH =
            "/internal/admin/exchange/usage-snapshots/export-page";
    private static final String EXCHANGE_SOURCE_PATH =
            "/internal/admin/exchange/sources/export-page";
    private static final String EXCHANGE_RULE_PATH =
            "/internal/admin/exchange/rules/export-page";
    private static final String EXCHANGE_VOLATILITY_PATH =
            "/internal/admin/exchange/volatility/export-page";
    private static final String EXCHANGE_ALERT_RULE_PATH =
            "/internal/admin/exchange/alert-rules/export-page";
    private static final String EXCHANGE_ALERT_EVENT_PATH =
            "/internal/admin/exchange/alert-events/export-page";
    private static final String FEE_TEMPLATE_PATH = "/internal/admin/fees/templates/export-page";
    private static final String MERCHANT_FEE_PATH = "/internal/admin/fees/merchants/export-page";
    private static final String FEE_REVIEW_PATH = "/internal/admin/fees/reviews/export-page";
    private static final String FEE_SIMULATION_PATH =
            "/internal/admin/fees/simulation-records/export-page";
    private static final String SYSTEM_USER_PATH = "/internal/admin/system/users/export-page";
    private static final String SYSTEM_CONFIG_PATH = "/internal/admin/system/configs/export-page";
    private static final String MERCHANT_IP_WHITELIST_PATH =
            "/internal/admin/merchant/ip-whitelists/export-page";
    private static final String RISK_CONFIG_PATH = "/internal/admin/risk/configs/export-page";
    private static final String DATASOURCE_SNAPSHOT_PATH =
            "/internal/admin/monitor/datasource-snapshot/export-page";
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private final RestTemplate direct;
    private final RestTemplate loadBalanced;
    private final DataAdminClientProperties properties;

    /**
     * 创建只访问 Admin 导出源路径的内部客户端。
     */
    public AdminWorkItemExportSourceRestClient(
            @Qualifier("dataAdminInternalRestTemplate") RestTemplate direct,
            @Qualifier("dataAdminInternalLoadBalancedRestTemplate") RestTemplate loadBalanced,
            DataAdminClientProperties properties) {
        this.direct = direct;
        this.loadBalanced = loadBalanced;
        this.properties = properties;
    }

    /**
     * 请求一页数据，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public PageResponse loadPage(PageRequest request) {
        return post(WORK_ITEM_PATH, request, new TypeReference<CommonResult<PageResponse>>() { });
    }

    /**
     * 请求一页交易主单，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public OrderPageResponse loadOrderPage(OrderPageRequest request) {
        return post(ORDER_PATH, request, new TypeReference<CommonResult<OrderPageResponse>>() { });
    }

    /**
     * 请求一页交易操作流水，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public OperationPageResponse loadOperationPage(OperationPageRequest request) {
        return post(OPERATION_PATH, request,
                new TypeReference<CommonResult<OperationPageResponse>>() { });
    }

    /**
     * 请求一页商户通知记录，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public MerchantNotificationPageResponse loadMerchantNotificationPage(
            MerchantNotificationPageRequest request) {
        return post(MERCHANT_NOTIFICATION_PATH, request,
                new TypeReference<CommonResult<MerchantNotificationPageResponse>>() { });
    }

    /**
     * 请求一页退款记录，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public RefundPageResponse loadRefundPage(RefundPageRequest request) {
        return post(REFUND_PATH, request, new TypeReference<CommonResult<RefundPageResponse>>() { });
    }

    /**
     * 请求一页结算交易明细，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public SettlementTransactionPageResponse loadSettlementTransactionPage(
            SettlementTransactionPageRequest request) {
        return post(SETTLEMENT_TRANSACTION_PATH, request,
                new TypeReference<CommonResult<SettlementTransactionPageResponse>>() { });
    }

    /**
     * 请求一页保证金结算明细，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public SettlementReservePageResponse loadSettlementReservePage(
            SettlementReservePageRequest request) {
        return post(SETTLEMENT_RESERVE_PATH, request,
                new TypeReference<CommonResult<SettlementReservePageResponse>>() { });
    }

    /**
     * 请求一页卡 BIN 安全投影，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public CardBinPageResponse loadCardBinPage(CardBinPageRequest request) {
        return post(CARD_BIN_PATH, request,
                new TypeReference<CommonResult<CardBinPageResponse>>() { });
    }

    /** 请求一页风控配置动态安全投影。 */
    @Override
    public RiskPageResponse loadRiskConfigPage(RiskPageRequest request) {
        return post(RISK_CONFIG_PATH, request,
                new TypeReference<CommonResult<RiskPageResponse>>() { });
    }

    /** 请求一页数据源脱敏运行快照。 */
    @Override
    public DatasourcePageResponse loadDatasourceSnapshotPage(DatasourcePageRequest request) {
        return post(DATASOURCE_SNAPSHOT_PATH, request,
                new TypeReference<CommonResult<DatasourcePageResponse>>() { });
    }

    /**
     * 请求一页国家和地区安全投影。
     *
     * @param request 冻结条件和分页参数
     * @return 国家和地区分页
     */
    @Override
    public CountryPageResponse loadCountryPage(CountryPageRequest request) {
        return post(COUNTRY_PATH, request,
                new TypeReference<CommonResult<CountryPageResponse>>() { });
    }

    /**
     * 请求一页币种安全投影。
     *
     * @param request 冻结条件和分页参数
     * @return 币种分页
     */
    @Override
    public CurrencyPageResponse loadCurrencyPage(CurrencyPageRequest request) {
        return post(CURRENCY_PATH, request,
                new TypeReference<CommonResult<CurrencyPageResponse>>() { });
    }

    /**
     * 请求一页国家默认币种关系安全投影。
     *
     * @param request 冻结条件和分页参数
     * @return 国家默认币种关系分页
     */
    @Override
    public RegionCurrencyPageResponse loadRegionCurrencyPage(RegionCurrencyPageRequest request) {
        return post(REGION_CURRENCY_PATH, request,
                new TypeReference<CommonResult<RegionCurrencyPageResponse>>() { });
    }

    /**
     * 请求一页 MCC 编码安全投影。
     *
     * @param request 冻结条件和分页参数
     * @return MCC 编码分页
     */
    @Override
    public MccCodePageResponse loadMccCodePage(MccCodePageRequest request) {
        return post(MCC_CODE_PATH, request,
                new TypeReference<CommonResult<MccCodePageResponse>>() { });
    }

    /**
     * 使用 Data-Admin 内部签名请求一页部门安全投影。
     */
    @Override
    public DepartmentPageResponse loadDepartmentPage(DepartmentPageRequest request) {
        return post(DEPARTMENT_PATH, request,
                new TypeReference<CommonResult<DepartmentPageResponse>>() { });
    }

    /**
     * 使用 Data-Admin 内部签名请求一页岗位安全投影。
     */
    @Override
    public PostPageResponse loadPostPage(PostPageRequest request) {
        return post(POST_PATH, request,
                new TypeReference<CommonResult<PostPageResponse>>() { });
    }

    /**
     * 使用 Data-Admin 内部签名请求一页字典类型安全投影。
     */
    @Override
    public DictTypePageResponse loadDictTypePage(DictTypePageRequest request) {
        return post(DICT_TYPE_PATH, request,
                new TypeReference<CommonResult<DictTypePageResponse>>() { });
    }

    /**
     * 使用 Data-Admin 内部签名请求一页字典数据安全投影。
     */
    @Override
    public DictDataPageResponse loadDictDataPage(DictDataPageRequest request) {
        return post(DICT_DATA_PATH, request,
                new TypeReference<CommonResult<DictDataPageResponse>>() { });
    }

    /**
     * 使用 Data-Admin 内部签名请求一页节假日日历安全投影。
     */
    @Override
    public HolidayCalendarPageResponse loadHolidayCalendarPage(HolidayCalendarPageRequest request) {
        return post(HOLIDAY_CALENDAR_PATH, request,
                new TypeReference<CommonResult<HolidayCalendarPageResponse>>() { });
    }

    /**
     * 请求一页任务运行日志安全投影，网络和鉴权失败统一映射为可重试的网关错误。
     */
    @Override
    public JobRunLogPageResponse loadJobRunLogPage(JobRunLogPageRequest request) {
        return post(JOB_RUN_LOG_PATH, request,
                new TypeReference<CommonResult<JobRunLogPageResponse>>() { });
    }

    /** 请求一页渠道勾兑异常安全投影。 */
    @Override
    public ChannelMatchAbnormalPageResponse loadChannelMatchAbnormalPage(
            ChannelMatchAbnormalPageRequest request) {
        return post(CHANNEL_MATCH_ABNORMAL_PATH, request,
                new TypeReference<CommonResult<ChannelMatchAbnormalPageResponse>>() { });
    }

    /** 请求一页结算审核单安全投影。 */
    @Override
    public SettlementReviewPageResponse loadSettlementReviewPage(
            SettlementReviewPageRequest request) {
        return post(SETTLEMENT_REVIEW_PATH, request,
                new TypeReference<CommonResult<SettlementReviewPageResponse>>() { });
    }

    /** 请求一页结算入账流水安全投影。 */
    @Override
    public SettlementPostingPageResponse loadSettlementPostingPage(
            SettlementPostingPageRequest request) {
        return post(SETTLEMENT_POSTING_PATH, request,
                new TypeReference<CommonResult<SettlementPostingPageResponse>>() { });
    }

    /** 请求一页安全拦截事件安全投影。 */
    @Override
    public SecurityInterceptEventPageResponse loadSecurityInterceptEventPage(
            SecurityInterceptEventPageRequest request) {
        return post(SECURITY_INTERCEPT_EVENT_PATH, request,
                new TypeReference<CommonResult<SecurityInterceptEventPageResponse>>() { });
    }

    /** 请求一页资金账户安全投影。 */
    @Override
    public FundAccountPageResponse loadFundAccountPage(FundAccountPageRequest request) {
        return post(FUND_ACCOUNT_PATH, request,
                new TypeReference<CommonResult<FundAccountPageResponse>>() { });
    }

    /** 请求一页指定账户或全局余额流水安全投影。 */
    @Override
    public FundLedgerPageResponse loadFundLedgerPage(FundLedgerPageRequest request) {
        return post(FUND_LEDGER_PATH, request,
                new TypeReference<CommonResult<FundLedgerPageResponse>>() { });
    }

    /** 请求一页充值申请安全投影。 */
    @Override
    public FundRechargePageResponse loadFundRechargePage(FundRechargePageRequest request) {
        return post(FUND_RECHARGE_PATH, request,
                new TypeReference<CommonResult<FundRechargePageResponse>>() { });
    }

    /** 请求一页扣减申请安全投影。 */
    @Override
    public FundDeductionPageResponse loadFundDeductionPage(FundDeductionPageRequest request) {
        return post(FUND_DEDUCTION_PATH, request,
                new TypeReference<CommonResult<FundDeductionPageResponse>>() { });
    }

    /** 请求一页原始汇率事实投影。 */
    @Override
    public RawRatePageResponse loadRawRatePage(RawRatePageRequest request) {
        return post(EXCHANGE_RAW_RATE_PATH, request,
                new TypeReference<CommonResult<RawRatePageResponse>>() { });
    }

    /** 请求一页业务汇率事实投影。 */
    @Override
    public BusinessRatePageResponse loadBusinessRatePage(BusinessRatePageRequest request) {
        return post(EXCHANGE_BUSINESS_RATE_PATH, request,
                new TypeReference<CommonResult<BusinessRatePageResponse>>() { });
    }

    /** 请求一页汇率使用快照事实投影。 */
    @Override
    public UsageSnapshotPageResponse loadUsageSnapshotPage(UsageSnapshotPageRequest request) {
        return post(EXCHANGE_USAGE_SNAPSHOT_PATH, request,
                new TypeReference<CommonResult<UsageSnapshotPageResponse>>() { });
    }

    /** 请求一页汇率源配置投影。 */
    @Override
    public SourcePageResponse loadSourcePage(SourcePageRequest request) {
        return post(EXCHANGE_SOURCE_PATH, request,
                new TypeReference<CommonResult<SourcePageResponse>>() { });
    }

    /** 请求一页汇率规则配置投影。 */
    @Override
    public RulePageResponse loadRulePage(RulePageRequest request) {
        return post(EXCHANGE_RULE_PATH, request,
                new TypeReference<CommonResult<RulePageResponse>>() { });
    }

    /** 请求一页权威汇率波动摘要投影。 */
    @Override
    public VolatilityPageResponse loadVolatilityPage(VolatilityPageRequest request) {
        return post(EXCHANGE_VOLATILITY_PATH, request,
                new TypeReference<CommonResult<VolatilityPageResponse>>() { });
    }

    /** 请求一页汇率预警规则投影。 */
    @Override
    public AlertRulePageResponse loadAlertRulePage(AlertRulePageRequest request) {
        return post(EXCHANGE_ALERT_RULE_PATH, request,
                new TypeReference<CommonResult<AlertRulePageResponse>>() { });
    }

    /** 请求一页汇率预警事件投影。 */
    @Override
    public AlertEventPageResponse loadAlertEventPage(AlertEventPageRequest request) {
        return post(EXCHANGE_ALERT_EVENT_PATH, request,
                new TypeReference<CommonResult<AlertEventPageResponse>>() { });
    }

    /** 请求一页费用模板安全投影。 */
    @Override
    public FeeTemplatePageResponse loadFeeTemplatePage(FeePlanPageRequest request) {
        return post(FEE_TEMPLATE_PATH, request,
                new TypeReference<CommonResult<FeeTemplatePageResponse>>() { });
    }

    /** 请求一页商户费率安全投影。 */
    @Override
    public MerchantFeePageResponse loadMerchantFeePage(FeePlanPageRequest request) {
        return post(MERCHANT_FEE_PATH, request,
                new TypeReference<CommonResult<MerchantFeePageResponse>>() { });
    }

    /** 请求一页费率复核安全投影。 */
    @Override
    public FeeReviewPageResponse loadFeeReviewPage(FeePlanPageRequest request) {
        return post(FEE_REVIEW_PATH, request,
                new TypeReference<CommonResult<FeeReviewPageResponse>>() { });
    }

    /** 请求一页费用试算逐项安全投影。 */
    @Override
    public FeeSimulationPageResponse loadFeeSimulationPage(FeeSimulationPageRequest request) {
        return post(FEE_SIMULATION_PATH, request,
                new TypeReference<CommonResult<FeeSimulationPageResponse>>() { });
    }

    /** 请求一页系统账户安全投影。 */
    @Override
    public SystemUserPageResponse loadSystemUserPage(SystemUserPageRequest request) {
        return post(SYSTEM_USER_PATH, request,
                new TypeReference<CommonResult<SystemUserPageResponse>>() { });
    }

    /** 请求一页已脱敏系统参数安全投影。 */
    @Override
    public SystemConfigPageResponse loadSystemConfigPage(SystemConfigPageRequest request) {
        return post(SYSTEM_CONFIG_PATH, request,
                new TypeReference<CommonResult<SystemConfigPageResponse>>() { });
    }

    /** 请求一页商户 IP 白名单安全投影。 */
    @Override
    public MerchantIpWhitelistPageResponse loadMerchantIpWhitelistPage(
            MerchantIpWhitelistPageRequest request) {
        return post(MERCHANT_IP_WHITELIST_PATH, request,
                new TypeReference<CommonResult<MerchantIpWhitelistPageResponse>>() { });
    }

    /**
     * 使用统一内部签名调用 Admin 导出来源并解包强类型响应。
     */
    private <T> T post(String path, Object request, TypeReference<CommonResult<T>> responseType) {
        properties.validate();
        URI uri = URI.create(properties.getBaseUrl().trim() + path);
        try {
            String body = choose(uri).exchange(uri, HttpMethod.POST, signed(uri, request), String.class).getBody();
            CommonResult<T> result = JsonUtils.parseObject(body, responseType);
            if (result == null || !CommonResult.isSuccess(result) || result.getData() == null) {
                throw new ServiceException(result == null ? ApiResultEnum.BAD_GATEWAY.getCode() : result.getCode(),
                        result == null ? "service-admin export source response is empty" : result.getMessage());
            }
            return result.getData();
        } catch (HttpStatusCodeException exception) {
            log.warn("管理端导出源调用被拒绝, event: DATA_EXPORT_SOURCE_REJECTED, path: {}, httpStatus: {}",
                    path, exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-admin export source rejected");
        } catch (RestClientException exception) {
            log.warn("管理端导出源调用失败, event: DATA_EXPORT_SOURCE_FAILED, path: {}, exceptionType: {}",
                    path, exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-admin export source failed");
        }
    }

    /**
     * 构造绑定方法、路径、正文摘要和调用方的内部签名实体。
     */
    private HttpEntity<String> signed(URI uri, Object request) {
        String body = JsonUtils.toJsonString(request);
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String signature = InternalServiceSignature.sign(HttpMethod.POST.name(),
                InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                timestamp, nonce, properties.getInternalCaller(),
                InternalServiceSignature.payloadSha256(body), properties.getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalServiceSignature.HEADER_CALLER, properties.getInternalCaller());
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(body, headers);
    }

    /**
     * 固定主机走直连，逻辑服务名走负载均衡客户端。
     */
    private RestTemplate choose(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-admin host is empty");
        }
        return "localhost".equalsIgnoreCase(host) || "::1".equals(host)
                || IPV4.matcher(host).matches() || host.contains(".") ? direct : loadBalanced;
    }
}
