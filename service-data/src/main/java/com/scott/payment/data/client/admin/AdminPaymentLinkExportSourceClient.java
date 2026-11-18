package com.scott.payment.data.client.admin;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.data.client.paymentlink.PaymentLinkExportDTOs.*;

/**
 * 支付链接安全分页来源，复用已有 Data 到业务系统的 HMAC 边界。
 */
public interface AdminPaymentLinkExportSourceClient {
    /**
     * 以任务冻结的账号范围读取一页数据，禁止返回分享凭据或个人信息。
     */
    PageResult<Row> loadPaymentLinkPage(PageRequest request);
}
