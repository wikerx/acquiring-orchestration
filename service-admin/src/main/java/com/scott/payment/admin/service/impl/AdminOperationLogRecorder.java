package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.SysOperLogRecordRequest;
import com.scott.payment.admin.service.AdminOperLogService;
import com.scott.payment.component.web.operation.dto.OperationLogRecord;
import com.scott.payment.component.web.operation.service.OperationLogRecorder;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperationLogRecorder
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : service-admin 本地操作日志落库记录器
 * @status : create
 */
@Component
public class AdminOperationLogRecorder implements OperationLogRecorder {

    /**
     * 管理后台操作日志服务。
     */
    private final AdminOperLogService operLogService;

    /**
     * 创建 service-admin 本地操作日志落库记录器。
     *
     * @param operLogService 管理后台操作日志服务
     */
    public AdminOperationLogRecorder(AdminOperLogService operLogService) {
        this.operLogService = operLogService;
    }

    /**
     * 将公共操作日志采集记录转换为 service-admin 操作日志写入请求并落库。
     *
     * @param record 操作日志采集记录
     */
    @Override
    public void record(OperationLogRecord record) {
        if (record == null) {
            return;
        }
        SysOperLogRecordRequest request = new SysOperLogRecordRequest();
        request.setTraceId(record.getTraceId());
        request.setRequestId(record.getRequestId());
        request.setMerchantId(record.getMerchantId());
        request.setModuleName(record.getModuleName());
        request.setBusinessType(record.getBusinessType());
        request.setMethodName(record.getMethodName());
        request.setRequestMethod(record.getRequestMethod());
        request.setOperatorType(record.getOperatorType());
        request.setOperatorId(record.getOperatorId());
        request.setOperatorName(record.getOperatorName());
        request.setOperUrl(record.getOperUrl());
        request.setOperIp(record.getOperIp());
        request.setOperLocation(record.getOperLocation());
        request.setRequestParam(record.getRequestParam());
        request.setResponseResult(record.getResponseResult());
        request.setCostTime(record.getCostTime());
        request.setStatus(record.getStatus());
        request.setErrorCode(record.getErrorCode());
        request.setErrorMsg(record.getErrorMsg());
        operLogService.recordOperLog(request);
    }
}
