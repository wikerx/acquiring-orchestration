package com.scott.payment.payout.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutTransactionStatusEnum
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 代付交易状态枚举，位于 service-payout 领域状态层，用于收敛平台代付状态取值，避免核心链路散落状态字符串。
 * @status : create
 */
@Getter
public enum PayoutTransactionStatusEnum {

    /**
     * 代付服务已接收交易，后续结果以查询、回调或通知为准。
     */
    RECEIVED("RECEIVED");

    /**
     * code 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
