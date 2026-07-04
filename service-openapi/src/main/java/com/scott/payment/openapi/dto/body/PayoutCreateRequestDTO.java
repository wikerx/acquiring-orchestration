package com.scott.payment.openapi.dto.body;

import lombok.Data;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutCreateRequestDTO
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 代付创建请求数据传输对象
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutCreateRequestDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIPayout Create Request 数据传输对象，位于 service-openapi 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class PayoutCreateRequestDTO implements Serializable {

    /**
     * 序列化版本号，用于保证请求对象在服务间传输或缓存时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 商户代付订单号，由商户侧生成并保证唯一，用于代付创建幂等和后续订单查询。
     */
    @NotBlank(message = "merchantOrderNo can not be blank")
    private String merchantOrderNo;

    /**
     * 代付币种，使用 ISO 4217 三位大写币种代码，例如 USD、EUR、CNY。
     */
    @NotBlank(message = "currency can not be blank")
    private String currency;

    /**
     * 代付金额，单位由业务约定统一控制，当前基础接口使用最小币种单位保存，必须大于 0。
     */
    @NotNull(message = "amount can not be null")
    @Positive(message = "amount must be positive")
    private Long amount;

    /**
     * 收款方账户号，代表代付资金最终入账账户，真实生产环境需要结合渠道规则做格式和实名校验。
     */
    @NotBlank(message = "receiverAccountNo can not be blank")
    private String receiverAccountNo;

}
