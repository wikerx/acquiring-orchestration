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

    /**
     * 按已落库的渠道和 MID 配置恢复路由快照。
     * <p>
     * 渠道查询勾兑不能重新路由，否则可能使用不同 MID 查询不到原渠道交易；该方法只恢复请求地址、超时和 MID 元数据。
     *
     * @param channelCode 渠道编码
     * @param channelId 渠道信息 ID
     * @param midConfigId MID 配置 ID
     * @param fallbackMidNo 历史动作单保存的 MID 或终端号
     * @return 路由结果快照
     */
    default PaymentRouteResultDTO restore(String channelCode, Long channelId, Long midConfigId, String fallbackMidNo) {
        throw new UnsupportedOperationException("restore route result is not implemented");
    }
}
