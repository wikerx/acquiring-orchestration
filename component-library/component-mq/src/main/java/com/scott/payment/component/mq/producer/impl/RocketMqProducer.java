package com.scott.payment.component.mq.producer.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.producer.MqProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;


@Slf4j
@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RocketMqProducer
 * @date : 2026-05-31 21:52
 * @email : scott_x@163.com
 * @description : Rocket MQ Producer 消息投递组件，位于 公共组件库，补齐消息标识、traceId、重试次数和业务载荷后发送 MQ。
 * @status : create
 */
public class RocketMqProducer implements MqProducer {

    /**
     * RocketMQ Spring 模板。
     */
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    /**
     * MQ 消息头中的重试次数字段，生产者补齐后供消费者日志和排障使用。
     */
    private static final String RETRY_COUNT_HEADER = "retryCount";

    /**
     * MQ 消息头中的消息唯一标识字段，与消息体 messageId 保持一致。
     */
    private static final String MESSAGE_ID_HEADER = "messageId";

    /**
     * 创建 RocketMQ 消息发送服务。
     *
     * @param rocketMQTemplateProvider RocketMQ Spring 模板提供器
     */
    public RocketMqProducer(ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        this.rocketMQTemplateProvider = rocketMQTemplateProvider;
    }

    /**
     * 发送普通同步消息。
     *
     * @param topic   RocketMQ Topic，不能为空
     * @param tag     RocketMQ Tag，可为空
     * @param message 基础消息体，必须是不含敏感明文的业务消息对象
     */
    @Override
    public void send(String topic, String tag, BaseMqMessage message) {
        Objects.requireNonNull(message, "mq message can not be null");
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("rocketmq topic can not be blank");
        }
        fillMessageMetadata(message);
        RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        if (rocketMQTemplate == null) {
            throw new IllegalStateException("RocketMQTemplate is not ready");
        }
        String destination = StringUtils.hasText(tag) ? topic + ":" + tag : topic;
        SendResult sendResult = rocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(JsonUtils.toJsonString(message))
                .setHeader(TraceContext.TRACE_ID_HEADER, message.getTraceId())
                .setHeader(RETRY_COUNT_HEADER, message.getRetryCount())
                .setHeader(MESSAGE_ID_HEADER, message.getMessageId())
                .build());
        requireSendOk(sendResult, destination, message.getMessageId());
    }

    /**
     * 将相同业务分组的消息发送到同一队列，避免同交易事件主动乱序。
     *
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag，可为空
     * @param message 不含敏感明文的消息体
     * @param messageGroup 非空业务分组键
     */
    @Override
    public void sendOrderly(String topic,
                            String tag,
                            BaseMqMessage message,
                            String messageGroup) {
        Objects.requireNonNull(message, "mq message can not be null");
        if (!StringUtils.hasText(topic) || !StringUtils.hasText(messageGroup)) {
            throw new IllegalArgumentException("rocketmq topic and message group can not be blank");
        }
        fillMessageMetadata(message);
        RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        if (rocketMQTemplate == null) {
            throw new IllegalStateException("RocketMQTemplate is not ready");
        }
        String destination = StringUtils.hasText(tag) ? topic + ":" + tag : topic;
        SendResult sendResult = rocketMQTemplate.syncSendOrderly(
                destination,
                MessageBuilder.withPayload(JsonUtils.toJsonString(message))
                        .setHeader(TraceContext.TRACE_ID_HEADER, message.getTraceId())
                        .setHeader(RETRY_COUNT_HEADER, message.getRetryCount())
                        .setHeader(MESSAGE_ID_HEADER, message.getMessageId())
                        .build(),
                messageGroup
        );
        requireSendOk(sendResult, destination, message.getMessageId());
    }

    /**
     * 使用 RocketMQ 5.x 绝对时间投递消息；已到期消息立即发送。
     *
     * @param topic RocketMQ Topic，不能为空
     * @param tag RocketMQ Tag，可为空
     * @param message 基础消息体
     * @param deliverAt 最早投递时间，不能为空
     */
    @Override
    public void sendAt(String topic, String tag, BaseMqMessage message, Instant deliverAt) {
        Objects.requireNonNull(message, "mq message can not be null");
        Objects.requireNonNull(deliverAt, "mq deliver time can not be null");
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("rocketmq topic can not be blank");
        }
        fillMessageMetadata(message);
        RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        if (rocketMQTemplate == null) {
            throw new IllegalStateException("RocketMQTemplate is not ready");
        }
        String destination = StringUtils.hasText(tag) ? topic + ":" + tag : topic;
        org.springframework.messaging.Message<String> rocketMessage = MessageBuilder
                .withPayload(JsonUtils.toJsonString(message))
                .setHeader(TraceContext.TRACE_ID_HEADER, message.getTraceId())
                .setHeader(RETRY_COUNT_HEADER, message.getRetryCount())
                .setHeader(MESSAGE_ID_HEADER, message.getMessageId())
                .build();
        long deliverTimestamp = deliverAt.toEpochMilli();
        if (deliverTimestamp <= System.currentTimeMillis()) {
            requireSendOk(rocketMQTemplate.syncSend(destination, rocketMessage),
                    destination, message.getMessageId());
            return;
        }
        requireSendOk(rocketMQTemplate.syncSendDeliverTimeMills(destination, rocketMessage, deliverTimestamp),
                destination, message.getMessageId());
    }

    /**
     * 发送 Outbox 冻结的 JSON 快照；生产者不可用时抛出异常，使记录保留在待重试状态。
     *
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag，可为空
     * @param messageId 消息唯一编号
     * @param traceId 链路追踪号，可为空
     * @param retryCount Outbox 投递重试次数
     * @param payloadJson 已脱敏 JSON 消息快照
     */
    @Override
    public void sendSerialized(String topic,
                               String tag,
                               String messageId,
                               String traceId,
                               int retryCount,
                               String payloadJson) {
        validateSerializedMessage(topic, messageId, payloadJson);
        RocketMQTemplate rocketMQTemplate = requireRocketMqTemplate();
        String destination = destination(topic, tag);
        Message<String> rocketMessage = buildSerializedMessage(
                messageId, traceId, retryCount, payloadJson);
        requireSendOk(rocketMQTemplate.syncSend(destination, rocketMessage), destination, messageId);
    }

    /**
     * 顺序发送 Outbox 冻结 JSON；载荷不经过反序列化或再次序列化。
     *
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag，可为空
     * @param messageId 消息唯一编号
     * @param traceId 链路追踪号，可为空
     * @param retryCount Outbox 投递重试次数
     * @param payloadJson 已脱敏 JSON 消息快照
     * @param messageGroup 非空业务分组键
     */
    @Override
    public void sendSerializedOrderly(String topic,
                                      String tag,
                                      String messageId,
                                      String traceId,
                                      int retryCount,
                                      String payloadJson,
                                      String messageGroup) {
        validateSerializedMessage(topic, messageId, payloadJson);
        if (!StringUtils.hasText(messageGroup)) {
            throw new IllegalArgumentException("serialized mq message group can not be blank");
        }
        RocketMQTemplate rocketMQTemplate = requireRocketMqTemplate();
        String destination = destination(topic, tag);
        Message<String> rocketMessage = buildSerializedMessage(
                messageId, traceId, retryCount, payloadJson);
        requireSendOk(rocketMQTemplate.syncSendOrderly(destination, rocketMessage, messageGroup),
                destination, messageId);
    }

    /**
     * 使用 RocketMQ 5.x 绝对时间发送 Outbox 冻结 JSON；已到期消息立即投递。
     *
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag，可为空
     * @param messageId 消息唯一编号
     * @param traceId 链路追踪号，可为空
     * @param retryCount Outbox 投递重试次数
     * @param payloadJson 已脱敏 JSON 消息快照
     * @param deliverAt 最早投递时间
     */
    @Override
    public void sendSerializedAt(String topic,
                                 String tag,
                                 String messageId,
                                 String traceId,
                                 int retryCount,
                                 String payloadJson,
                                 Instant deliverAt) {
        validateSerializedMessage(topic, messageId, payloadJson);
        Objects.requireNonNull(deliverAt, "mq deliver time can not be null");
        RocketMQTemplate rocketMQTemplate = requireRocketMqTemplate();
        String destination = destination(topic, tag);
        Message<String> rocketMessage = buildSerializedMessage(
                messageId, traceId, retryCount, payloadJson);
        long deliverTimestamp = deliverAt.toEpochMilli();
        SendResult sendResult = deliverTimestamp <= System.currentTimeMillis()
                ? rocketMQTemplate.syncSend(destination, rocketMessage)
                : rocketMQTemplate.syncSendDeliverTimeMills(destination, rocketMessage, deliverTimestamp);
        requireSendOk(sendResult, destination, messageId);
    }

    /** 校验冻结 JSON 投递所需的稳定路由、消息号和载荷。 */
    private void validateSerializedMessage(String topic, String messageId, String payloadJson) {
        if (!StringUtils.hasText(topic) || !StringUtils.hasText(messageId)
                || !StringUtils.hasText(payloadJson)) {
            throw new IllegalArgumentException("serialized mq delivery metadata can not be blank");
        }
    }

    /** 获取当前 RocketMQ 模板；未就绪时保留 Outbox 待重试事实。 */
    private RocketMQTemplate requireRocketMqTemplate() {
        RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        if (rocketMQTemplate == null) {
            throw new IllegalStateException("RocketMQTemplate is not ready");
        }
        return rocketMQTemplate;
    }

    /** 生成 RocketMQ Spring 使用的 {@code topic[:tag]} 目标地址。 */
    private String destination(String topic, String tag) {
        return StringUtils.hasText(tag) ? topic + ":" + tag : topic;
    }

    /** 构建不改写 payload 的冻结 JSON 消息，并补齐可追踪 Header。 */
    private Message<String> buildSerializedMessage(String messageId,
                                                   String traceId,
                                                   int retryCount,
                                                   String payloadJson) {
        MessageBuilder<String> builder = MessageBuilder.withPayload(payloadJson)
                .setHeader(RETRY_COUNT_HEADER, Math.max(retryCount, 0))
                .setHeader(MESSAGE_ID_HEADER, messageId);
        if (StringUtils.hasText(traceId)) {
            builder.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        }
        return builder.build();
    }

    /**
     * 校验同步发送结果，任何非 {@code SEND_OK} 结果均交给 Outbox 或上层重试。
     *
     * @param sendResult RocketMQ 同步发送结果
     * @param destination Topic 与 Tag 组成的目标地址
     * @param messageId 平台消息唯一编号
     */
    private void requireSendOk(SendResult sendResult, String destination, String messageId) {
        if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
            String sendStatus = sendResult == null ? "NULL" : sendResult.getSendStatus().name();
            throw new IllegalStateException("RocketMQ send failed, destination=" + destination
                    + ", messageId=" + messageId + ", sendStatus=" + sendStatus);
        }
    }

    /**
     * 补齐消息元数据。
     *
     * @param message 基础消息体
     */
    private void fillMessageMetadata(BaseMqMessage message) {
        if (!StringUtils.hasText(message.getMessageId())) {
            message.setMessageId(UUID.randomUUID().toString());
        }
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(LocalDateTime.now());
        }
        if (!StringUtils.hasText(message.getTraceId())) {
            message.setTraceId(TraceContext.getOrCreateTraceId());
        }
        if (message.getRetryCount() == null || message.getRetryCount() < 0) {
            message.setRetryCount(0);
        }
    }
}
