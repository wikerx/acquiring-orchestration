package com.scott.payment.component.web.operation.service;

import com.scott.payment.component.web.operation.dto.OperationLogRecord;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogRecorder
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理类系统操作日志记录器接口
 * @status : create
 */
public interface OperationLogRecorder {

    /**
     * 记录管理类系统操作日志。
     *
     * @param record 操作日志采集记录
     */
    void record(OperationLogRecord record);
}
