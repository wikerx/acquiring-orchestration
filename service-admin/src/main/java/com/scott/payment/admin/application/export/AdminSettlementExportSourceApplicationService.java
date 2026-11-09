package com.scott.payment.admin.application.export;

import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReservePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReservePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReserveRow;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementTransactionPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementTransactionPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementTransactionRow;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.TransactionSettlementSummary;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminSettlementReportingQueryService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * 管理端结算异步导出来源服务，恢复冻结查询并在 SQL 层应用冻结商户范围。
 */
@Service
public class AdminSettlementExportSourceApplicationService {
    private final AdminSettlementReportingQueryService reportingQueryService;

    /**
     * 创建结算异步导出来源服务。
     */
    public AdminSettlementExportSourceApplicationService(
            AdminSettlementReportingQueryService reportingQueryService) {
        this.reportingQueryService = reportingQueryService;
    }

    /**
     * 按冻结条件和商户范围读取一页结算交易安全投影。
     */
    public SettlementTransactionPageResponse pageTransactions(
            SettlementTransactionPageRequest request) {
        PageResult<TransactionSettlementSummary> page = reportingQueryService.searchResultItems(
                toTransactionQuery(request), dataScope(request.isAllMerchants(), request.getMerchantIds()));
        SettlementTransactionPageResponse response = new SettlementTransactionPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toTransactionRow).toList());
        return response;
    }

    /**
     * 按冻结条件和商户范围读取一页保证金结算安全投影。
     */
    public SettlementReservePageResponse pageReserves(SettlementReservePageRequest request) {
        PageResult<ReserveItemSummary> page = reportingQueryService.searchReserveItems(
                toReserveQuery(request), dataScope(request.isAllMerchants(), request.getMerchantIds()));
        SettlementReservePageResponse response = new SettlementReservePageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toReserveRow).toList());
        return response;
    }

    /**
     * 恢复结算交易筛选并由内部请求覆盖分页参数。
     */
    private ResultItemSearchRequest toTransactionQuery(SettlementTransactionPageRequest request) {
        var source = request.getQuery();
        ResultItemSearchRequest target = new ResultItemSearchRequest();
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
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /**
     * 恢复保证金结算筛选并由内部请求覆盖分页参数。
     */
    private ReserveItemSearchRequest toReserveQuery(SettlementReservePageRequest request) {
        var source = request.getQuery();
        ReserveItemSearchRequest target = new ReserveItemSearchRequest();
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
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /**
     * 将冻结范围恢复为显式全量或有限集合，空有限集合保持无权限语义。
     */
    private AdminMerchantDataScope dataScope(boolean allMerchants, java.util.Set<String> merchantIds) {
        return allMerchants ? AdminMerchantDataScope.all() : AdminMerchantDataScope.limited(merchantIds);
    }

    /**
     * 将结算交易查询结果投影为内部安全导出行，不重新计算任何金额。
     */
    private SettlementTransactionRow toTransactionRow(TransactionSettlementSummary source) {
        SettlementTransactionRow row = new SettlementTransactionRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setBatchStatus(source.getBatchStatus());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setSourceTransactionDateTime(source.getSourceTransactionDateTime());
        row.setPaymentType(source.getPaymentType());
        row.setPaymentMethod(source.getPaymentMethod());
        row.setTransactionType(source.getTransactionType());
        row.setSourceAmount(source.getSourceAmount());
        row.setSourceCurrency(source.getSourceCurrency());
        row.setComponentCount(source.getComponentCount());
        row.setNetDirection(source.getNetDirection());
        row.setNetTargetAmount(source.getNetTargetAmount());
        row.setTargetCurrency(source.getTargetCurrency());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    /**
     * 将保证金动作查询结果投影为内部安全导出行，不转换标签币种。
     */
    private SettlementReserveRow toReserveRow(ReserveItemSummary source) {
        SettlementReserveRow row = new SettlementReserveRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setMerchantId(source.getMerchantId());
        row.setReserveNo(source.getReserveNo());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setSourceTransactionDateTime(source.getSourceTransactionDateTime());
        row.setActionType(source.getActionType());
        row.setDirection(source.getDirection());
        row.setCurrency(source.getCurrency());
        row.setAmount(source.getAmount());
        row.setRemainingAmount(source.getRemainingAmount());
        row.setReserveStatus(source.getReserveStatus());
        row.setExpectedReleaseDate(source.getExpectedReleaseDate());
        row.setActionTime(source.getActionTime());
        return row;
    }
}
