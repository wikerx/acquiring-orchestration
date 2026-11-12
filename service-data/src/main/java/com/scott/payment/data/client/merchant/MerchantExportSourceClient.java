package com.scott.payment.data.client.merchant;

import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.RefundPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.RefundPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.FundLedgerPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.FundLedgerPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementBatchPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementBatchPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementReservePageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementReservePageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementSummaryPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementSummaryPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementTransactionPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementTransactionPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.TransactionPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.TransactionPageResponse;

/**
 * service-data 分页读取 Merchant 交易、退款和结算安全导出投影的内部客户端契约。
 */
public interface MerchantExportSourceClient {

    /**
     * 按冻结商户和条件读取一页交易动作。
     */
    TransactionPageResponse loadTransactionPage(TransactionPageRequest request);

    /**
     * 按冻结商户和条件读取一页退款记录。
     */
    RefundPageResponse loadRefundPage(RefundPageRequest request);

    /**
     * 按冻结商户和条件读取一页结算批次。
     */
    SettlementBatchPageResponse loadSettlementBatchPage(SettlementBatchPageRequest request);

    /**
     * 按冻结商户读取指定批次的一页结算汇总。
     */
    SettlementSummaryPageResponse loadSettlementSummaryPage(SettlementSummaryPageRequest request);

    /**
     * 按冻结商户和条件读取一页交易结算明细。
     */
    SettlementTransactionPageResponse loadSettlementTransactionPage(
            SettlementTransactionPageRequest request);

    /**
     * 按冻结商户和条件读取一页保证金结算明细。
     */
    SettlementReservePageResponse loadSettlementReservePage(SettlementReservePageRequest request);

    /**
     * 按冻结商户、账户和条件读取一页余额流水。
     */
    FundLedgerPageResponse loadFundLedgerPage(FundLedgerPageRequest request);
}
