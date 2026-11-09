package com.scott.payment.data.client.admin;

import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.OrderPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.OrderPageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.RefundPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.RefundPageResponse;

/**
 * service-data 分页读取管理端交易主单和退款导出源的内部客户端契约。
 */
public interface AdminTransactionExportSourceClient {

    /**
     * 按冻结条件和商户范围读取一页交易主单。
     */
    OrderPageResponse loadOrderPage(OrderPageRequest request);

    /**
     * 按冻结条件和商户范围读取一页退款记录。
     */
    RefundPageResponse loadRefundPage(RefundPageRequest request);
}
