package com.scott.payment.job.client.payment;

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
     * 关闭超过付款截止时间且从未提交支付的收银台订单。
     *
     * @param limit 单次扫描上限，支付服务会再次执行范围保护
     * @return 本次成功推进为超时失败的订单数量
     */
    int expireDueCheckoutSessions(int limit);

    /**
     * 触发指定交易时间分表中的渠道交易查询勾兑。
     *
     * @param requestDTO 查询勾兑请求
     * @return 查询勾兑处理结果
     */
    PaymentChannelMatchClientResultDTO matchDueChannelTransactions(PaymentChannelMatchClientRequestDTO requestDTO);
}
