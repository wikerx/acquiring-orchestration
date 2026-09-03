package com.scott.payment.merchant.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.SysOperLogDTO;
import com.scott.payment.merchant.dto.SysOperLogQueryRequest;
import com.scott.payment.merchant.dto.SysOperLogRecordRequest;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperLogService
 * @date : 2026-06-20 10:46
 * @email : scott_x@163.com
 * @description : 商户oper日志服务契约，位于 商户后台服务，声明该业务能力的输入、返回结果和异常边界，由实现类保持一致。
 * @status : create
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
