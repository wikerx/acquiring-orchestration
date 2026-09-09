package com.scott.payment.component.mq.message;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BaseMqMessage
 * @date : 2026-05-28 09:28
 * @email : scott_x@163.com
 * @description : 基础mq说明协作组件，位于 公共组件库，封装该业务的本地校验、转换或运行时协作入口。
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

    /**
     * 链路追踪号，用于把生产者请求、RocketMQ 投递和消费者处理串联到同一条日志链路。
     */
    private String traceId;

    /**
     * MQ 消费重试次数。
     * <p>
     * 单位：次；格式：从 0 开始递增的整数；允许为空，生产者投递前会补齐为 0；非敏感字段。
     * 数据来源：首次生产消息时由平台生成，失败重投或补偿消息必须沿用原 traceId 并递增该字段。
     * </p>
     */
    private Integer retryCount;
}
