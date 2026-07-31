package com.scott.payment.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.SysOperLogDTO;
import com.scott.payment.merchant.dto.SysOperLogQueryRequest;
import com.scott.payment.merchant.dto.SysOperLogRecordRequest;
import com.scott.payment.merchant.entity.SysOperLogDO;
import com.scott.payment.merchant.mapper.SysOperLogMapper;
import com.scott.payment.merchant.service.MerchantOperLogService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperLogServiceImpl
 * @date : 2026-06-20 10:46
 * @email : scott_x@163.com
 * @description : 写入和查询商户端操作审计日志，使用 idempotent_key 唯一约束吸收 MQ 重复投递
 * @status : create
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
        try {
            operLogMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            if (!StringUtils.hasText(request.getIdempotentKey())) {
                throw exception;
            }
            // Redis 只做辅助去重；数据库唯一键命中即表示同一审计消息已经完成持久化。
        }
    }

    /**
     * 分页查询当前商户可见的操作日志。
     *
     * @param request 查询条件
     * @return 商户操作日志分页结果
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
                page.getRecords().stream().map(this::toDTO).toList()
        );
    }

    /**
     * 构造强制带 merchantId 的操作日志查询条件。
     *
     * @param query 当前商户日志查询
     * @return 按操作时间倒序的租户隔离查询包装器
     */
    private LambdaQueryWrapper<SysOperLogDO> buildOperLogQueryWrapper(SysOperLogQueryRequest query) {
        return Wrappers.<SysOperLogDO>lambdaQuery()
                .eq(SysOperLogDO::getMerchantId, query.getMerchantId())
                .eq(StringUtils.hasText(query.getModuleName()), SysOperLogDO::getModuleName, query.getModuleName())
                .eq(query.getStatus() != null, SysOperLogDO::getStatus, query.getStatus())
                .ge(query.getOperatedStartAt() != null, SysOperLogDO::getOperatedAt, query.getOperatedStartAt())
                .le(query.getOperatedEndAt() != null, SysOperLogDO::getOperatedAt, query.getOperatedEndAt())
                .orderByDesc(SysOperLogDO::getOperatedAt);
    }

    /**
     * 将操作日志记录转换为商户可见 DTO。
     * <p>
     * 不映射请求参数、响应报文或其他可能含密钥、token 的原始内容。
     * </p>
     *
     * @param entity 操作日志数据库记录
     * @return 商户后台审计展示 DTO
     */
    private SysOperLogDTO toDTO(SysOperLogDO entity) {
        SysOperLogDTO dto = new SysOperLogDTO();
        dto.setId(entity.getId());
        dto.setTraceId(entity.getTraceId());
        dto.setMerchantId(entity.getMerchantId());
        dto.setModuleName(entity.getModuleName());
        dto.setOperationName(entity.getOperationName());
        dto.setBusinessType(entity.getBusinessType());
        dto.setRequestMethod(entity.getRequestMethod());
        dto.setOperatorName(entity.getOperatorName());
        dto.setOperUrl(entity.getOperUrl());
        dto.setOperIp(entity.getOperIp());
        dto.setCostTime(entity.getCostTime());
        dto.setStatus(entity.getStatus());
        dto.setErrorCode(entity.getErrorCode());
        dto.setErrorMsg(entity.getErrorMsg());
        dto.setOperatedAt(entity.getOperatedAt());
        return dto;
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
