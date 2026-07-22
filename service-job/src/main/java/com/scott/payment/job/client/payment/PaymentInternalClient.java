package com.scott.payment.job.client.payment;

import com.scott.payment.job.client.payment.dto.PaymentMerchantNotificationNotifyDueClientRequestDTO;
import com.scott.payment.job.client.payment.dto.PaymentChannelMatchClientRequestDTO;
import com.scott.payment.job.client.payment.dto.PaymentChannelMatchClientResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalClient
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : service-payment 内部补偿客户端契约，位于 service-job 客户端层，为定时任务封装支付核心内部接口调用。
 * @status : create
 */
public interface PaymentInternalClient {

    /**
     * 触发指定交易时间分表中的到期商户通知补偿。
     *
     * @param requestDTO 补偿请求
     * @return 成功通知数量
     */
    Integer notifyDueMerchantNotifications(PaymentMerchantNotificationNotifyDueClientRequestDTO requestDTO);

    /**
     * 触发指定交易时间分表中的渠道交易查询勾兑。
     *
     * @param requestDTO 查询勾兑请求
     * @return 查询勾兑处理结果
     */
    PaymentChannelMatchClientResultDTO matchDueChannelTransactions(PaymentChannelMatchClientRequestDTO requestDTO);
}
