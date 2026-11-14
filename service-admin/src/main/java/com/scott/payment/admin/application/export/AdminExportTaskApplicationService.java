package com.scott.payment.admin.application.export;

import com.scott.payment.admin.client.data.DataExportTaskInternalClient;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.CreateRequest;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.CardBinQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.CountryQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.CurrencyQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.DepartmentQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.DictDataQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.DictTypeQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.HolidayCalendarQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.MccCodeQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.PostQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.RegionCurrencyQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.ChannelMatchAbnormalQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.DownloadRequest;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.FundAccountQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.FundDeductionQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.FundLedgerQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.FundRechargeQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.ExchangeBusinessRateQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.ExchangeAlertEventQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.ExchangeAlertRuleQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.ExchangeCurrencyPair;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.ExchangeRawRateQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.ExchangeRuleQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.ExchangeSourceQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.ExchangeUsageSnapshotQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.ExchangeVolatilityQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.FeePlanQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.FeeSimulationQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SystemUserQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SystemConfigQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.MerchantIpWhitelistQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.JobRunLogQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SecurityInterceptEventQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SettlementPostingQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SettlementReviewQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.TaskRequest;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.WorkItemQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.TransactionOrderQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SettlementReserveQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SettlementTransactionQuery;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.MerchantNotificationSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.CardBinSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.CountrySubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.CurrencySubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DepartmentSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DictDataSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DictTypeSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.HolidayCalendarSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.MccCodeSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.PostSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RegionCurrencySubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ChannelMatchAbnormalSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.JobRunLogSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FundAccountSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FundDeductionSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FundLedgerSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FundRechargeSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeBusinessRateSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeAlertEventSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeAlertRuleSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeRawRateSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeRuleSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeSourceSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeUsageSnapshotSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeVolatilitySubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FeeReviewSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FeeSimulationSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FeeTemplateSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.MerchantFeeSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SystemUserSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SystemConfigSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.MerchantIpWhitelistSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RefundSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RetryRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SearchRequestDTO;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SettlementReserveSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SecurityInterceptEventSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SettlementPostingSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SettlementReviewSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SettlementTransactionSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TransactionOrderSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TransactionOperationSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.WorkItemSubmitRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchRequest;
import com.scott.payment.admin.dto.base.CardBinDTOs.CardBinQueryRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogQueryRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.util.Locale;

/**
 * 管理端异步导出应用服务，负责可信身份、数据范围冻结和 Data 内部调用编排。
 */
@Service
public class AdminExportTaskApplicationService {
    private static final String OPERATIONAL_WORK_ITEM = "ADMIN_OPERATIONAL_WORK_ITEM";
    private static final String TRANSACTION_ORDER = "ADMIN_TRANSACTION_ORDER";
    private static final String TRANSACTION_OPERATION = "ADMIN_TRANSACTION_OPERATION";
    private static final String MERCHANT_NOTIFICATION = "ADMIN_MERCHANT_NOTIFICATION";
    private static final String REFUND = "ADMIN_REFUND";
    private static final String SETTLEMENT_TRANSACTION = "ADMIN_SETTLEMENT_TRANSACTION";
    private static final String SETTLEMENT_RESERVE = "ADMIN_SETTLEMENT_RESERVE";
    private static final String CARD_BIN = "ADMIN_CARD_BIN";
    private static final String COUNTRY = "ADMIN_COUNTRY";
    private static final String CURRENCY = "ADMIN_CURRENCY";
    private static final String REGION_CURRENCY = "ADMIN_REGION_CURRENCY";
    private static final String MCC_CODE = "ADMIN_MCC_CODE";
    private static final String DEPARTMENT = "ADMIN_DEPARTMENT";
    private static final String POST = "ADMIN_POST";
    private static final String DICT_TYPE = "ADMIN_DICT_TYPE";
    private static final String DICT_DATA = "ADMIN_DICT_DATA";
    private static final String HOLIDAY_CALENDAR = "ADMIN_HOLIDAY_CALENDAR";
    private static final String JOB_RUN_LOG = "ADMIN_JOB_RUN_LOG";
    private static final String CHANNEL_MATCH_ABNORMAL = "ADMIN_CHANNEL_MATCH_ABNORMAL";
    private static final String SETTLEMENT_REVIEW = "ADMIN_SETTLEMENT_REVIEW";
    private static final String SETTLEMENT_POSTING = "ADMIN_SETTLEMENT_POSTING";
    private static final String SECURITY_INTERCEPT_EVENT = "ADMIN_SECURITY_INTERCEPT_EVENT";
    private static final String FUND_ACCOUNT = "ADMIN_FUND_ACCOUNT";
    private static final String FUND_LEDGER = "ADMIN_FUND_LEDGER";
    private static final String FUND_RECHARGE = "ADMIN_FUND_RECHARGE";
    private static final String FUND_DEDUCTION = "ADMIN_FUND_DEDUCTION";
    private static final String EXCHANGE_RAW_RATE = "ADMIN_EXCHANGE_RAW_RATE";
    private static final String EXCHANGE_BUSINESS_RATE = "ADMIN_EXCHANGE_BUSINESS_RATE";
    private static final String EXCHANGE_USAGE_SNAPSHOT = "ADMIN_EXCHANGE_USAGE_SNAPSHOT";
    private static final String EXCHANGE_SOURCE = "ADMIN_EXCHANGE_SOURCE";
    private static final String EXCHANGE_RULE = "ADMIN_EXCHANGE_RULE";
    private static final String EXCHANGE_VOLATILITY = "ADMIN_EXCHANGE_VOLATILITY";
    private static final String EXCHANGE_ALERT_RULE = "ADMIN_EXCHANGE_ALERT_RULE";
    private static final String EXCHANGE_ALERT_EVENT = "ADMIN_EXCHANGE_ALERT_EVENT";
    private static final String FEE_TEMPLATE = "ADMIN_FEE_TEMPLATE";
    private static final String MERCHANT_FEE = "ADMIN_MERCHANT_FEE";
    private static final String FEE_REVIEW = "ADMIN_FEE_REVIEW";
    private static final String FEE_SIMULATION = "ADMIN_FEE_SIMULATION";
    private static final String SYSTEM_USER = "ADMIN_SYSTEM_USER";
    private static final String SYSTEM_CONFIG = "ADMIN_SYSTEM_CONFIG";
    private static final String MERCHANT_IP_WHITELIST = "ADMIN_MERCHANT_IP_WHITELIST";
    private static final String RISK_CONFIG = "ADMIN_RISK_CONFIG";
    private static final String TRADE_BLACK_RECORD = "ADMIN_TRADE_BLACK_RECORD";
    private static final String DATASOURCE_SNAPSHOT = "ADMIN_DATASOURCE_SNAPSHOT";
    private final DataExportTaskInternalClient dataClient;
    private final AdminMerchantDataScopeResolver dataScopeResolver;

