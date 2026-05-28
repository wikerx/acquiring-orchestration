package com.sinopay.payment.openapi.dto.body;

import lombok.Data;

import java.io.Serializable;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutCreateRequestDTO
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 代付创建请求数据传输对象
 * @status : create
 */
@Data
public class PayoutCreateRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "merchantOrderNo can not be blank")
    private String merchantOrderNo;
    @NotBlank(message = "currency can not be blank")
    private String currency;
    @NotNull(message = "amount can not be null")
    @Positive(message = "amount must be positive")
    private Long amount;
    @NotBlank(message = "receiverAccountNo can not be blank")
    private String receiverAccountNo;

}
