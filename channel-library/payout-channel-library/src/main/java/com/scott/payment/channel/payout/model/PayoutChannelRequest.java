package com.scott.payment.channel.payout.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelRequest
 * @date : 2026-05-28 10:58
 * @email : scott_x@163.com
 * @description : 代付渠道请求模型
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 渠道管理Payout Channel 请求对象，位于 channel-library 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class PayoutChannelRequest implements Serializable {

    /**
     * 序列化版本号，用于保证渠道请求对象在服务间传输时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 代付渠道编码，用于路由到具体代付渠道适配器，例如银行通道、钱包通道或本地清算通道。
     */
    private String channelCode;

    /**
     * 商户代付订单号，来自开放接口入参，用于渠道请求与商户订单维度的关联。
     */
    private String merchantOrderNo;

    /**
     * 系统内部代付订单号，由 service-payout 生成，用于渠道调用、状态推进和对账。
     */
    private String payoutOrderNo;

    /**
     * 渠道扩展参数，用于承载不同代付渠道的差异化字段，核心通用字段应优先使用显式属性。
     */
    private Map<String, String> parameters;
}
