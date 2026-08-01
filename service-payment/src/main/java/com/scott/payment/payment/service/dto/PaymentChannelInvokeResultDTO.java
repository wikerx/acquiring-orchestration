package com.scott.payment.payment.service.dto;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelInvokeResultDTO
 * @date : 2026-07-14 19:20
 * @email : scott_x@163.com
 * @description : 收单渠道调用结果 DTO，位于 service-payment 服务 DTO 层，承载渠道请求、同步响应、异常和耗时，供交易审计日志落库使用。
 * @status : create
 */
@Data
public class PaymentChannelInvokeResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台渠道请求 ID，关联 transaction_channel_request.request_id。
     */
    private String requestId;

    /**
     * 渠道统一请求对象，落库前必须脱敏。
     */
    private ChannelPaymentRequest channelRequest;

    /**
     * 渠道统一响应对象，落库前必须脱敏。
     */
    private ChannelPaymentResponse channelResponse;

    /**
     * 请求发起时间。
     */
    private LocalDateTime requestStartTime;

    /**
     * 响应或异常产生时间。
     */
    private LocalDateTime responseTime;

    /**
     * 渠道调用耗时，单位毫秒。
     */
    private Integer durationMillis;

    /**
     * 请求状态：SUCCESS、FAILED、TIMEOUT。
     */
    private String requestStatus;

    /**
     * HTTP 方法；当前 service-payment 只能按交易类型推导。
     */
    private String httpMethod;

    /**
     * 渠道请求场景，如 AUTHORIZE、CAPTURE、REFUND、VOID、RETRIEVE。
     */
    private String requestScene;

    /**
     * 脱敏后的请求 URL，当前以渠道配置地址和渠道交易标识重建。
     */
    private String requestUrlMasked;

    /**
     * 异常类型，渠道超时、网络错误或响应解析失败时填写。
     */
    private String exceptionType;

    /**
     * 异常摘要，禁止包含完整卡号、CVV、密钥或 Authorization 头。
     */
    private String exceptionMessage;

    /**
     * 渠道结果是否不确定。仅当请求可能已经到达渠道但平台无法取得可信结果时为 true，
     * 此时资金动作必须保留为处理中并等待查询或回调勾兑；发送前校验、配置等确定性失败保持 false。
     */
    private boolean outcomeUncertain;

    /**
     * 创建成功的渠道调用结果。
     *
     * @param channelRequest  渠道统一请求
     * @param channelResponse 渠道统一响应
     * @return 渠道调用结果
     */
    public static PaymentChannelInvokeResultDTO success(ChannelPaymentRequest channelRequest,
                                                        ChannelPaymentResponse channelResponse) {
        PaymentChannelInvokeResultDTO resultDTO = new PaymentChannelInvokeResultDTO();
        resultDTO.setChannelRequest(channelRequest);
        resultDTO.setChannelResponse(channelResponse);
        resultDTO.setRequestStatus("SUCCESS");
        return resultDTO;
    }
}
