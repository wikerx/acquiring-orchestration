package com.scott.payment.component.mq.admin;

import lombok.Builder;
import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MqResourceCheckResult
 * @date : 2026-06-20 22:47
 * @email : scott_x@163.com
 * @description : 单个 RocketMQ 资源检查结果
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MqResourceCheckResult
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Mq Resource Check Result，位于 component-library/component-mq 的消息消费层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Getter
@Builder
public class MqResourceCheckResult {

    /**
     * 资源名称。
     */
    private final String resourceName;

    /**
     * 资源类型。
     */
    private final MqResourceType resourceType;

    /**
     * 是否已存在。
     */
    private final boolean exists;

    /**
     * 是否已创建。
     */
    private final boolean created;

    /**
     * 是否已更新。
     */
    private final boolean updated;

    /**
     * 结果摘要。
     */
    private final String message;
}
