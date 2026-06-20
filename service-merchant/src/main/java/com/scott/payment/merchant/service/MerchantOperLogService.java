package com.scott.payment.merchant.service;

import com.scott.payment.merchant.dto.SysOperLogRecordRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperLogService
 * @date : 2026-06-20 10:30
 * @email : scott_x@163.com
 * @description : 商户管理系统操作日志领域服务
 * @status : create
 */
public interface MerchantOperLogService {

    /**
     * 写入商户管理系统操作日志。
     *
     * @param request 操作日志写入请求
     */
    void recordOperLog(SysOperLogRecordRequest request);
}
