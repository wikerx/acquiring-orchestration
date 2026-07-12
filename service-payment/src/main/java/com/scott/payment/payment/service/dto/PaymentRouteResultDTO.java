package com.scott.payment.payment.service.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRouteResultDTO
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道路由结果 DTO，位于 service-payment 服务 DTO 层，用于承载渠道编码、MID 标识和路由原因。
 * @status : create
 */
@Data
public class PaymentRouteResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否路由成功。
     */
    private boolean routed;

    /**
     * 渠道编码，例如 MPGS。
     */
    private String channelCode;

    /**
     * MID 配置主键，交易表后续应记录该配置快照。
     */
    private Long midConfigId;

    /**
     * 渠道真实 MID，例如 MPGS merchantId。
     */
    private String midNo;

    /**
     * 渠道请求地址，来自 channel_info。
     */
    private String requestUrl;

    /**
     * 渠道连接超时时间，单位秒。
     */
    private Integer connectTimeoutSeconds;

    /**
     * 渠道读取超时时间，单位秒。
     */
    private Integer readTimeoutSeconds;

    /**
     * 渠道 MID 元数据，来自 channel_mid_config.metadata_value_json。
     */
    private Map<String, String> metadataValues = new HashMap<>();

    /**
     * 路由失败或命中原因。
     */
    private String routeReason;

    /**
     * 构造成功路由结果。
     *
     * @param channelCode 渠道编码
     * @return 路由结果
     */
    public static PaymentRouteResultDTO routed(String channelCode) {
        PaymentRouteResultDTO dto = new PaymentRouteResultDTO();
        dto.setRouted(true);
        dto.setChannelCode(channelCode);
        return dto;
    }
}
