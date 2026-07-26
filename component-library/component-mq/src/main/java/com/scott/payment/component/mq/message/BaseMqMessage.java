package com.scott.payment.component.mq.message;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;


@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BaseMqMessage
 * @date : 2026-05-28 09:28
 * @email : scott_x@163.com
 * @description : BaseMqMessage Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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

    /**
     * 链路追踪号，用于把生产者请求、RocketMQ 投递和消费者处理串联到同一条日志链路。
     */
    private String traceId;
}
