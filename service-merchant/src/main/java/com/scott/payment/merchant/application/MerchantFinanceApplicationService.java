package com.scott.payment.merchant.application;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.CurrentFeeResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.DetailQuery;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundAccountResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundLedgerResponse;
import com.scott.payment.merchant.dto.export.MerchantFundLedgerExportRow;
import com.scott.payment.merchant.service.MerchantFinanceService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFinanceApplicationService
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户费率、账户余额和余额明细只读应用服务，从认证上下文绑定商户数据范围。
 * @status : create
 */
@Service
public class MerchantFinanceApplicationService {

    private static final int EXPORT_PAGE_SIZE = 200;
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MerchantFinanceService financeService;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver messageResolver;
    private final ExcelLocaleResolver localeResolver;

    /**
     * 构造商户财务应用服务。
     *
     * @param financeService 商户财务只读领域服务
     * @param excelExportService 分页 Excel 导出服务
     * @param messageResolver Excel 国际化标题解析器
     * @param localeResolver 当前请求语言解析器
     */
    public MerchantFinanceApplicationService(MerchantFinanceService financeService,
                                             ExcelExportService excelExportService,
                                             ExcelI18nMessageResolver messageResolver,
                                             ExcelLocaleResolver localeResolver) {
        this.financeService = financeService;
        this.excelExportService = excelExportService;
        this.messageResolver = messageResolver;
        this.localeResolver = localeResolver;
    }

    /**
     * 从认证上下文绑定商户号并查询当前生效费率。
     *
     * @return 当前商户已生效费率；未配置时返回 null
     */
    public CurrentFeeResponse getCurrentFee() { return financeService.getCurrentFee(currentMerchantId()); }
    /**
     * 从认证上下文绑定商户号并查询资金账户及实时汇总。
     *
     * @return 当前商户资金账户响应
     */
    public FundAccountResponse getFundAccount() { return financeService.getFundAccount(currentMerchantId()); }
    /**
     * 查询当前认证商户不可变余额流水。
     *
     * @param query 流水筛选和分页条件
     * @return 当前商户余额流水分页结果
     */
    public PageResult<FundLedgerResponse> pageLedgers(DetailQuery query) {
        return financeService.pageLedgers(currentMerchantId(), query);
    }
    /**
     * 按当前认证商户与筛选条件分页导出全部余额明细。
     *
     * @param request 导出筛选条件
     * @param response Excel 文件响应，写入后不再返回 JSON
     */
    public void exportLedgers(DetailQuery request, HttpServletResponse response) {
        DetailQuery query = request == null ? new DetailQuery() : request;
        String merchantId = currentMerchantId();
        Locale locale = localeResolver.resolveCurrentLocale();
        String titleKey = "excel.fund.ledgerTitle";
        String title = messageResolver.resolve(titleKey, locale);
        LocalDateTime now = LocalDateTime.now();
        excelExportService.exportPaged(ExcelPagedExportRequest.<MerchantFundLedgerExportRow>builder()
                .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                .sheetName(title)
                .titleKey(titleKey)
                .operator(currentOperatorName())
                .exportTime(now)
                .locale(locale)
                .querySummary("merchantId=" + merchantId + ", keyword=" + query.getKeyword()
                        + ", balanceType=" + query.getBalanceType()
                        + ", businessType=" + query.getBusinessType())
                .rowClass(MerchantFundLedgerExportRow.class)
                .pageSize(EXPORT_PAGE_SIZE)
                .pageLoader(pageNo -> {
                    query.setPageNo(pageNo);
                    query.setPageSize(EXPORT_PAGE_SIZE);
                    return financeService.pageLedgers(merchantId, query).getRecords().stream()
                            .map(this::toLedgerExportRow).toList();
                })
                .build(), response);
    }
    /** 读取认证商户号；缺失时拒绝查询，禁止降级为未限定商户的访问。 */
    private String currentMerchantId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return account.getMerchantId();
    }

    /** 生成导出文件中的操作人快照，不包含敏感认证信息。 */
    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) return "merchant";
        return StringUtils.hasText(account.getRealName()) ? account.getRealName() : account.getLoginAccount();
    }

    /** 将商户可见流水映射为导出行，不带内部幂等键和追踪字段。 */
    private MerchantFundLedgerExportRow toLedgerExportRow(FundLedgerResponse source) {
        MerchantFundLedgerExportRow row = new MerchantFundLedgerExportRow();
        row.setLedgerNo(source.getLedgerNo());
        row.setBusinessType(source.getBusinessType());
        row.setSummary(source.getSummary());
        row.setBusinessNo(source.getBusinessNo());
        row.setBalanceType(source.getBalanceType());
        row.setDirection(source.getDirection());
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setBalanceBefore(source.getBalanceBefore());
        row.setBalanceAfter(source.getBalanceAfter());
        row.setOperatorName(source.getOperatorName());
        row.setReviewerName(source.getReviewerName());
        row.setPostedTime(source.getPostedTime());
        return row;
    }
}
