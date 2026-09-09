package com.scott.payment.payout.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutTransactionStatusEnum
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 代付交易状态枚举，位于 代付服务，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
 * @status : create
 */
@Getter
public enum PayoutTransactionStatusEnum {

    /**
     * 代付服务已接收交易，后续结果以查询、回调或通知为准。
     */
    RECEIVED("RECEIVED");

    /**
     * 编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final String code;

    /**
     * 创建代付交易状态。
     *
     * @param code 对外和内部接口传递的状态编码
     */
    PayoutTransactionStatusEnum(String code) {
        this.code = code;
    }
}
