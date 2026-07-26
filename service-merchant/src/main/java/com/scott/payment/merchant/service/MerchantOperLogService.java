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
 * @description : MerchantOperLogService 服务契约，用于声明业务能力、调用边界和返回结果约束，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
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
