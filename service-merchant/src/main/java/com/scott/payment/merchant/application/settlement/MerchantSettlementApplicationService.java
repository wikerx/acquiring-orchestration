package com.scott.payment.merchant.application.settlement;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.merchant.dto.export.MerchantSettlementBatchExportRow;
import com.scott.payment.merchant.dto.export.MerchantSettlementReserveExportRow;
import com.scott.payment.merchant.dto.export.MerchantSettlementTransactionExportRow;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchDetail;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchSummary;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery;
import com.scott.payment.merchant.service.MerchantSettlementQueryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSettlementApplicationService
 * @date : 2026-09-01 22:35
 * @email : scott_x@163.com
 * @description : 商户结算查询和导出编排；每次查询及每个导出分页都从可信登录上下文重新绑定 merchantId，禁止客户端指定数据范围。
 * @status : update
 */
@Service
public class MerchantSettlementApplicationService {

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
     * {@code EXPORT_TIME_FORMATTER}常量，统一 {@code MerchantSettlementApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MerchantSettlementQueryService queryService;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver messageResolver;
    private final ExcelLocaleResolver localeResolver;

    public MerchantSettlementApplicationService(MerchantSettlementQueryService queryService,
                                                ExcelExportService excelExportService,
                                                ExcelI18nMessageResolver messageResolver,
                                                ExcelLocaleResolver localeResolver) {
        this.queryService = queryService;
        this.excelExportService = excelExportService;
        this.messageResolver = messageResolver;
        this.localeResolver = localeResolver;
    }

    /**
     * 每次调用重新读取可信登录上下文，查询当前认证商户结算批次。
     *
     * @param query 可空批次过滤和分页条件
     * @return 仅当前 merchantId 的批次分页
     */
    public PageResult<BatchSummary> searchBatches(BatchQuery query) {
        return queryService.searchBatches(currentMerchantId(), query);
    }

    /**
     * 查询当前认证商户指定结算批次详情。
     *
     * @param settlementBatchNo 正式结算批次号
     * @return 仅当前 merchantId 可见的净额、汇率和聚合详情
     */
    public BatchDetail getBatch(String settlementBatchNo) {
        return queryService.getBatch(currentMerchantId(), settlementBatchNo);
    }

    /**
     * 查询当前认证商户真实交易结算明细。
     *
     * @param query 可空交易明细过滤和分页条件
     * @return 仅当前 merchantId 且具备真实 transactionId 的明细分页
     */
    public PageResult<TransactionItem> searchTransactionItems(TransactionItemQuery query) {
        return queryService.searchTransactionItems(currentMerchantId(), query);
    }

    /**
     * 查询当前认证商户保证金结算动作明细。
     *
     * @param query 可空保证金动作过滤和分页条件
     * @return 仅当前 merchantId 的保证金动作分页
     */
    public PageResult<ReserveItem> searchReserveItems(ReserveItemQuery query) {
        return queryService.searchReserveItems(currentMerchantId(), query);
    }

    /**
     * 按固定页大小流式导出当前商户的结算批次，防止一次性加载全部数据。
     *
     * @param request 可空批次过滤条件，每个导出分页都会重新绑定可信 merchantId
     * @param response Excel 流式下载响应
     */
    public void exportBatches(BatchQuery request, HttpServletResponse response) {
        BatchQuery query = request == null ? new BatchQuery() : request;
        Locale locale = localeResolver.resolveCurrentLocale();
        exportPaged("excel.merchantSettlement.batchTitle", MerchantSettlementBatchExportRow.class,
                pageNo -> {
                    query.setPageNo(pageNo);
                    query.setPageSize(EXPORT_PAGE_SIZE);
                    return searchBatches(query).getRecords().stream()
                            .map(item -> toBatchRow(item, locale)).toList();
                }, response);
    }

    /**
     * 按固定页大小流式导出当前商户的真实交易结算明细。
     *
     * @param request 可空交易明细过滤条件，每个导出分页都会重新绑定可信 merchantId
     * @param response Excel 流式下载响应
     */
    public void exportTransactionItems(TransactionItemQuery request, HttpServletResponse response) {
        TransactionItemQuery query = request == null ? new TransactionItemQuery() : request;
        Locale locale = localeResolver.resolveCurrentLocale();
        exportPaged("excel.merchantSettlement.transactionTitle", MerchantSettlementTransactionExportRow.class,
                pageNo -> {
                    query.setPageNo(pageNo);
                    query.setPageSize(EXPORT_PAGE_SIZE);
                    return searchTransactionItems(query).getRecords().stream()
                            .map(item -> toTransactionRow(item, locale)).toList();
                }, response);
    }

    /**
     * 按固定页大小流式导出当前商户的保证金动作明细。
     *
     * @param request 可空保证金动作过滤条件，每个导出分页都会重新绑定可信 merchantId
     * @param response Excel 流式下载响应
     */
    public void exportReserveItems(ReserveItemQuery request, HttpServletResponse response) {
        ReserveItemQuery query = request == null ? new ReserveItemQuery() : request;
        Locale locale = localeResolver.resolveCurrentLocale();
        exportPaged("excel.merchantSettlement.reserveTitle", MerchantSettlementReserveExportRow.class,
                pageNo -> {
                    query.setPageNo(pageNo);
                    query.setPageSize(EXPORT_PAGE_SIZE);
                    return searchReserveItems(query).getRecords().stream()
                            .map(item -> toReserveRow(item, locale)).toList();
                }, response);
    }

    /**
     * 使用统一 Excel 组件分页拉取和写出，导出审计仅记录 merchantId，不写入敏感请求信息。
     */
    private <T> void exportPaged(String titleKey, Class<T> rowClass,
                                 java.util.function.IntFunction<List<T>> pageLoader,
                                 HttpServletResponse response) {
        InternalAuthAccount account = currentMerchantAccount();
        Locale locale = localeResolver.resolveCurrentLocale();
        String title = messageResolver.resolve(titleKey, locale);
        LocalDateTime now = LocalDateTime.now();
        excelExportService.exportPaged(ExcelPagedExportRequest.<T>builder()
                .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                .sheetName(title).titleKey(titleKey).operator(operatorName(account))
                .exportTime(now).locale(locale).querySummary("merchantId=" + account.getMerchantId())
                .rowClass(rowClass).pageSize(EXPORT_PAGE_SIZE).pageLoader(pageLoader).build(), response);
    }

    private MerchantSettlementBatchExportRow toBatchRow(BatchSummary source, Locale locale) {
        MerchantSettlementBatchExportRow row = new MerchantSettlementBatchExportRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setBatchType(settlementLabel("batchType", source.getBatchType(), locale));
        row.setBatchStatus(settlementLabel("batchStatus", source.getBatchStatus(), locale));
        row.setTargetCurrency(source.getTargetCurrency());
        row.setTransactionCount(source.getTransactionCount());
        row.setSettlementItemCount(source.getCandidateCount());
        row.setNetDirection(settlementLabel("direction", source.getNetDirection(), locale));
        row.setNetAmount(source.getNetAmount());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    private MerchantSettlementTransactionExportRow toTransactionRow(TransactionItem source, Locale locale) {
        MerchantSettlementTransactionExportRow row = new MerchantSettlementTransactionExportRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setSourceTransactionDateTime(source.getSourceTransactionDateTime());
        row.setResultItemType(settlementLabel("resultItemType", source.getResultItemType(), locale));
        row.setPaymentType(settlementLabel("paymentType", source.getPaymentType(), locale));
        row.setPaymentMethod(settlementLabel("paymentMethod", source.getPaymentMethod(), locale));
        row.setTransactionType(settlementLabel("transactionType", source.getTransactionType(), locale));
        row.setFeeCategory(settlementLabel("feeCategory", source.getFeeCategory(), locale));
        row.setDirection(settlementLabel("direction", source.getDirection(), locale));
        row.setSourceAmount(source.getSourceAmount());
        row.setSourceCurrency(source.getSourceCurrency());
        row.setDirectRate(source.getDirectRate());
        row.setTargetAmount(source.getTargetAmount());
        row.setTargetCurrency(source.getTargetCurrency());
        row.setAppliedLimit(settlementLabel("appliedLimit", source.getAppliedLimit(), locale));
        return row;
    }

    private MerchantSettlementReserveExportRow toReserveRow(ReserveItem source, Locale locale) {
        MerchantSettlementReserveExportRow row = new MerchantSettlementReserveExportRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setReserveNo(source.getReserveNo());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setSourceTransactionDateTime(source.getSourceTransactionDateTime());
        row.setActionType(settlementLabel("reserveAction", source.getActionType(), locale));
        row.setDirection(settlementLabel("direction", source.getDirection(), locale));
        row.setCurrency(source.getCurrency());
        row.setAmount(source.getAmount());
        row.setRemainingAmount(source.getRemainingAmount());
        row.setReserveStatus(settlementLabel("reserveStatus", source.getReserveStatus(), locale));
        row.setExpectedReleaseDate(source.getExpectedReleaseDate());
        row.setActionTime(source.getActionTime());
        return row;
    }

    private String settlementLabel(String category, String value, Locale locale) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        String key = "excel.settlement.enum." + category + "." + normalized;
        String resolved = messageResolver.resolve(key, locale);
        return key.equals(resolved) ? normalized : resolved;
    }

    private String currentMerchantId() {
        return currentMerchantAccount().getMerchantId();
    }

    /**
     * 解析当前可信登录商户；不存在认证账户或 merchantId 时直接拒绝，禁止回退到请求参数。
     */
    private InternalAuthAccount currentMerchantAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return account;
    }

    private String operatorName(InternalAuthAccount account) {
        String value = StringUtils.hasText(account.getRealName()) ? account.getRealName() : account.getLoginAccount();
        return StringUtils.hasText(value) ? value.trim() : "merchant";
    }
}
