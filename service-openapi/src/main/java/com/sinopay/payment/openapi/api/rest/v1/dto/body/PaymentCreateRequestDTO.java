package com.sinopay.payment.openapi.api.rest.v1.dto.body;

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

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "merchantOrderNo can not be blank")
    private String merchantOrderNo;
    @NotBlank(message = "currency can not be blank")
    private String currency;
    @NotNull(message = "amount can not be null")
    @Positive(message = "amount must be positive")
    private Long amount;

}
