package com.scott.payment.openapi.vo.payment;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateVO
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 收单支付创建响应视图对象
 * @status : create
 */
@Data
public class PaymentCreateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String merchantOrderNo;
    private String currency;
    private Long amount;
}
