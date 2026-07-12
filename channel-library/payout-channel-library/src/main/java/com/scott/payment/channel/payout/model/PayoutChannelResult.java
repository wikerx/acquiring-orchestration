package com.scott.payment.channel.payout.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelResult
 * @date : 2026-05-28 10:58
 * @email : scott_x@163.com
 * @description : 代付渠道响应模型
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelResult
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 渠道管理Payout Channel Result，位于 channel-library 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class PayoutChannelResult implements Serializable {

    /**
     * 序列化版本号，用于保证渠道响应对象在服务间传输时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 代付渠道编码，用于标识本次响应来自哪个代付渠道适配器。
     */
    private String channelCode;

    /**
     * 渠道侧订单号或流水号，用于后续查单、退汇、对账和问题排查。
     */
    private String channelOrderNo;

    /**
     * 渠道侧代付状态，进入 service-payout 后会映射为系统内部统一代付状态。
     */
    private String channelStatus;

    /**
     * 渠道侧响应码，保留原始语义，便于错误归因和渠道规则分析。
     */
    private String channelResponseCode;

    /**
     * 渠道侧响应描述，保留原始文本，返回商户前应根据产品规则做必要脱敏和标准化。
     */
    private String channelResponseMessage;

    /**
     * 渠道原始响应字段集合，用于审计、排错和后续补充解析，不建议直接暴露给商户。
     */
    private Map<String, String> rawResponse;
}
