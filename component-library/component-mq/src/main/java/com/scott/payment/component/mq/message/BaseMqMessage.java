package com.scott.payment.component.mq.message;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BaseMqMessage
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 基础消息体模型
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BaseMqMessage
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Base Mq Message，位于 component-library/component-mq 的消息消费层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class BaseMqMessage implements Serializable {

    /**
     * 序列化版本号，用于保证 MQ 消息对象在生产、消费、重试和补偿场景下的兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 消息唯一标识，用于 MQ 幂等消费、日志追踪和问题排查。
     */
    private String messageId;

    /**
     * 消息创建时间，用于计算延迟、过期和消费耗时。
     */
    private LocalDateTime createdAt;
}
