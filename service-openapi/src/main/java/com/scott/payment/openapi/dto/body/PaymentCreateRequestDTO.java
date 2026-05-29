package com.scott.payment.openapi.dto.body;

import lombok.Data;

import java.io.Serializable;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateRequestDTO
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 收单支付创建请求数据传输对象
 * @status : create
 */
@Data
public class PaymentCreateRequestDTO implements Serializable {

    /**
     * 序列化版本号，用于保证请求对象在服务间传输或缓存时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 商户订单号，由商户侧生成并保证唯一，用于幂等控制、交易查询和后续退款/撤销关联。
     */
    @NotBlank(message = "merchantOrderNo can not be blank")
    private String merchantOrderNo;

    /**
     * 交易币种，使用 ISO 4217 三位大写币种代码，例如 USD、EUR、CNY。
     */
    @NotBlank(message = "currency can not be blank")
    private String currency;

    /**
     * 交易金额，单位由业务约定统一控制，当前基础接口使用最小币种单位保存，必须大于 0。
     */
    @NotNull(message = "amount can not be null")
    @Positive(message = "amount must be positive")
    private Long amount;

}
