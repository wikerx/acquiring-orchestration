package com.scott.payment.admin.mq;

import com.scott.payment.admin.converter.OperLogMessageConverter;
import com.scott.payment.admin.dto.SysOperLogRecordRequest;
import com.scott.payment.admin.service.AdminOperLogService;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.component.mq.properties.OperationLogMqProperties;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperationLogConsumer
 * @date : 2026-06-20 01:54
 * @email : scott_x@163.com
 * @description : service-admin 操作日志 MQ 消费者
 * @status : create
 *
 * <p>负责消费后台管理系统操作日志消息，并在本地做幂等控制后落库到 sys_oper_log。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "acquiring.operation-log.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqTopic.ADMIN_OPERATION_LOG,
        consumerGroup = AdminOperationLogMqConstants.ADMIN_OPERATION_LOG_CONSUMER_GROUP,
        messageModel = MessageModel.CLUSTERING
)
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperationLogConsumer
 * @date : 2026-06-20 01:54
 * @email : scott_x@163.com
 * @description : AdminOperationLogConsumer 消息消费组件，用于解析 MQ 消息、绑定链路上下文并触发后续处理，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminOperationLogConsumer implements RocketMQListener<String> {

    /**
     * 操作日志领域服务。
     */
    private final AdminOperLogService adminOperLogService;

    /**
     * Redis 幂等服务。
     */
    private final IdempotentService idempotentService;

    /**
     * 操作日志 MQ 配置。
     */
    private final OperationLogMqProperties properties;
    /**
     * 操作日志 MQ 消息转换器。
     */
    private final OperLogMessageConverter operLogMessageConverter;

    /**
     * 创建后台操作日志消费者。
     *
     * @param adminOperLogService 操作日志领域服务
     * @param idempotentService   Redis 幂等服务
     * @param properties          操作日志 MQ 配置
     * @param operLogMessageConverter 操作日志 MQ 消息转换器
     */
    public AdminOperationLogConsumer(AdminOperLogService adminOperLogService,
                                     IdempotentService idempotentService,
                                     OperationLogMqProperties properties,
                                     OperLogMessageConverter operLogMessageConverter) {
        this.adminOperLogService = adminOperLogService;
        this.idempotentService = idempotentService;
        this.properties = properties;
        this.operLogMessageConverter = operLogMessageConverter;
    }

    /**
     * 消费后台操作日志消息。
     *
     * @param payload 操作日志消息 JSON 字符串
     */
    @Override
    public void onMessage(String payload) {
        OperationLogMessage message = JsonUtils.parseObject(payload, OperationLogMessage.class);
        if (message == null) {
            log.warn("event=ADMIN_OPERATION_LOG_CONSUME_SKIP reason=messageInvalid payloadLength: {}",
                    payload == null ? 0 : payload.length());
            return;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            String idempotentKey = "operation-log:consume:admin:" + message.getIdempotentKey();
            if (!idempotentService.acquire(idempotentKey, properties.getConsumeIdempotentTtlSeconds())) {
                log.info("event=ADMIN_OPERATION_LOG_DUPLICATE messageId: {} retryCount: {} idempotentKey: {}",
                        message.getMessageId(),
                        message.getRetryCount(),
                        message.getIdempotentKey());
                return;
            }
            SysOperLogRecordRequest request = operLogMessageConverter.toRecordRequest(message);
            adminOperLogService.recordOperLog(request);
            log.info("event=ADMIN_OPERATION_LOG_CONSUMED messageId: {} retryCount: {} operationModule: {} operationType: {}",
                    message.getMessageId(),
                    message.getRetryCount(),
                    message.getOperationModule(),
                    message.getOperationType());
        } finally {
            TraceContext.clear();
        }
    }
}
