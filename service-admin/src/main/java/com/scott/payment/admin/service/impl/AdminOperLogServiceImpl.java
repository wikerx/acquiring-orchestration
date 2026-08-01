package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.converter.OperLogConverter;
import com.scott.payment.admin.dto.SysOperLogDTO;
import com.scott.payment.admin.dto.SysOperLogQueryRequest;
import com.scott.payment.admin.dto.SysOperLogRecordRequest;
import com.scott.payment.admin.entity.SysOperLogDO;
import com.scott.payment.admin.mapper.SysOperLogMapper;
import com.scott.payment.admin.service.AdminOperLogService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperLogServiceImpl
 * @date : 2026-06-19 21:55
 * @email : scott_x@163.com
 * @description : 管理后台操作日志领域服务实现
 * @status : create
 *
 * <p>负责后台操作日志写入、默认字段补齐和审计分页查询，不承担控制器协议适配逻辑。</p>
 */
@Service
public class AdminOperLogServiceImpl implements AdminOperLogService {

    /**
     * 默认后台用户操作人类型。
     */
    private static final int DEFAULT_OPERATOR_TYPE = 1;

    /**
     * 默认成功状态。
     */
    private static final int SUCCESS_STATUS = 1;

    /**
     * 操作日志 Mapper。
     */
    private final SysOperLogMapper operLogMapper;

    /**
     * 操作日志对象转换器。
     */
    private final OperLogConverter operLogConverter;

    /**
     * 创建操作日志服务实现。
     *
     * @param operLogMapper 操作日志 Mapper
     * @param operLogConverter 操作日志对象转换器
     */
    public AdminOperLogServiceImpl(SysOperLogMapper operLogMapper, OperLogConverter operLogConverter) {
        this.operLogMapper = operLogMapper;
        this.operLogConverter = operLogConverter;
    }

    /**
     * 写入后台操作日志。
     *
     * @param request 操作日志写入请求
     */
    @Override
    public void recordOperLog(SysOperLogRecordRequest request) {
        if (request == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime operatedAt = request.getOperatedAt() == null ? now : request.getOperatedAt();
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
        entity.setOperatedAt(operatedAt);
        entity.setCreatedAt(now);
        try {
            operLogMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            if (!StringUtils.hasText(request.getIdempotentKey())) {
                throw exception;
            }
            // MQ 重投可能绕过 Redis 快速层；数据库唯一键命中即表示该审计日志已经持久化。
        }
    }

    /**
     * 按条件查询后台操作日志列表。
     *
     * @param request 查询条件
     * @return 操作日志列表
     */
    @Override
    public PageResult<SysOperLogDTO> pageOperLogs(SysOperLogQueryRequest request) {
        SysOperLogQueryRequest query = request == null ? new SysOperLogQueryRequest() : request;
        Page<SysOperLogDO> page = operLogMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildOperLogQueryWrapper(query)
        );
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream().map(operLogConverter::toDTO).toList()
        );
    }

    /**
     * 构建操作日志查询条件。
     *
     * @param query 查询请求
     * @return MyBatis Plus 查询条件
     */
    private LambdaQueryWrapper<SysOperLogDO> buildOperLogQueryWrapper(SysOperLogQueryRequest query) {
        return Wrappers.<SysOperLogDO>lambdaQuery()
                .eq(StringUtils.hasText(query.getTraceId()), SysOperLogDO::getTraceId, query.getTraceId())
                .eq(StringUtils.hasText(query.getRequestId()), SysOperLogDO::getRequestId, query.getRequestId())
                .eq(StringUtils.hasText(query.getMerchantId()), SysOperLogDO::getMerchantId, query.getMerchantId())
                .eq(StringUtils.hasText(query.getOperatorId()), SysOperLogDO::getOperatorId, query.getOperatorId())
                .eq(StringUtils.hasText(query.getModuleName()), SysOperLogDO::getModuleName, query.getModuleName())
                .eq(query.getBusinessType() != null, SysOperLogDO::getBusinessType, query.getBusinessType())
                .eq(query.getStatus() != null, SysOperLogDO::getStatus, query.getStatus())
                .ge(query.getOperatedStartAt() != null, SysOperLogDO::getOperatedAt, query.getOperatedStartAt())
                .le(query.getOperatedEndAt() != null, SysOperLogDO::getOperatedAt, query.getOperatedEndAt())
                .orderByDesc(SysOperLogDO::getOperatedAt);
    }

    /**
     * 获取非空整数。
     *
     * @param value        入参值
     * @param defaultValue 默认值
     * @return 非空整数
     */
    private Integer defaultIfNull(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }
}