    /**
     * 创建管理端异步导出用例编排服务。
     */
    public AdminExportTaskApplicationService(DataExportTaskInternalClient dataClient,
                                             AdminMerchantDataScopeResolver dataScopeResolver) {
        this.dataClient = dataClient;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 冻结当前账号、商户范围和待办查询后幂等创建异步任务。
     */
    public TaskSummary submitOperationalWorkItems(WorkItemSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), OPERATIONAL_WORK_ITEM, account, scope);
        command.setWorkItemQuery(toWorkItemQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前账号、商户范围和交易主单查询后幂等创建异步任务。
     */
    public TaskSummary submitTransactionOrders(TransactionOrderSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), TRANSACTION_ORDER, account, scope);
        command.setTransactionOrderQuery(toTransactionOrderQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前账号、商户范围和交易操作查询后幂等创建异步任务。
     */
    public TaskSummary submitTransactionOperations(TransactionOperationSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), TRANSACTION_OPERATION, account, scope);
        command.setTransactionOperationQuery(toTransactionOrderQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前账号、商户范围和商户通知查询后幂等创建异步任务。
     */
    public TaskSummary submitMerchantNotifications(MerchantNotificationSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), MERCHANT_NOTIFICATION, account, scope);
        command.setMerchantNotificationQuery(toMerchantNotificationQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前账号、商户范围和退款查询后幂等创建异步任务。
     */
    public TaskSummary submitRefunds(RefundSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), REFUND, account, scope);
        command.setRefundQuery(toRefundQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前账号、商户范围和结算交易明细查询后幂等创建异步任务。
     */
    public TaskSummary submitSettlementTransactions(SettlementTransactionSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(
                request.getRequestId(), SETTLEMENT_TRANSACTION, account, scope);
        command.setSettlementTransactionQuery(toSettlementTransactionQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前账号、商户范围和保证金结算明细查询后幂等创建异步任务。
     */
    public TaskSummary submitSettlementReserves(SettlementReserveSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), SETTLEMENT_RESERVE, account, scope);
        command.setSettlementReserveQuery(toSettlementReserveQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前账号、语言、数据范围和卡 BIN 查询后幂等创建异步任务。
     */
    public TaskSummary submitCardBins(CardBinSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), CARD_BIN, account, scope);
        command.setCardBinQuery(toCardBinQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结国家和地区列表筛选条件并幂等创建异步导出任务。
     *
     * @param request 浏览器提交的幂等键和公开筛选条件
     * @return 已创建或幂等命中的任务摘要
     */
    public TaskSummary submitCountries(CountrySubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), COUNTRY, account, scope);
        command.setCountryQuery(toCountryQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结币种列表筛选条件并幂等创建异步导出任务。
     *
     * @param request 浏览器提交的幂等键和公开筛选条件
     * @return 已创建或幂等命中的任务摘要
     */
    public TaskSummary submitCurrencies(CurrencySubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), CURRENCY, account, scope);
        command.setCurrencyQuery(toCurrencyQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结国家默认币种关系筛选条件并幂等创建异步导出任务。
     *
     * @param request 浏览器提交的幂等键和公开筛选条件
     * @return 已创建或幂等命中的任务摘要
     */
    public TaskSummary submitRegionCurrencies(RegionCurrencySubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), REGION_CURRENCY, account, scope);
        command.setRegionCurrencyQuery(toRegionCurrencyQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结 MCC 编码树筛选条件并幂等创建异步导出任务。
     *
     * @param request 浏览器提交的幂等键和树查询条件
     * @return 已创建或幂等命中的任务摘要
     */
    public TaskSummary submitMccCodes(MccCodeSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), MCC_CODE, account, scope);
        command.setMccCodeQuery(toMccCodeQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结部门导出空查询契约，并使用浏览器请求键幂等创建异步任务。
     */
    public TaskSummary submitDepartments(DepartmentSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), DEPARTMENT, account, scope);
        command.setDepartmentQuery(new DepartmentQuery());
        return dataClient.submit(command);
    }

    /**
     * 冻结岗位列表公开筛选条件，并使用浏览器请求键幂等创建异步任务。
     */
    public TaskSummary submitPosts(PostSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), POST, account, scope);
        command.setPostQuery(toPostQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结字典类型列表公开筛选条件，并使用浏览器请求键幂等创建异步任务。
     */
    public TaskSummary submitDictTypes(DictTypeSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), DICT_TYPE, account, scope);
        command.setDictTypeQuery(toDictTypeQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结字典数据列表公开筛选条件，并使用浏览器请求键幂等创建异步任务。
     */
    public TaskSummary submitDictData(DictDataSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), DICT_DATA, account, scope);
        command.setDictDataQuery(toDictDataQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结节假日日历自然年，并使用浏览器请求键幂等创建异步任务。
     */
    public TaskSummary submitHolidayCalendar(HolidayCalendarSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), HOLIDAY_CALENDAR, account, scope);
        HolidayCalendarQuery query = new HolidayCalendarQuery();
        query.setYear(request.getQuery().getYear());
        command.setHolidayCalendarQuery(query);
        return dataClient.submit(command);
    }

    /**
     * 冻结当前账号、语言、数据范围和运行日志查询后幂等创建异步任务。
     */
    public TaskSummary submitJobRunLogs(JobRunLogSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), JOB_RUN_LOG, account, scope);
        command.setJobRunLogQuery(toJobRunLogQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结受控风控功能身份和公开筛选条件后创建异步导出任务。
     *
     * @param request 已完成动态功能权限校验的提交命令
     * @return 已创建或幂等命中的任务摘要
     */
    public TaskSummary submitRiskConfig(
            com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RiskConfigSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), RISK_CONFIG, account, scope);
        command.setRiskConfigQuery(toRiskConfigQuery(request));
        return dataClient.submit(command);
    }

    /** 冻结系统交易加黑筛选条件后创建异步导出任务。 */
    public TaskSummary submitTradeBlack(
            com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TradeBlackSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), TRADE_BLACK_RECORD, account, scope);
        command.setRiskConfigQuery(toTradeBlackQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 创建数据源运行快照异步导出任务，查询快照不接受任何浏览器连接参数。
     *
     * @param request 仅包含幂等键的提交命令
     * @return 已创建或幂等命中的任务摘要
     */
    public TaskSummary submitDatasourceSnapshot(
            com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DatasourceSnapshotSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), DATASOURCE_SNAPSHOT, account, scope);
        command.setDatasourceSnapshotQuery(
                new com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.DatasourceSnapshotQuery());
        return dataClient.submit(command);
    }

    /** 冻结渠道勾兑异常查询和当前账号商户范围后创建异步任务。 */
    public TaskSummary submitChannelMatchAbnormalities(ChannelMatchAbnormalSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), CHANNEL_MATCH_ABNORMAL, account, scope);
        command.setChannelMatchAbnormalQuery(toChannelMatchAbnormalQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结结算审核单查询和当前账号商户范围后创建异步任务。 */
    public TaskSummary submitSettlementReviews(SettlementReviewSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), SETTLEMENT_REVIEW, account, scope);
        command.setSettlementReviewQuery(toSettlementReviewQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结结算入账流水查询和当前账号商户范围后创建异步任务。 */
    public TaskSummary submitSettlementPostings(SettlementPostingSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), SETTLEMENT_POSTING, account, scope);
        command.setSettlementPostingQuery(toSettlementPostingQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结安全拦截事件查询和当前账号商户范围后创建异步任务。 */
    public TaskSummary submitSecurityInterceptEvents(SecurityInterceptEventSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), SECURITY_INTERCEPT_EVENT, account, scope);
        command.setSecurityInterceptEventQuery(toSecurityInterceptEventQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结资金账户查询和当前账号商户范围后创建异步任务。 */
    public TaskSummary submitFundAccounts(FundAccountSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), FUND_ACCOUNT, account, scope);
        command.setFundAccountQuery(toFundAccountQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结余额流水查询、可选账户主键和当前账号商户范围后创建异步任务。 */
    public TaskSummary submitFundLedgers(FundLedgerSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), FUND_LEDGER, account, scope);
        command.setFundLedgerQuery(toFundLedgerQuery(request.getAccountId(), request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结充值申请查询和当前账号商户范围后创建异步任务。 */
    public TaskSummary submitFundRecharges(FundRechargeSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), FUND_RECHARGE, account, scope);
        command.setFundRechargeQuery(toFundRechargeQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结扣减申请查询和当前账号商户范围后创建异步任务。 */
    public TaskSummary submitFundDeductions(FundDeductionSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), FUND_DEDUCTION, account, scope);
        command.setFundDeductionQuery(toFundDeductionQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结原始汇率查询和可信操作人后创建异步任务。 */
    public TaskSummary submitExchangeRawRates(ExchangeRawRateSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), EXCHANGE_RAW_RATE, account, scope);
        command.setExchangeRawRateQuery(toExchangeRawRateQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结业务汇率查询和可信操作人后创建异步任务。 */
    public TaskSummary submitExchangeBusinessRates(ExchangeBusinessRateSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), EXCHANGE_BUSINESS_RATE, account, scope);
        command.setExchangeBusinessRateQuery(toExchangeBusinessRateQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结汇率使用快照查询和可信操作人后创建异步任务。 */
    public TaskSummary submitExchangeUsageSnapshots(ExchangeUsageSnapshotSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), EXCHANGE_USAGE_SNAPSHOT, account, scope);
        command.setExchangeUsageSnapshotQuery(toExchangeUsageSnapshotQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结汇率源查询和可信操作人后创建异步任务。 */
    public TaskSummary submitExchangeSources(ExchangeSourceSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), EXCHANGE_SOURCE, account, scope);
        command.setExchangeSourceQuery(toExchangeSourceQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结汇率规则查询和可信操作人后创建异步任务。 */
    public TaskSummary submitExchangeRules(ExchangeRuleSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), EXCHANGE_RULE, account, scope);
        command.setExchangeRuleQuery(toExchangeRuleQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结汇率波动查询和可信操作人后创建异步任务。 */
    public TaskSummary submitExchangeVolatility(ExchangeVolatilitySubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), EXCHANGE_VOLATILITY, account, scope);
        command.setExchangeVolatilityQuery(toExchangeVolatilityQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结汇率预警规则查询和可信操作人后创建异步任务。 */
    public TaskSummary submitExchangeAlertRules(ExchangeAlertRuleSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), EXCHANGE_ALERT_RULE, account, scope);
        command.setExchangeAlertRuleQuery(toExchangeAlertRuleQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结汇率预警事件查询和可信操作人后创建异步任务。 */
    public TaskSummary submitExchangeAlertEvents(ExchangeAlertEventSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), EXCHANGE_ALERT_EVENT, account, scope);
        command.setExchangeAlertEventQuery(toExchangeAlertEventQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结费用模板筛选条件和可信操作人后创建异步任务。 */
    public TaskSummary submitFeeTemplates(FeeTemplateSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), FEE_TEMPLATE, account, scope);
        command.setFeeTemplateQuery(toFeePlanQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结商户费率筛选条件和商户范围后创建异步任务。 */
    public TaskSummary submitMerchantFees(MerchantFeeSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), MERCHANT_FEE, account, scope);
        command.setMerchantFeeQuery(toFeePlanQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结费率复核筛选条件和商户范围后创建异步任务。 */
    public TaskSummary submitFeeReviews(FeeReviewSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), FEE_REVIEW, account, scope);
        command.setFeeReviewQuery(toFeePlanQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结费用试算记录筛选条件和商户范围后创建异步任务。 */
    public TaskSummary submitFeeSimulations(FeeSimulationSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), FEE_SIMULATION, account, scope);
        command.setFeeSimulationQuery(toFeeSimulationQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结系统账户公开筛选条件和可信操作人后创建异步任务。 */
    public TaskSummary submitSystemUsers(SystemUserSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), SYSTEM_USER, account, scope);
        command.setSystemUserQuery(toSystemUserQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结系统参数公开筛选条件和可信操作人后创建异步任务。 */
    public TaskSummary submitSystemConfigs(SystemConfigSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), SYSTEM_CONFIG, account, scope);
        command.setSystemConfigQuery(toSystemConfigQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /** 冻结商户 IP 白名单筛选条件和账号商户范围后创建异步任务。 */
    public TaskSummary submitMerchantIpWhitelists(MerchantIpWhitelistSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), MERCHANT_IP_WHITELIST, account, scope);
        command.setMerchantIpWhitelistQuery(toMerchantIpWhitelistQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 查询当前账号自己的导出任务，服务端覆盖任何调用方账号字段。
     */
    public PageResult<TaskSummary> search(SearchRequestDTO request) {
        InternalAuthAccount account = currentAccount();
        SearchRequestDTO safeRequest = request == null ? new SearchRequestDTO() : request;
        com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SearchRequestDTO command =
                new com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SearchRequestDTO();
        command.setCreatorAccountId(account.getAccountId());
        command.setTaskType(safeRequest.getTaskType());
        command.setStatus(safeRequest.getStatus());
        command.setCreatedStartTime(safeRequest.getCreatedStartTime());
        command.setCreatedEndTime(safeRequest.getCreatedEndTime());
        command.setPageNo(safeRequest.getPageNo());
        command.setPageSize(safeRequest.getPageSize());
        return dataClient.search(command);
    }

    /**
     * 查询当前账号拥有的单任务详情。
     */
    public TaskSummary detail(String taskNo) {
        return dataClient.detail(taskRequest(taskNo, null));
    }

    /**
     * 使用当前账号和预期版本人工重试 FAILED 任务。
     */
    public TaskSummary retry(String taskNo, RetryRequest request) {
        return dataClient.retry(taskRequest(taskNo, request.getExpectedVersion()));
    }

    /**
     * 通过 Data 代理私有对象流并把当前操作人与网络来源写入下载审计。
     */
    public void download(String taskNo,
                         HttpServletRequest servletRequest,
                         HttpServletResponse servletResponse) {
        InternalAuthAccount account = currentAccount();
        DownloadRequest command = new DownloadRequest();
        command.setTaskNo(requireTaskNo(taskNo));
        command.setCreatorAccountId(account.getAccountId());
        command.setOperatorName(operatorName(account));
        command.setClientIp(servletRequest == null ? null : servletRequest.getRemoteAddr());
        dataClient.download(command, servletResponse);
    }

    /**
     * 构造归属当前账号的内部任务请求。
     */
    private TaskRequest taskRequest(String taskNo, Long expectedVersion) {
        TaskRequest command = new TaskRequest();
        command.setTaskNo(requireTaskNo(taskNo));
        command.setCreatorAccountId(currentAccount().getAccountId());
        command.setExpectedVersion(expectedVersion);
        return command;
    }

    /**
     * 复制允许冻结的待办筛选字段，不保存列表当前页码和每页条数。
     */
    private WorkItemQuery toWorkItemQuery(SearchRequest source) {
        WorkItemQuery target = new WorkItemQuery();
        target.setWorkItemNo(source.getWorkItemNo());
        target.setKeyword(source.getKeyword());
        target.setSourceType(source.getSourceType());
        target.setWorkStatus(source.getWorkStatus());
        target.setPriority(source.getPriority());
        target.setOwnerAccountId(source.getOwnerAccountId());
        target.setMineOnly(source.getMineOnly());
        target.setUnassignedOnly(source.getUnassignedOnly());
        target.setOverdueOnly(source.getOverdueOnly());
        target.setDetectedStartTime(source.getDetectedStartTime());
        target.setDetectedEndTime(source.getDetectedEndTime());
        return target;
    }

    /**
     * 创建包含可信操作人、语言、时区和商户范围的 Data 内部命令。
     */
    private CreateRequest createRequest(String requestId,
                                        String taskType,
                                        InternalAuthAccount account,
                                        AdminMerchantDataScope scope) {
        CreateRequest command = new CreateRequest();
        command.setRequestId(requestId.trim());
        command.setTaskType(taskType);
        command.setTaskName(taskName(taskType, LocaleContextHolder.getLocale()));
        command.setCreatorAccountId(account.getAccountId());
        command.setCreatorAccountName(operatorName(account));
        command.setLocale(LocaleContextHolder.getLocale().toLanguageTag());
        command.setTimezone(ZoneId.systemDefault().getId());
        command.setAllMerchants(scope.allMerchants());
        command.setMerchantIds(scope.merchantIds());
        return command;
    }

    /** 复制风控导出允许冻结的功能身份和筛选字段，剔除页面分页参数。 */
    private com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.RiskConfigQuery toRiskConfigQuery(
            com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RiskConfigSubmitRequest source) {
        var target = new com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.RiskConfigQuery();
        var query = source.getQuery();
        target.setModuleType(source.getModuleType().trim().toUpperCase(Locale.ROOT));
        target.setFunctionCode(source.getFunctionCode().trim());
        target.setMerchantScope(query.getMerchantScope());
        target.setMerchantId(query.getMerchantId());
        target.setRuleName(query.getRuleName());
        target.setMatchValue(query.getMatchValue());
        target.setCountryAlpha2(query.getCountryAlpha2());
        target.setSourceUrl(query.getSourceUrl());
        target.setSourceHost(query.getSourceHost());
        target.setLimitType(query.getLimitType());
        target.setRuleType(query.getRuleType());
        target.setChannelCode(query.getChannelCode());
        target.setPaymentMethod(query.getPaymentMethod());
        target.setCardBrand(query.getCardBrand());
        target.setCurrency(query.getCurrency());
        target.setTriggerAction(query.getTriggerAction());
        target.setStatus(query.getStatus());
        target.setApprovalStatus(query.getApprovalStatus());
        target.setSubmitSource(query.getSubmitSource());
        return target;
    }

    /** 将交易加黑页面筛选条件冻结到通用风控导出快照。 */
    private com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.RiskConfigQuery toTradeBlackQuery(
            com.scott.payment.admin.dto.risk.RiskDTOs.TradeBlackQueryRequest source) {
        var target = new com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.RiskConfigQuery();
        target.setModuleType("TRADE_BLACK");
        target.setFunctionCode("system");
        target.setMerchantId(source.getMerchantId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setPaymentOrderNo(source.getPaymentOrderNo());
        target.setBlackTargetType(source.getBlackTargetType());
        target.setStatus(source.getStatus());
        target.setOperator(source.getOperator());
        target.setOperationStartTime(source.getOperationStartTime());
        target.setOperationEndTimeExclusive(source.getOperationEndTimeExclusive());
        return target;
    }

    /**
     * 复制交易主单允许冻结的筛选字段并剔除分页参数。
     */
    private TransactionOrderQuery toTransactionOrderQuery(TransactionPageQuery source) {
        TransactionOrderQuery target = new TransactionOrderQuery();
        target.setMerchantId(source.getMerchantId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setTransactionId(source.getTransactionId());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setTransactionType(source.getTransactionType());
        target.setTransactionStatus(source.getTransactionStatus());
        target.setChannelCode(source.getChannelCode());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setPaymentBrand(source.getPaymentBrand());
        target.setCardBin(source.getCardBin());
        target.setChannelOrderNo(source.getChannelOrderNo());
        target.setMerchantResponseCode(source.getMerchantResponseCode());
        target.setChannelResponseCode(source.getChannelResponseCode());
        target.setAuthCode(source.getAuthCode());
        target.setAcquirerReferenceNo(source.getAcquirerReferenceNo());
        target.setChannelMatchStatus(source.getChannelMatchStatus());
        target.setReconciliationStatus(source.getReconciliationStatus());
        target.setSettlementStatus(source.getSettlementStatus());
        target.setBeginTime(source.getBeginTime());
        target.setEndTime(source.getEndTime());
        target.setQueryTimeZone(source.getQueryTimeZone());
        return target;
    }

    /**
     * 复制退款允许冻结的筛选字段并剔除分页参数。
     */
    private com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.RefundQuery toRefundQuery(
            RefundQuery source) {
        com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.RefundQuery target =
                new com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.RefundQuery();
        target.setMerchantId(source.getMerchantId());
        target.setRefundTransactionId(source.getRefundTransactionId());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setMerchantOperationNo(source.getMerchantOperationNo());
        target.setTransactionType(source.getTransactionType());
        target.setRefundScope(source.getRefundScope());
        target.setApprovalStatus(source.getApprovalStatus());
        target.setTransactionStatus(source.getTransactionStatus());
        target.setRequestSource(source.getRequestSource());
        target.setChannelCode(source.getChannelCode());
        target.setChannelOrderNo(source.getChannelOrderNo());
        target.setAcquirerReferenceNo(source.getAcquirerReferenceNo());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setPaymentBrand(source.getPaymentBrand());
        target.setLabelCurrency(source.getLabelCurrency());
        target.setTransactionCurrency(source.getTransactionCurrency());
        target.setMinimumTransactionAmount(source.getMinimumTransactionAmount());
        target.setMaximumTransactionAmount(source.getMaximumTransactionAmount());
        target.setApplicantId(source.getApplicantId());
        target.setBeginTime(source.getBeginTime());
        target.setEndTime(source.getEndTime());
        target.setCompleteBeginTime(source.getCompleteBeginTime());
        target.setCompleteEndTime(source.getCompleteEndTime());
        target.setQueryTimeZone(source.getQueryTimeZone());
        return target;
    }

    /**
     * 复制卡 BIN 允许冻结的筛选字段，剔除页面页码和每页条数。
     */
    private CardBinQuery toCardBinQuery(CardBinQueryRequest source) {
        CardBinQuery target = new CardBinQuery();
        target.setCardBin(source.getCardBin());
        target.setCardBrand(source.getCardBrand());
        target.setCardType(source.getCardType());
        target.setIssuerCountryAlpha2(source.getIssuerCountryAlpha2());
        target.setIssuerBank(source.getIssuerBank());
        target.setStatus(source.getStatus());
        target.setDataSource(source.getDataSource());
        return target;
    }

    /** 复制国家和地区允许冻结的筛选字段。 */
    private CountryQuery toCountryQuery(
            com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.CountryQuery source) {
        CountryQuery target = new CountryQuery();
        target.setKeyword(source.getKeyword());
        target.setContinentCode(source.getContinentCode());
        target.setStatus(source.getStatus());
        return target;
    }

    /** 复制币种允许冻结的筛选字段。 */
    private CurrencyQuery toCurrencyQuery(
            com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.CurrencyQuery source) {
        CurrencyQuery target = new CurrencyQuery();
        target.setKeyword(source.getKeyword());
        target.setStatus(source.getStatus());
        return target;
    }

    /** 复制国家默认币种关系允许冻结的筛选字段。 */
    private RegionCurrencyQuery toRegionCurrencyQuery(
            com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RegionCurrencyQuery source) {
        RegionCurrencyQuery target = new RegionCurrencyQuery();
        target.setKeyword(source.getKeyword());
        target.setContinentCode(source.getContinentCode());
        return target;
    }

    /** 复制 MCC 编码树允许冻结的全部公开筛选字段。 */
    private MccCodeQuery toMccCodeQuery(
            com.scott.payment.admin.dto.base.MccRequests.MccTreeQueryRequest source) {
        MccCodeQuery target = new MccCodeQuery();
        target.setKeyword(source.getKeyword());
        target.setMccCode(source.getMccCode());
        target.setNameCn(source.getNameCn());
        target.setNameEn(source.getNameEn());
        target.setNodeType(source.getNodeType());
        target.setRiskLevel(source.getRiskLevel());
        target.setStatus(source.getStatus());
        return target;
    }

    /**
     * 复制岗位允许冻结的公开筛选字段，明确排除浏览器分页参数。
     */
    private PostQuery toPostQuery(
            com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.PostQuery source) {
        PostQuery target = new PostQuery();
        target.setPostCode(source.getPostCode());
        target.setPostName(source.getPostName());
        target.setStatus(source.getStatus());
        return target;
    }

    /**
     * 复制字典类型允许冻结的公开筛选字段，明确排除浏览器分页参数。
     */
    private DictTypeQuery toDictTypeQuery(
            com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DictTypeQuery source) {
        DictTypeQuery target = new DictTypeQuery();
        target.setDictName(source.getDictName());
        target.setDictType(source.getDictType());
        target.setBizDomain(source.getBizDomain());
        target.setStatus(source.getStatus());
        return target;
    }

    /**
     * 复制字典数据允许冻结的公开筛选字段，明确排除浏览器分页参数。
     */
    private DictDataQuery toDictDataQuery(
            com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DictDataQuery source) {
        DictDataQuery target = new DictDataQuery();
        target.setDictType(source.getDictType());
        target.setDictLabel(source.getDictLabel());
        target.setDictValue(source.getDictValue());
        target.setParentValue(source.getParentValue());
        target.setLocale(source.getLocale());
        target.setStatus(source.getStatus());
        return target;
    }

    /**
     * 复制任务运行日志允许冻结的筛选字段，禁止保存分页和日志正文内容。
     */
    private JobRunLogQuery toJobRunLogQuery(JobRunLogQueryRequest source) {
        JobRunLogQuery target = new JobRunLogQuery();
        target.setJobId(source.getJobId());
        target.setJobCode(source.getJobCode());
        target.setRunStatus(source.getRunStatus());
        target.setTriggerType(source.getTriggerType());
        target.setExecutorNode(source.getExecutorNode());
        return target;
    }

    /** 复制渠道勾兑异常允许冻结的筛选字段并剔除分页参数。 */
    private ChannelMatchAbnormalQuery toChannelMatchAbnormalQuery(
            com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalQuery source) {
        ChannelMatchAbnormalQuery target = new ChannelMatchAbnormalQuery();
        target.setEventId(source.getEventId());
        target.setTransactionId(source.getTransactionId());
        target.setMerchantId(source.getMerchantId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setAbnormalType(source.getAbnormalType());
        target.setAbnormalLevel(source.getAbnormalLevel());
        target.setEventStatus(source.getEventStatus());
        target.setTransactionType(source.getTransactionType());
        target.setPlatformStatus(source.getPlatformStatus());
        target.setChannelCode(source.getChannelCode());
        target.setChannelOrderNo(source.getChannelOrderNo());
        target.setAssignedToId(source.getAssignedToId());
        target.setDetectSource(source.getDetectSource());
        target.setMinimumOccurrenceCount(source.getMinimumOccurrenceCount());
        target.setBeginTime(source.getBeginTime());
        target.setEndTime(source.getEndTime());
        target.setQueryTimeZone(source.getQueryTimeZone());
        return target;
    }

    /** 复制结算审核单允许冻结的筛选字段并剔除分页参数。 */
    private SettlementReviewQuery toSettlementReviewQuery(
            com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest source) {
        SettlementReviewQuery target = new SettlementReviewQuery();
        target.setReviewOrderNo(source.getReviewOrderNo());
        target.setMerchantId(source.getMerchantId());
        target.setReviewType(source.getReviewType());
        target.setReviewStatus(source.getReviewStatus());
        target.setBeginBusinessDate(source.getBeginBusinessDate());
        target.setEndBusinessDate(source.getEndBusinessDate());
        return target;
    }

    /** 复制结算入账流水允许冻结的筛选字段并剔除分页参数。 */
    private SettlementPostingQuery toSettlementPostingQuery(
            com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest source) {
        SettlementPostingQuery target = new SettlementPostingQuery();
        target.setSettlementBatchNo(source.getSettlementBatchNo());
        target.setMerchantId(source.getMerchantId());
        target.setLedgerNo(source.getLedgerNo());
        target.setDirection(source.getDirection());
        target.setOperationMode(source.getOperationMode());
        target.setCurrency(source.getCurrency());
        target.setBeginPostedTime(source.getBeginPostedTime());
        target.setEndPostedTime(source.getEndPostedTime());
        return target;
    }

    /** 复制安全拦截事件允许冻结的筛选字段并剔除分页参数。 */
    private SecurityInterceptEventQuery toSecurityInterceptEventQuery(
            com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventQuery source) {
        SecurityInterceptEventQuery target = new SecurityInterceptEventQuery();
        target.setEventNo(source.getEventNo());
        target.setSourceLayer(source.getSourceLayer());
        target.setEventType(source.getEventType());
        target.setRiskLevel(source.getRiskLevel());
        target.setAction(source.getAction());
        target.setMerchantId(source.getMerchantId());
        target.setClientIp(source.getClientIp());
        target.setRequestPath(source.getRequestPath());
        target.setTraceId(source.getTraceId());
        target.setRequestId(source.getRequestId());
        target.setHitRuleCode(source.getHitRuleCode());
        target.setProcessStatus(source.getProcessStatus());
        target.setQueryTimeZone(source.getQueryTimeZone());
        target.setBeginTime(source.getBeginTime());
        target.setEndTime(source.getEndTime());
        return target;
    }

    /** 复制资金账户允许冻结的筛选字段并剔除分页参数。 */
    private FundAccountQuery toFundAccountQuery(
            com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountQuery source) {
        FundAccountQuery target = new FundAccountQuery();
        target.setKeyword(source.getKeyword());
        target.setAccountStatus(source.getAccountStatus());
        target.setSettlementCurrency(source.getSettlementCurrency());
        return target;
    }

    /** 复制余额流水允许冻结的筛选字段，并单独冻结账户主键。 */
    private FundLedgerQuery toFundLedgerQuery(
            Long accountId,
            com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDetailQuery source) {
        FundLedgerQuery target = new FundLedgerQuery();
        target.setAccountId(accountId);
        target.setKeyword(source.getKeyword());
        target.setMerchantId(source.getMerchantId());
        target.setAccountNo(source.getAccountNo());
        target.setCurrency(source.getCurrency());
        target.setBalanceType(source.getBalanceType());
        target.setBusinessType(source.getBusinessType());
        target.setDirection(source.getDirection());
        target.setPostedStartTime(source.getPostedStartTime());
        target.setPostedEndTime(source.getPostedEndTime());
        return target;
    }

    /** 复制充值申请允许冻结的筛选字段并剔除分页参数。 */
    private FundRechargeQuery toFundRechargeQuery(
            com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeQuery source) {
        FundRechargeQuery target = new FundRechargeQuery();
        target.setKeyword(source.getKeyword());
        target.setMerchantId(source.getMerchantId());
        target.setRechargeStatus(source.getRechargeStatus());
        return target;
    }

    /** 复制扣减申请允许冻结的筛选字段并剔除分页参数。 */
    private FundDeductionQuery toFundDeductionQuery(
            com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDeductionQuery source) {
        FundDeductionQuery target = new FundDeductionQuery();
        target.setKeyword(source.getKeyword());
        target.setMerchantId(source.getMerchantId());
        target.setDeductionCategory(source.getDeductionCategory());
        target.setDeductionStatus(source.getDeductionStatus());
        return target;
    }

    /** 复制原始汇率允许冻结的筛选字段并剔除分页参数。 */
    private ExchangeRawRateQuery toExchangeRawRateQuery(
            com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateQuery source) {
        ExchangeRawRateQuery target = new ExchangeRawRateQuery();
        target.setSourceCode(source.getSourceCode());
        target.setBaseCurrency(source.getBaseCurrency());
        target.setQuoteCurrency(source.getQuoteCurrency());
        target.setRateStatus(source.getRateStatus());
        target.setCreateMethod(source.getCreateMethod());
        target.setPublishStartTime(source.getPublishStartTime());
        target.setPublishEndTime(source.getPublishEndTime());
        target.setFetchStartTime(source.getFetchStartTime());
        target.setFetchEndTime(source.getFetchEndTime());
        target.setEffectiveStartTime(source.getEffectiveStartTime());
        target.setEffectiveEndTime(source.getEffectiveEndTime());
        return target;
    }

    /** 复制业务汇率允许冻结的筛选字段并剔除分页参数。 */
    private ExchangeBusinessRateQuery toExchangeBusinessRateQuery(
            com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateQuery source) {
        ExchangeBusinessRateQuery target = new ExchangeBusinessRateQuery();
        target.setRateType(source.getRateType());
        target.setSourceCode(source.getSourceCode());
        target.setBaseCurrency(source.getBaseCurrency());
        target.setQuoteCurrency(source.getQuoteCurrency());
        target.setRateStatus(source.getRateStatus());
        target.setGenerateMethod(source.getGenerateMethod());
        target.setEffectiveStartTime(source.getEffectiveStartTime());
        target.setEffectiveEndTime(source.getEffectiveEndTime());
        target.setCreateStartTime(source.getCreateStartTime());
        target.setCreateEndTime(source.getCreateEndTime());
        return target;
    }

    /** 复制汇率使用快照允许冻结的筛选字段并剔除分页参数。 */
    private ExchangeUsageSnapshotQuery toExchangeUsageSnapshotQuery(
            com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotQuery source) {
        ExchangeUsageSnapshotQuery target = new ExchangeUsageSnapshotQuery();
        target.setRateType(source.getRateType());
        target.setUsageScene(source.getUsageScene());
        target.setBusinessType(source.getBusinessType());
        target.setBusinessNo(source.getBusinessNo());
        target.setBaseCurrency(source.getBaseCurrency());
        target.setQuoteCurrency(source.getQuoteCurrency());
        target.setAppliedStartTime(source.getAppliedStartTime());
        target.setAppliedEndTime(source.getAppliedEndTime());
        return target;
    }

    /** 复制汇率源允许冻结的筛选字段并剔除分页参数。 */
    private ExchangeSourceQuery toExchangeSourceQuery(
            com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceQuery source) {
        ExchangeSourceQuery target = new ExchangeSourceQuery();
        target.setKeyword(source.getKeyword());
        target.setSourceType(source.getSourceType());
        target.setSourceStatus(source.getSourceStatus());
        return target;
    }

    /** 复制汇率规则允许冻结的筛选字段并剔除分页参数。 */
    private ExchangeRuleQuery toExchangeRuleQuery(
            com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleQuery source) {
        ExchangeRuleQuery target = new ExchangeRuleQuery();
        target.setRateType(source.getRateType());
        target.setSourceCode(source.getSourceCode());
        target.setBaseCurrency(source.getBaseCurrency());
        target.setQuoteCurrency(source.getQuoteCurrency());
        target.setAdjustDirection(source.getAdjustDirection());
        target.setAdjustMethod(source.getAdjustMethod());
        target.setRuleStatus(source.getRuleStatus());
        return target;
    }

    /** 复制汇率波动筛选字段，保持币种对方向、顺序和时间边界不变。 */
    private ExchangeVolatilityQuery toExchangeVolatilityQuery(
            com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityQuery source) {
        ExchangeVolatilityQuery target = new ExchangeVolatilityQuery();
        target.setRateType(source.getRateType());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setCurrencyPairs(source.getCurrencyPairs() == null ? java.util.List.of()
                : source.getCurrencyPairs().stream().map(pair -> {
                    ExchangeCurrencyPair copy = new ExchangeCurrencyPair();
                    copy.setBaseCurrency(pair.getBaseCurrency());
                    copy.setQuoteCurrency(pair.getQuoteCurrency());
                    return copy;
                }).toList());
        return target;
    }

    /** 复制汇率预警规则允许冻结的筛选字段并剔除分页参数。 */
    private ExchangeAlertRuleQuery toExchangeAlertRuleQuery(
            com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleQuery source) {
        ExchangeAlertRuleQuery target = new ExchangeAlertRuleQuery();
        target.setKeyword(source.getKeyword());
        target.setRateType(source.getRateType());
        target.setBaseCurrency(source.getBaseCurrency());
        target.setQuoteCurrency(source.getQuoteCurrency());
        target.setRuleStatus(source.getRuleStatus());
        target.setSystemMandatory(source.getSystemMandatory());
        return target;
    }

    /** 复制汇率预警事件允许冻结的筛选字段并剔除分页参数。 */
    private ExchangeAlertEventQuery toExchangeAlertEventQuery(
            com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventQuery source) {
        ExchangeAlertEventQuery target = new ExchangeAlertEventQuery();
        target.setEventNo(source.getEventNo());
        target.setRateType(source.getRateType());
        target.setBaseCurrency(source.getBaseCurrency());
        target.setQuoteCurrency(source.getQuoteCurrency());
        target.setEventStatus(source.getEventStatus());
        target.setAcknowledgedBy(source.getAcknowledgedBy());
        target.setTriggerStartTime(source.getTriggerStartTime());
        target.setTriggerEndTime(source.getTriggerEndTime());
        return target;
    }

    /** 复制费用方案允许冻结的筛选字段并剔除浏览器分页参数。 */
    private FeePlanQuery toFeePlanQuery(
            com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanQuery source) {
        FeePlanQuery target = new FeePlanQuery();
        target.setKeyword(source.getKeyword());
        target.setStatus(source.getStatus());
        target.setVersionStatus(source.getVersionStatus());
        return target;
    }

    /** 复制费用试算记录允许冻结的筛选字段并剔除浏览器分页参数。 */
    private FeeSimulationQuery toFeeSimulationQuery(
            com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordQuery source) {
        FeeSimulationQuery target = new FeeSimulationQuery();
        target.setKeyword(source.getKeyword());
        target.setMerchantId(source.getMerchantId());
        target.setTransactionType(source.getTransactionType());
        return target;
    }

    /** 复制系统账户允许冻结的筛选字段并剔除浏览器分页参数。 */
    private SystemUserQuery toSystemUserQuery(
            com.scott.payment.admin.dto.SysUserAccountQueryRequest source) {
        SystemUserQuery target = new SystemUserQuery();
        target.setLoginAccount(source.getLoginAccount());
        target.setMobile(source.getMobile());
        target.setEmail(source.getEmail());
        target.setDeptId(source.getDeptId());
        target.setStatus(source.getStatus());
        target.setEmailVerificationStatus(source.getEmailVerificationStatus());
        target.setActivationStatus(source.getActivationStatus());
        return target;
    }

    /** 复制系统参数允许冻结的筛选字段并剔除浏览器分页参数。 */
    private SystemConfigQuery toSystemConfigQuery(
            com.scott.payment.admin.dto.SysConfigQueryRequest source) {
        SystemConfigQuery target = new SystemConfigQuery();
        target.setConfigName(source.getConfigName());
        target.setConfigKey(source.getConfigKey());
        target.setConfigGroup(source.getConfigGroup());
        target.setStatus(source.getStatus());
        return target;
    }

    /** 复制商户 IP 白名单公开筛选字段并剔除浏览器分页参数。 */
    private MerchantIpWhitelistQuery toMerchantIpWhitelistQuery(
            com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistQuery source) {
        MerchantIpWhitelistQuery target = new MerchantIpWhitelistQuery();
        target.setMerchantId(source.getMerchantId());
        target.setMerchantKeyword(source.getMerchantKeyword());
        target.setIpValue(source.getIpValue());
        target.setIpType(source.getIpType());
        target.setStatus(source.getStatus());
        target.setApprovalStatus(source.getApprovalStatus());
        target.setSubmitSource(source.getSubmitSource());
        return target;
    }

    /**
     * 复制商户通知允许冻结的筛选字段并剔除分页参数。
     */
    private MerchantNotificationQuery toMerchantNotificationQuery(
            com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery source) {
        MerchantNotificationQuery target = new MerchantNotificationQuery();
        target.setMerchantId(source.getMerchantId());
        target.setTransactionId(source.getTransactionId());
        target.setNotifyStatus(source.getNotifyStatus());
        target.setBeginTime(source.getBeginTime());
        target.setEndTime(source.getEndTime());
        target.setQueryTimeZone(source.getQueryTimeZone());
        return target;
    }

    /**
     * 复制结算交易明细允许冻结的筛选字段并剔除分页参数。
     */
    private SettlementTransactionQuery toSettlementTransactionQuery(ResultItemSearchRequest source) {
        SettlementTransactionQuery target = new SettlementTransactionQuery();
        target.setSettlementBatchNo(source.getSettlementBatchNo());
        target.setMerchantId(source.getMerchantId());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setBeginTransactionTime(source.getBeginTransactionTime());
        target.setEndTransactionTime(source.getEndTransactionTime());
        target.setResultItemType(source.getResultItemType());
        target.setResultRole(source.getResultRole());
        target.setDirection(source.getDirection());
        target.setTargetCurrency(source.getTargetCurrency());
        target.setSourceDetailType(source.getSourceDetailType());
        target.setBeginBusinessDate(source.getBeginBusinessDate());
        target.setEndBusinessDate(source.getEndBusinessDate());
        return target;
    }

    /**
     * 复制保证金结算明细允许冻结的筛选字段并剔除分页参数。
     */
    private SettlementReserveQuery toSettlementReserveQuery(ReserveItemSearchRequest source) {
        SettlementReserveQuery target = new SettlementReserveQuery();
        target.setSettlementBatchNo(source.getSettlementBatchNo());
        target.setMerchantId(source.getMerchantId());
        target.setReserveNo(source.getReserveNo());
        target.setReserveActionNo(source.getReserveActionNo());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setReserveStatus(source.getReserveStatus());
        target.setActionType(source.getActionType());
        target.setCurrency(source.getCurrency());
        target.setBeginTransactionTime(source.getBeginTransactionTime());
        target.setEndTransactionTime(source.getEndTransactionTime());
        target.setBeginExpectedReleaseDate(source.getBeginExpectedReleaseDate());
        target.setEndExpectedReleaseDate(source.getEndExpectedReleaseDate());
        target.setBeginBusinessDate(source.getBeginBusinessDate());
        target.setEndBusinessDate(source.getEndBusinessDate());
        return target;
    }

    /**
     * 从线程认证上下文获取 ADMIN 账号，内部调用不能伪造浏览器身份。
     */
    private InternalAuthAccount currentAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null || account.getAppId() == null
                || !"ADMIN".equalsIgnoreCase(account.getAppCode())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return account;
    }

    /**
     * 选择可审计的操作人名称，真实姓名为空时回退登录账号。
     */
    private String operatorName(InternalAuthAccount account) {
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName().trim();
        }
        return StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount().trim() : "admin";
    }

    /**
     * 校验任务号后返回去除首尾空白的稳定值。
     */
    private String requireTaskNo(String taskNo) {
        if (!StringUtils.hasText(taskNo) || taskNo.length() > 64) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return taskNo.trim();
    }

    /**
     * 根据当前管理端语言生成用户可识别的任务名称。
     */
    private String taskName(String taskType, Locale locale) {
        boolean chinese = Locale.CHINESE.getLanguage().equals(locale.getLanguage());
        return switch (taskType) {
            case TRANSACTION_ORDER -> chinese ? "交易主单导出" : "Transaction Order Export";
            case TRANSACTION_OPERATION -> chinese ? "交易操作流水导出" : "Transaction Operation Export";
            case MERCHANT_NOTIFICATION -> chinese ? "商户通知记录导出" : "Merchant Notification Export";
            case REFUND -> chinese ? "退款管理导出" : "Refund Management Export";
            case SETTLEMENT_TRANSACTION -> chinese ? "结算交易明细导出" : "Settlement Transaction Export";
            case SETTLEMENT_RESERVE -> chinese ? "保证金结算明细导出" : "Settlement Reserve Export";
            case CARD_BIN -> chinese ? "卡 BIN 库导出" : "Card BIN Export";
            case COUNTRY -> chinese ? "国家和地区导出" : "Country and Region Export";
            case CURRENCY -> chinese ? "币种导出" : "Currency Export";
            case REGION_CURRENCY -> chinese ? "国家默认币种关系导出" : "Region Currency Export";
            case MCC_CODE -> chinese ? "MCC 编码导出" : "MCC Code Export";
            case DEPARTMENT -> chinese ? "部门导出" : "Department Export";
            case POST -> chinese ? "岗位导出" : "Post Export";
            case DICT_TYPE -> chinese ? "字典类型导出" : "Dictionary Type Export";
            case DICT_DATA -> chinese ? "字典数据导出" : "Dictionary Data Export";
            case HOLIDAY_CALENDAR -> chinese ? "节假日日历导出" : "Holiday Calendar Export";
            case JOB_RUN_LOG -> chinese ? "任务运行日志导出" : "Job Run Log Export";
            case CHANNEL_MATCH_ABNORMAL -> chinese ? "渠道勾兑异常导出" : "Channel Match Abnormality Export";
            case SETTLEMENT_REVIEW -> chinese ? "结算审核单导出" : "Settlement Review Export";
            case SETTLEMENT_POSTING -> chinese ? "结算入账流水导出" : "Settlement Posting Export";
            case SECURITY_INTERCEPT_EVENT -> chinese ? "安全拦截事件导出" : "Security Intercept Event Export";
            case FUND_ACCOUNT -> chinese ? "资金账户导出" : "Fund Account Export";
            case FUND_LEDGER -> chinese ? "余额流水导出" : "Fund Ledger Export";
            case FUND_RECHARGE -> chinese ? "充值申请导出" : "Fund Recharge Export";
            case FUND_DEDUCTION -> chinese ? "扣减申请导出" : "Fund Deduction Export";
            case EXCHANGE_RAW_RATE -> chinese ? "原始汇率导出" : "Raw Exchange Rate Export";
            case EXCHANGE_BUSINESS_RATE -> chinese ? "业务汇率导出" : "Business Exchange Rate Export";
            case EXCHANGE_USAGE_SNAPSHOT -> chinese ? "汇率使用快照导出" : "Exchange Rate Usage Snapshot Export";
            case EXCHANGE_SOURCE -> chinese ? "汇率源导出" : "Exchange Rate Source Export";
            case EXCHANGE_RULE -> chinese ? "汇率规则导出" : "Exchange Rate Rule Export";
            case EXCHANGE_VOLATILITY -> chinese ? "汇率波动导出" : "Exchange Rate Volatility Export";
            case EXCHANGE_ALERT_RULE -> chinese ? "汇率预警规则导出" : "Exchange Rate Alert Rule Export";
            case EXCHANGE_ALERT_EVENT -> chinese ? "汇率预警事件导出" : "Exchange Rate Alert Event Export";
            case FEE_TEMPLATE -> chinese ? "费用模板导出" : "Fee Template Export";
            case MERCHANT_FEE -> chinese ? "商户费率导出" : "Merchant Fee Export";
            case FEE_REVIEW -> chinese ? "费率审核导出" : "Fee Review Export";
            case FEE_SIMULATION -> chinese ? "费用试算记录导出" : "Fee Simulation Export";
            case SYSTEM_USER -> chinese ? "系统账户导出" : "System User Export";
            case SYSTEM_CONFIG -> chinese ? "系统参数导出" : "System Configuration Export";
            case MERCHANT_IP_WHITELIST -> chinese ? "商户 IP 白名单导出" : "Merchant IP Whitelist Export";
            case RISK_CONFIG -> chinese ? "风控配置导出" : "Risk Configuration Export";
            case TRADE_BLACK_RECORD -> chinese ? "系统交易加黑记录导出" : "Transaction Blacklist Record Export";
            case DATASOURCE_SNAPSHOT -> chinese ? "数据源监控快照导出" : "Datasource Monitor Snapshot Export";
            default -> chinese ? "统一运营待办导出" : "Operational Work Item Export";
        };
    }
}
