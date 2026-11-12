package com.scott.payment.merchant.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.merchant.application.export.MerchantExportSourceApplicationService;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.RefundPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.RefundPageResponse;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.FundLedgerPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.FundLedgerPageResponse;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementBatchPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementBatchPageResponse;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementReservePageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementReservePageResponse;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementSummaryPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementSummaryPageResponse;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementTransactionPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementTransactionPageResponse;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.TransactionPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.TransactionPageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * service-data 调用的商户导出安全来源入口，受独立内部 HMAC 和防重放链路保护。
 */
@RestController
@RequestMapping("/internal/merchant/export-sources")
public class MerchantExportSourceInternalController {
    private final MerchantExportSourceApplicationService applicationService;

    /**
     * 创建商户导出来源内部入口。
     */
    public MerchantExportSourceInternalController(MerchantExportSourceApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 按冻结商户和条件分页读取交易安全投影。
     */
    @PostMapping("/transaction-operations")
    public CommonResult<TransactionPageResponse> transactionPage(
            @Valid @RequestBody TransactionPageRequest request) {
        return success(applicationService.pageTransactions(request));
    }

    /**
     * 按冻结商户和条件分页读取退款安全投影。
     */
    @PostMapping("/refunds")
    public CommonResult<RefundPageResponse> refundPage(@Valid @RequestBody RefundPageRequest request) {
        return success(applicationService.pageRefunds(request));
    }

    /**
     * 按冻结商户和条件分页读取结算批次安全投影。
     */
    @PostMapping("/settlement-batches")
    public CommonResult<SettlementBatchPageResponse> settlementBatchPage(
            @Valid @RequestBody SettlementBatchPageRequest request) {
        return success(applicationService.pageSettlementBatches(request));
    }

    /**
     * 按冻结商户分页读取指定批次的结算汇总安全投影。
     */
    @PostMapping("/settlement-summaries")
    public CommonResult<SettlementSummaryPageResponse> settlementSummaryPage(
            @Valid @RequestBody SettlementSummaryPageRequest request) {
        return success(applicationService.pageSettlementSummaries(request));
    }

    /**
     * 按冻结商户和条件分页读取交易结算安全投影。
     */
    @PostMapping("/settlement-transactions")
    public CommonResult<SettlementTransactionPageResponse> settlementTransactionPage(
            @Valid @RequestBody SettlementTransactionPageRequest request) {
        return success(applicationService.pageSettlementTransactions(request));
    }

    /**
     * 按冻结商户和条件分页读取保证金结算安全投影。
     */
    @PostMapping("/settlement-reserves")
    public CommonResult<SettlementReservePageResponse> settlementReservePage(
            @Valid @RequestBody SettlementReservePageRequest request) {
        return success(applicationService.pageSettlementReserves(request));
    }

    /**
     * 按冻结商户、资金账户和条件分页读取余额流水安全投影。
     */
    @PostMapping("/fund-ledgers")
    public CommonResult<FundLedgerPageResponse> fundLedgerPage(
            @Valid @RequestBody FundLedgerPageRequest request) {
        return success(applicationService.pageFundLedgers(request));
    }
}
