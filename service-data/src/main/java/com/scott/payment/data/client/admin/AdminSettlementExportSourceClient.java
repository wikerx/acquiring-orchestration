package com.scott.payment.data.client.admin;

import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReservePageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReservePageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementTransactionPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementTransactionPageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementPostingPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementPostingPageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReviewPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReviewPageResponse;

/**
 * service-data 分页读取管理端结算导出源的内部客户端契约。
 */
public interface AdminSettlementExportSourceClient {

    /**
     * 按冻结条件和商户范围读取一页结算交易明细。
     */
    SettlementTransactionPageResponse loadSettlementTransactionPage(
            SettlementTransactionPageRequest request);

    /**
     * 按冻结条件和商户范围读取一页保证金结算明细。
     */
    SettlementReservePageResponse loadSettlementReservePage(SettlementReservePageRequest request);

    /** 按冻结条件和商户范围读取一页结算审核单。 */
    SettlementReviewPageResponse loadSettlementReviewPage(SettlementReviewPageRequest request);

    /** 按冻结条件和商户范围读取一页结算入账流水。 */
    SettlementPostingPageResponse loadSettlementPostingPage(SettlementPostingPageRequest request);
}
