package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutSessionQueryCommandDTO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 查询 Hosted Checkout 会话内部命令。
 * @status : create
 */
@Data
public class PaymentCheckoutSessionQueryCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 不透明访问令牌摘要；内部接口不得接收或记录令牌明文。 */
    @NotBlank(message = "tokenHash is required")
    private String tokenHash;

    /** 可选付款页封面或主题标识，不参与身份校验。 */
    private String cover;
    /** 客户端 IP 摘要，用于访问审计和异常比对。 */
    private String clientIpHash;
    /** User-Agent 摘要，用于访问审计。 */
    private String userAgentHash;
    /** Origin 摘要，用于来源一致性比对。 */
    private String originHash;
    /** Referer 摘要，用于来源审计。 */
    private String refererHash;
    /** 前端设备标识摘要，不能单独作为身份凭据。 */
    private String deviceIdHash;
    /** 浏览器语言标识。 */
    private String language;
    /** 浏览器时区偏移。 */
    private String timezoneOffset;
    /** 当前调用链追踪号。 */
    private String traceId;
}
