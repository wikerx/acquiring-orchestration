package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionLocatorService
 * @date : 2026-08-14 12:35
 * @email : scott_x@163.com
 * @description : 交易定位服务，位于 service-payment 服务层，为商户后续动作和查询补齐内部交易分片路由字段。
 * @status : create
 */
public interface TransactionLocatorService {

    /**
     * 根据源交易 ID 补齐后续资金动作的分片路由时间。
     *
     * @param commandDTO 后续动作命令
     */
    void enrichFollowUpRoute(PaymentCreateCommandDTO commandDTO);

    /**
     * 根据平台交易 ID 或商户订单号补齐查询路由时间。
     *
     * @param commandDTO 商户交易查询命令
     */
    void enrichQueryRoute(PaymentCreateCommandDTO commandDTO);
}
