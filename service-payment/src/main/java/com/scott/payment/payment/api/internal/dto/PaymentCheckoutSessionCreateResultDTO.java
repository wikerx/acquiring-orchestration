package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutSessionCreateResultDTO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 创建 Hosted Checkout 会话内部结果。
 * @status : create
 */
@Data
public class PaymentCheckoutSessionCreateResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新建或幂等命中的 Hosted Checkout 会话号。 */
    private String checkoutSessionId;
    /** 服务端访问令牌记录号，不是付款人持有的不透明令牌。 */
    private String checkoutTokenId;
    /** 付款人访问收银台的受控 URL。 */
    private String checkoutUrl;
    /** 当前会话状态。 */
    private String checkoutStatus;
    /** 会话和访问令牌的失效时间。 */
    private LocalDateTime expireTime;
    /** 是否命中相同商户请求的持久化幂等结果。 */
    private Boolean idempotentHit;
}
