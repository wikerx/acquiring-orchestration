package com.scott.payment.component.mq.producer;

import com.scott.payment.component.mq.message.BaseMqMessage;

import java.time.Instant;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MqProducer
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 消息发送服务接口
 * @status : create
 */
public interface MqProducer {

    /**
     * 发送普通消息。
     *
     * @param topic   RocketMQ Topic
     * @param tag     RocketMQ Tag，用于消费者过滤业务类型
     * @param message 基础消息体
     */
    void send(String topic, String tag, BaseMqMessage message);

    /**
     * 按业务分组键发送顺序消息。
     *
     * <p>同一个分组键的消息固定选择同一 RocketMQ 队列，消费者仍需使用数据库幂等和状态机
     * 处理重复投递，不能把队列顺序当作最终一致性保证。</p>
     *
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag，可为空
     * @param message 不含敏感明文的消息体
     * @param messageGroup 非空业务分组键，例如交易号
     */
    default void sendOrderly(String topic,
                             String tag,
                             BaseMqMessage message,
                             String messageGroup) {
        throw new UnsupportedOperationException("orderly mq delivery is not supported");
    }

    /**
     * 按绝对时间发送 RocketMQ 5.x 定时消息。
     *
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag，可为空
     * @param message 不含敏感明文的消息体
     * @param deliverAt 最早投递时间
     */
    default void sendAt(String topic, String tag, BaseMqMessage message, Instant deliverAt) {
        throw new UnsupportedOperationException("scheduled mq delivery is not supported");
    }

    /**
     * 发送 Outbox 中已经冻结的 JSON 消息快照。
     *
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag，可为空
     * @param messageId 消息唯一编号
     * @param traceId 链路追踪号，可为空
     * @param retryCount Outbox 投递重试次数
     * @param payloadJson 已脱敏 JSON 消息快照
     */
    default void sendSerialized(String topic,
                                String tag,
                                String messageId,
                                String traceId,
                                int retryCount,
                                String payloadJson) {
        throw new UnsupportedOperationException("serialized mq delivery is not supported");
    }

    /**
     * 按业务分组键发送 Outbox 中已经冻结的 JSON 消息快照。
     *
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag，可为空
     * @param messageId 消息唯一编号
     * @param traceId 链路追踪号，可为空
     * @param retryCount Outbox 投递重试次数
     * @param payloadJson 已脱敏 JSON 消息快照，不得重新序列化
     * @param messageGroup 非空业务分组键
     */
    default void sendSerializedOrderly(String topic,
                                       String tag,
                                       String messageId,
                                       String traceId,
                                       int retryCount,
                                       String payloadJson,
                                       String messageGroup) {
        throw new UnsupportedOperationException("serialized orderly mq delivery is not supported");
    }

    /**
     * 按绝对时间发送 Outbox 中已经冻结的 JSON 消息快照。
     *
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag，可为空
     * @param messageId 消息唯一编号
     * @param traceId 链路追踪号，可为空
     * @param retryCount Outbox 投递重试次数
     * @param payloadJson 已脱敏 JSON 消息快照，不得重新序列化
     * @param deliverAt 最早投递时间
     */
    default void sendSerializedAt(String topic,
                                  String tag,
                                  String messageId,
                                  String traceId,
                                  int retryCount,
                                  String payloadJson,
                                  Instant deliverAt) {
        throw new UnsupportedOperationException("serialized scheduled mq delivery is not supported");
    }
}
