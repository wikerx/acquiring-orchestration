package com.scott.payment.channel.payment.scottchannela.model.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * ScottChannelA 直连代收请求。
 *
 * <p>{@code paymentData} 的结构由支付方式决定，因此保留为开放对象；其余稳定字段使用强类型，
 * 避免渠道字段名称散落在请求组装代码中。</p>
 **/
@Data
public final class PayinRequestPayload {

    /**
     * 平台系统交易号，同时作为渠道侧商户订单号和幂等键，必填。
     **/
    private String merchantOrderNo;

    /**
     * 交易金额，必填，发送前固定校验为两位小数。
     **/
    private BigDecimal amount;

    /**
     * ISO 4217 大写币种代码，必填。
     **/
    private String currency;

    /**
     * ScottChannelA 支付方式编码，必填。
     **/
    private String paymentMethod;

    /**
     * 平台接收渠道异步通知的地址，可选。
     **/
    private String notifyUrl;

    /**
     * 浏览器支付完成后的返回地址，可选。
     **/
    private String returnUrl;

    /**
     * 付款方 IP，可选。
     **/
    private String clientIp;

    /**
     * 平台请求时间，ISO 本地日期时间格式，必填。
     **/
    private String requestTime;

    /**
     * 渠道沙箱模拟结果，仅测试环境使用。
     **/
    private String simulationResult;

    /**
     * 支付方式专有参数，可能包含卡号等敏感数据，禁止写入未脱敏日志。
     **/
    private Map<String, Object> paymentData;
}
