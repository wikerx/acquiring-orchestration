package com.scott.payment.merchant.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.SysOperLogDTO;
import com.scott.payment.merchant.dto.SysOperLogQueryRequest;
import com.scott.payment.merchant.dto.SysOperLogRecordRequest;

/**
 * 商户管理系统操作日志领域服务，负责写入审计日志并按商户边界查询已脱敏记录。
 */
public interface MerchantOperLogService {

    /**
     * 写入商户管理系统操作日志。
     *
     * @param request 操作日志写入请求
     */
    void recordOperLog(SysOperLogRecordRequest request);

    /**
     * 分页查询当前商户可见的操作日志。
     *
     * @param request 查询条件，merchantId 必须由服务端上下文补齐
     * @return 商户操作日志分页结果
     */
    PageResult<SysOperLogDTO> pageOperLogs(SysOperLogQueryRequest request);
}
