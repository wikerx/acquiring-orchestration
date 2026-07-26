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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperLogServiceImpl
 * @date : 2026-06-20 10:46
 * @email : scott_x@163.com
 * @description : MerchantOperLogServiceImpl 服务实现，用于执行领域规则、数据读写编排和业务异常转换，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
     * 构建 build Oper Log Query Wrapper 对应的领域对象、请求对象或日志对象。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 转换生成 to DTO 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param entity entity 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
