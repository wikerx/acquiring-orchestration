package com.scott.payment.component.web.operation.service;

import com.scott.payment.component.web.operation.dto.OperationLogRecord;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogPublisher
 * @date : 2026-06-20 01:25
 * @email : scott_x@163.com
 * @description : 管理类系统操作日志发布器接口
 * @status : create
 *
 * <p>用于把 AOP 采集完成且已脱敏的操作日志交给具体下游实现处理。
 * 当前默认实现会通过 RocketMQ 异步发送，后续也可扩展为事件总线或其他消息中间件。</p>
 */
public interface OperationLogPublisher {

    /**
     * 发布管理类系统操作日志。
     *
     * @param record 已完成脱敏和长度控制的操作日志记录
     */
    void publish(OperationLogRecord record);
}
