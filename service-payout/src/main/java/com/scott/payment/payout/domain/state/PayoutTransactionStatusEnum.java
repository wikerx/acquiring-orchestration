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
     * code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
