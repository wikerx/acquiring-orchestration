package com.scott.payment.merchant.service.impl;

import com.scott.payment.merchant.dto.SysOperLogRecordRequest;
import com.scott.payment.merchant.entity.SysOperLogDO;
import com.scott.payment.merchant.mapper.SysOperLogMapper;
import com.scott.payment.merchant.service.MerchantOperLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperLogServiceImpl
 * @date : 2026-06-20 10:31
 * @email : scott_x@163.com
 * @description : 商户管理系统操作日志领域服务实现
 * @status : create
 *
 * <p>负责消费商户管理端操作日志消息并按商户侧本地审计表结构完成落库。</p>
 */
@Service
public class MerchantOperLogServiceImpl implements MerchantOperLogService {

    /**
     * 默认商户操作人类型。
     */
    private static final int DEFAULT_OPERATOR_TYPE = 2;

    /**
     * 默认成功状态。
     */
    private static final int SUCCESS_STATUS = 1;

    /**
     * 商户操作日志 Mapper。
     */
    private final SysOperLogMapper operLogMapper;

    /**
     * 创建商户操作日志服务实现。
     *
     * @param operLogMapper 商户操作日志 Mapper
     */
    public MerchantOperLogServiceImpl(SysOperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    /**
     * 写入商户管理系统操作日志。
     *
     * @param request 操作日志写入请求
     */
    @Override
    public void recordOperLog(SysOperLogRecordRequest request) {
        if (request == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        SysOperLogDO entity = new SysOperLogDO();
        entity.setTraceId(request.getTraceId());
        entity.setRequestId(request.getRequestId());
        entity.setMessageId(request.getMessageId());
        entity.setIdempotentKey(request.getIdempotentKey());
        entity.setSystemCode(request.getSystemCode());
        entity.setMerchantId(request.getMerchantId());
        entity.setModuleName(request.getModuleName());
        entity.setOperationName(request.getOperationName());
        entity.setBusinessType(request.getBusinessType());
        entity.setMethodName(request.getMethodName());
        entity.setRequestMethod(request.getRequestMethod());
        entity.setOperatorType(defaultIfNull(request.getOperatorType(), DEFAULT_OPERATOR_TYPE));
        entity.setOperatorId(request.getOperatorId());
        entity.setOperatorName(request.getOperatorName());
        entity.setOperUrl(request.getOperUrl());
        entity.setOperIp(request.getOperIp());
        entity.setOperLocation(request.getOperLocation());
        entity.setStoreId(request.getStoreId());
        entity.setUserAgent(request.getUserAgent());
        entity.setRequestParam(request.getRequestParam());
        entity.setResponseResult(request.getResponseResult());
        entity.setCostTime(request.getCostTime());
        entity.setStatus(defaultIfNull(request.getStatus(), SUCCESS_STATUS));
        entity.setErrorCode(request.getErrorCode());
        entity.setErrorMsg(request.getErrorMsg());
        entity.setOperatedAt(now);
        entity.setCreatedAt(now);
        operLogMapper.insert(entity);
    }

    /**
     * 获取非空整数。
     *
     * @param value 入参值
     * @param defaultValue 默认值
     * @return 非空整数
     */
    private Integer defaultIfNull(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }
}
