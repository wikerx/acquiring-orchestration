package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelRouteService
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道路由服务，位于 service-payment 服务层，用于根据商户、交易类型、支付方式、币种、卡品牌和渠道能力选择渠道及后续 MID。
 * @status : create
 */
public interface PaymentChannelRouteService {

    /**
     * 选择收单渠道。
     *
     * @param commandDTO 创建交易命令
     * @return 渠道路由结果
     */
    PaymentRouteResultDTO route(PaymentCreateCommandDTO commandDTO);
}
