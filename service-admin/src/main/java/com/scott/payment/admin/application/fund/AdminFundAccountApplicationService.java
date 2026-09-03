package com.scott.payment.admin.application.fund;

import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountStatusRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDetailQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDeductionCreateRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDeductionQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDeductionResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundLedgerResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeCreateRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeResponse;
import com.scott.payment.admin.dto.export.FundAccountExportRow;
import com.scott.payment.admin.dto.export.FundDeductionExportRow;
import com.scott.payment.admin.dto.export.FundLedgerExportRow;
import com.scott.payment.admin.dto.export.FundRechargeExportRow;
import com.scott.payment.admin.service.AdminFundAccountService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFundAccountApplicationService
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端资金账户、余额明细导出、充值和扣减审批应用服务。
 * @status : create
 */
@Service
public class AdminFundAccountApplicationService {

    /**
     * {@code EXPORT_PAGE_SIZE}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int EXPORT_PAGE_SIZE = 200;
    /**
     * {@code EXPORT_TIME_FORMATTER}常量，统一 {@code AdminFundAccountApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminFundAccountService accountService;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 构造资金账户应用服务。
     *
     * @param accountService 资金账户、充值和扣减审批领域服务
     * @param excelExportService 分页 Excel 导出服务
     * @param excelI18nMessageResolver Excel 国际化标题解析器
     * @param excelLocaleResolver 当前请求语言解析器
     */
    public AdminFundAccountApplicationService(AdminFundAccountService accountService,
                                              ExcelExportService excelExportService,
                                              ExcelI18nMessageResolver excelI18nMessageResolver,
                                              ExcelLocaleResolver excelLocaleResolver) {
        this.accountService = accountService;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 分页查询账户基础信息，列表不触发派生余额统计。
     *
     * @param query 商户、状态、结算币种和分页条件
     * @return 资金账户分页结果
     */
    public PageResult<FundAccountResponse> pageAccounts(FundAccountQuery query) {
        return accountService.pageAccounts(query);
    }

    /**
     * 按当前筛选条件分页导出商户资金账户。
     *
     * @param request 账户筛选条件
     * @param response Excel 文件响应，写入后不再返回 JSON
     */
    public void exportAccounts(FundAccountQuery request, HttpServletResponse response) {
        FundAccountQuery query = request == null ? new FundAccountQuery() : request;
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        String titleKey = "excel.fund.title";
        String title = excelI18nMessageResolver.resolve(titleKey, locale);
        LocalDateTime now = LocalDateTime.now();
        excelExportService.exportPaged(ExcelPagedExportRequest.<FundAccountExportRow>builder()
                .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                .sheetName(title)
                .titleKey(titleKey)
                .operator(currentOperatorName())
                .exportTime(now)
                .locale(locale)
                .querySummary("keyword=" + query.getKeyword() + ", accountStatus=" + query.getAccountStatus()
                        + ", settlementCurrency=" + query.getSettlementCurrency())
                .rowClass(FundAccountExportRow.class)
                .pageSize(EXPORT_PAGE_SIZE)
                .pageLoader(pageNo -> {
                    query.setPageNo(pageNo);
                    query.setPageSize(EXPORT_PAGE_SIZE);
                    return accountService.pageAccounts(query).getRecords().stream().map(this::toExportRow).toList();
                })
                .build(), response);
    }

    /**
     * 查询账户详情和实时派生余额。
     *
     * @param id 资金账户主键
     * @return 账户详情和能力快照
     */
    public FundAccountResponse getAccount(Long id) {
        return accountService.getAccount(id);
    }

    /**
     * 分页查询指定账户余额流水。
     *
     * @param id 资金账户主键
     * @param query 流水筛选和分页条件
     * @return 指定账户余额流水分页结果
     */
    public PageResult<FundLedgerResponse> pageLedgers(Long id, FundDetailQuery query) {
        return accountService.pageLedgers(id, query);
    }

    /**
     * 按账户和筛选条件导出全部余额明细，不受当前页限制。
     *
     * @param id 资金账户主键
     * @param request 流水筛选条件
     * @param response Excel 文件响应，写入后不再返回 JSON
     */
    public void exportLedgers(Long id, FundDetailQuery request, HttpServletResponse response) {
        FundDetailQuery query = request == null ? new FundDetailQuery() : request;
        exportPaged("excel.fund.ledgerTitle", FundLedgerExportRow.class,
                pageNo -> {
                    query.setPageNo(pageNo);
                    query.setPageSize(EXPORT_PAGE_SIZE);
                    return accountService.pageLedgers(id, query).getRecords().stream()
                            .map(this::toLedgerExportRow).toList();
                }, "accountId=" + id + ", keyword=" + query.getKeyword()
                        + ", balanceType=" + query.getBalanceType()
                        + ", businessType=" + query.getBusinessType()
                        + ", direction=" + query.getDirection()
                        + ", postedStartTime=" + query.getPostedStartTime()
                        + ", postedEndTime=" + query.getPostedEndTime(), response);
    }

    /**
     * 分页查询所有商户的余额流水。
     *
     * @param query 全局流水筛选和分页条件
     * @return 全局余额流水分页结果
     */
    public PageResult<FundLedgerResponse> pageAllLedgers(FundDetailQuery query) {
        return accountService.pageAllLedgers(query);
    }

    /**
     * 按全局筛选条件导出所有商户余额流水，不受当前页限制。
     *
     * @param request 商户、账户、业务类型、方向和入账时间条件
     * @param response Excel 文件响应，写入后不再返回 JSON
     */
    public void exportAllLedgers(FundDetailQuery request, HttpServletResponse response) {
        FundDetailQuery query = request == null ? new FundDetailQuery() : request;
        exportPaged("excel.fund.ledgerTitle", FundLedgerExportRow.class,
                pageNo -> {
                    query.setPageNo(pageNo);
                    query.setPageSize(EXPORT_PAGE_SIZE);
                    return accountService.pageAllLedgers(query).getRecords().stream()
                            .map(this::toLedgerExportRow).toList();
                }, "merchantId=" + query.getMerchantId()
                        + ", accountNo=" + query.getAccountNo()
                        + ", keyword=" + query.getKeyword()
                        + ", businessType=" + query.getBusinessType()
                        + ", direction=" + query.getDirection()
                        + ", postedStartTime=" + query.getPostedStartTime()
                        + ", postedEndTime=" + query.getPostedEndTime(), response);
    }

    /**
     * 分页查询充值申请及审批状态。
     *
     * @param query 商户、状态、关键字和分页条件
     * @return 充值申请分页结果
     */
    public PageResult<FundRechargeResponse> pageRecharges(FundRechargeQuery query) {
        return accountService.pageRecharges(query);
    }

    /**
     * 使用当前登录账号创建待审核充值申请。
     *
     * @param request 账户、金额、请求号和充值说明
     * @return 已创建或幂等命中的充值申请
     */
    public FundRechargeResponse createRecharge(FundRechargeCreateRequest request) {
        Operator operator = currentOperator();
        return accountService.createRecharge(request, operator.id(), operator.name(), operator.loginAccount());
    }

    /**
     * 使用当前登录账号审核充值申请。
     *
     * @param id 充值申请主键
     * @param comment 审核意见，允许为空
     * @return 待复核充值申请
     */
    public FundRechargeResponse auditRecharge(Long id, String comment) {
        Operator operator = currentOperator();
        return accountService.auditRecharge(id, comment, operator.id(), operator.name(), operator.loginAccount());
    }

    /**
     * 使用当前登录账号复核充值申请并原子入账。
     *
     * @param id 充值申请主键
     * @param comment 复核意见，允许为空
     * @return 已入账充值申请
     */
    public FundRechargeResponse recheckRecharge(Long id, String comment) {
        Operator operator = currentOperator();
        return accountService.recheckRecharge(id, comment, operator.id(), operator.name(), operator.loginAccount());
    }

    /**
     * 使用当前登录账号驳回待审核或待复核充值申请。
     *
     * @param id 充值申请主键
     * @param comment 驳回原因，不允许为空
     * @return 已驳回充值申请
     */
    public FundRechargeResponse rejectRecharge(Long id, String comment) {
        Operator operator = currentOperator();
        return accountService.rejectRecharge(id, comment, operator.id(), operator.name(), operator.loginAccount());
    }

    /**
     * 冻结账户，允许入账和结算但禁止主动出账、提现与逆向交易。
     *
     * @param id 资金账户主键
     * @param request 期望版本号和操作原因
     * @return 冻结后的账户能力快照
     */
    public FundAccountResponse freezeAccount(Long id, FundAccountStatusRequest request) {
        return changeAccountStatus(id, request, "FROZEN");
    }

    /**
     * 解冻账户并恢复为正常人工状态，负余额限制仍独立生效。
     *
     * @param id 资金账户主键
     * @param request 期望版本号和操作原因
     * @return 解冻后的账户能力快照
     */
    public FundAccountResponse unfreezeAccount(Long id, FundAccountStatusRequest request) {
        return changeAccountStatus(id, request, "NORMAL");
    }

    /**
     * 关闭账户，保留人工充值入口但禁止结算和任何资金转出。
     *
     * @param id 资金账户主键
     * @param request 期望版本号和操作原因
     * @return 关闭后的账户能力快照
     */
    public FundAccountResponse closeAccount(Long id, FundAccountStatusRequest request) {
        return changeAccountStatus(id, request, "CLOSED");
    }

    /**
     * 将关闭账户恢复为正常人工状态，负余额限制仍独立生效。
     *
     * @param id 资金账户主键
     * @param request 期望版本号和操作原因
     * @return 恢复后的账户能力快照
     */
    public FundAccountResponse reopenAccount(Long id, FundAccountStatusRequest request) {
        return changeAccountStatus(id, request, "NORMAL");
    }

    /**
     * 按筛选条件导出全部充值申请及审批信息。
     *
     * @param request 充值申请筛选条件
     * @param response Excel 文件响应，写入后不再返回 JSON
     */
    public void exportRecharges(FundRechargeQuery request, HttpServletResponse response) {
        FundRechargeQuery query = request == null ? new FundRechargeQuery() : request;
        exportPaged("excel.fund.rechargeTitle", FundRechargeExportRow.class,
                pageNo -> {
                    query.setPageNo(pageNo);
                    query.setPageSize(EXPORT_PAGE_SIZE);
                    return accountService.pageRecharges(query).getRecords().stream()
                            .map(this::toRechargeExportRow).toList();
                }, "keyword=" + query.getKeyword() + ", merchantId=" + query.getMerchantId()
                        + ", rechargeStatus=" + query.getRechargeStatus(), response);
    }

    /** 分页查询账户扣减申请及审批状态。 */
    public PageResult<FundDeductionResponse> pageDeductions(FundDeductionQuery query) {
        return accountService.pageDeductions(query);
    }

    /** 查询账户扣减申请详情。 */
    public FundDeductionResponse getDeduction(Long id) {
        return accountService.getDeduction(id);
    }

    /** 使用当前登录账号创建待审核账户扣减申请。 */
    public FundDeductionResponse createDeduction(FundDeductionCreateRequest request) {
        Operator operator = currentOperator();
        return accountService.createDeduction(request, operator.id(), operator.name(), operator.loginAccount());
    }

    /** 使用当前登录账号审核账户扣减申请。 */
    public FundDeductionResponse auditDeduction(Long id, String comment) {
        Operator operator = currentOperator();
        return accountService.auditDeduction(id, comment, operator.id(), operator.name(), operator.loginAccount());
    }

    /** 使用当前登录账号复核账户扣减申请并原子入账。 */
    public FundDeductionResponse recheckDeduction(Long id, String comment) {
        Operator operator = currentOperator();
        return accountService.recheckDeduction(id, comment, operator.id(), operator.name(), operator.loginAccount());
    }

    /** 使用当前登录账号驳回账户扣减申请。 */
    public FundDeductionResponse rejectDeduction(Long id, String comment) {
        Operator operator = currentOperator();
        return accountService.rejectDeduction(id, comment, operator.id(), operator.name(), operator.loginAccount());
    }

    /** 按筛选条件导出全部账户扣减申请及审批信息。 */
    public void exportDeductions(FundDeductionQuery request, HttpServletResponse response) {
        FundDeductionQuery query = request == null ? new FundDeductionQuery() : request;
        exportPaged("excel.fund.deductionTitle", FundDeductionExportRow.class,
                pageNo -> {
                    query.setPageNo(pageNo);
                    query.setPageSize(EXPORT_PAGE_SIZE);
                    return accountService.pageDeductions(query).getRecords().stream()
                            .map(this::toDeductionExportRow).toList();
                }, "keyword=" + query.getKeyword() + ", merchantId=" + query.getMerchantId()
                        + ", deductionCategory=" + query.getDeductionCategory()
                        + ", deductionStatus=" + query.getDeductionStatus(), response);
    }

    /** 将账户基础信息映射为导出行，不触发派生余额查询。 */
    private FundAccountExportRow toExportRow(FundAccountResponse source) {
        FundAccountExportRow row = new FundAccountExportRow();
        row.setAccountNo(source.getAccountNo());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setSettlementCurrency(source.getSettlementCurrency());
        row.setAvailableBalance(source.getAvailableBalance());
        row.setAccountStatus(source.getAccountStatus());
        row.setReverseRestricted(source.getReverseRestricted());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /** 将不可变余额流水映射为核对导出行。 */
    private FundLedgerExportRow toLedgerExportRow(FundLedgerResponse source) {
        FundLedgerExportRow row = new FundLedgerExportRow();
        row.setLedgerNo(source.getLedgerNo());
        row.setAccountNo(source.getAccountNo());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setBusinessType(source.getBusinessType());
        row.setSummary(source.getSummary());
        row.setBusinessNo(source.getBusinessNo());
        row.setBalanceType(source.getBalanceType());
        row.setDirection(source.getDirection());
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setBalanceBefore(source.getBalanceBefore());
        row.setBalanceAfter(source.getBalanceAfter());
        row.setAccountSequence(source.getAccountSequence());
        row.setOperatorName(source.getOperatorName());
        row.setReviewerName(source.getReviewerName());
        row.setOperationReason(source.getOperationReason());
        row.setReviewComment(source.getReviewComment());
        row.setBusinessTime(source.getBusinessTime());
        row.setPostedTime(source.getPostedTime());
        row.setRequestId(source.getRequestId());
        row.setIdempotencyKey(source.getIdempotencyKey());
        return row;
    }

    /** 使用当前登录账号提交带账户期望版本保护的状态变更。 */
    private FundAccountResponse changeAccountStatus(Long id,
                                                    FundAccountStatusRequest request,
                                                    String targetStatus) {
        Operator operator = currentOperator();
        return accountService.changeAccountStatus(id, request.getAccountVersion(), targetStatus,
                request.getReason(), operator.id(), operator.name());
    }

    /** 将充值申请和审批快照映射为导出行。 */
    private FundRechargeExportRow toRechargeExportRow(FundRechargeResponse source) {
        FundRechargeExportRow row = new FundRechargeExportRow();
        row.setRechargeNo(source.getRechargeNo());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setAccountNo(source.getAccountNo());
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setRechargeStatus(source.getRechargeStatus());
        row.setRemark(source.getRemark());
        row.setSubmitByName(source.getSubmitByName());
        row.setSubmitTime(source.getSubmitTime());
        row.setAuditByName(source.getAuditByName());
        row.setAuditTime(source.getAuditTime());
        row.setRecheckByName(source.getRecheckByName());
        row.setRecheckTime(source.getRecheckTime());
        row.setLedgerNo(source.getLedgerNo());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    /** 将扣减申请和完整审批快照映射为导出行。 */
    private FundDeductionExportRow toDeductionExportRow(FundDeductionResponse source) {
        FundDeductionExportRow row = new FundDeductionExportRow();
        row.setDeductionNo(source.getDeductionNo());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setAccountNo(source.getAccountNo());
        row.setDeductionCategory(source.getDeductionCategory());
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setDeductionStatus(source.getDeductionStatus());
        row.setReason(source.getReason());
        row.setSubmitByName(source.getSubmitByName());
        row.setSubmitTime(source.getSubmitTime());
        row.setAuditByName(source.getAuditByName());
        row.setAuditTime(source.getAuditTime());
        row.setRecheckByName(source.getRecheckByName());
        row.setRecheckTime(source.getRecheckTime());
        row.setLedgerNo(source.getLedgerNo());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    /** 分页拉取导出数据，避免一次性加载全部资金明细到内存。 */
    private <T> void exportPaged(String titleKey,
                                 Class<T> rowClass,
                                 java.util.function.IntFunction<java.util.List<T>> pageLoader,
                                 String querySummary,
                                 HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        String title = excelI18nMessageResolver.resolve(titleKey, locale);
        LocalDateTime now = LocalDateTime.now();
        excelExportService.exportPaged(ExcelPagedExportRequest.<T>builder()
                .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                .sheetName(title)
                .titleKey(titleKey)
                .operator(currentOperator().name())
                .exportTime(now)
                .locale(locale)
                .querySummary(querySummary)
                .rowClass(rowClass)
                .pageSize(EXPORT_PAGE_SIZE)
                .pageLoader(pageLoader::apply)
                .build(), response);
    }

    /** 返回当前登录操作人名称快照，用于导出审计。 */
    private String currentOperatorName() {
        return currentOperator().name();
    }

    /** 读取完整登录账号上下文；缺少主键或登录名时拒绝资金操作。 */
    private Operator currentOperator() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null
                || !StringUtils.hasText(account.getLoginAccount())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "登录账号上下文缺失");
        }
        String name = StringUtils.hasText(account.getRealName())
                ? account.getRealName() : account.getLoginAccount();
        return new Operator(account.getAccountId(), name, account.getLoginAccount());
    }

    private record Operator(Long id, String name, String loginAccount) {
    }
}
