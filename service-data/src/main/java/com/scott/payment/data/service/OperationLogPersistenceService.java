package com.scott.payment.data.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.data.entity.DataOperationLogDO;
import com.scott.payment.data.mapper.DataOperationLogMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogPersistenceService
 * @date : 2026-08-01 14:40
 * @email : scott_x@163.com
 * @description : 操作日志事务写入服务，以 sys_oper_log 唯一索引吸收 RocketMQ 重复投递
 * @status : create
 */
@Service
public class OperationLogPersistenceService {

    /** 后台用户操作人类型。 */
    private static final int ADMIN_OPERATOR_TYPE = 1;

    /** 商户用户操作人类型。 */
    private static final int MERCHANT_OPERATOR_TYPE = 2;

    /** 默认成功状态。 */
    private static final int SUCCESS_STATUS = 1;

    /** 操作日志数据访问入口。 */
    private final DataOperationLogMapper operationLogMapper;

    /**
     * 创建操作日志事务写入服务。
     *
     * @param operationLogMapper 操作日志 Mapper
     */
    public OperationLogPersistenceService(DataOperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 将一条已通过基础校验的操作日志写入主库。
     *
     * <p>Redis 只减少重复插入请求。数据库唯一键命中表示同一消息此前已经成功写入，
     * 可以正常确认本次 MQ 消费；其他数据库异常必须上抛触发 RocketMQ 重试。</p>
     *
     * @param message       已由公共契约反序列化的操作日志消息
     * @param idempotentKey 非空消费幂等键
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void persist(OperationLogMessage message, String idempotentKey) {
        DataOperationLogDO entity = toEntity(message, idempotentKey);
        try {
            operationLogMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            if (!StringUtils.hasText(idempotentKey)) {
                throw exception;
            }
            // 数据库唯一索引是最终幂等依据，命中即表示该审计事实已完成持久化。
        }
    }

    /**
     * 在主库核对操作日志幂等事实是否已经提交。
     *
     * @param idempotentKey 消费幂等键
     * @return true 表示数据库中已经存在对应操作日志
     */
    @DS(DataSourceName.MASTER)
    public boolean existsByIdempotentKey(String idempotentKey) {
        if (!StringUtils.hasText(idempotentKey)) {
            return false;
        }
        return operationLogMapper.selectCount(Wrappers.<DataOperationLogDO>lambdaQuery()
                .eq(DataOperationLogDO::getIdempotentKey, idempotentKey.trim())) > 0;
    }

    /**
     * 把公共 MQ 契约转换为数据库实体，并统一补齐审计默认值。
     *
     * @param message       操作日志消息
     * @param idempotentKey 非空消费幂等键
     * @return 可直接写入 sys_oper_log 的实体
     */
    private DataOperationLogDO toEntity(OperationLogMessage message, String idempotentKey) {
        LocalDateTime now = LocalDateTime.now();
        DataOperationLogDO entity = new DataOperationLogDO();
        entity.setTraceId(message.getTraceId());
        entity.setRequestId(message.getRequestId());
        entity.setMessageId(message.getMessageId());
        entity.setIdempotentKey(idempotentKey);
        entity.setSystemCode(message.getSystemCode());
        entity.setMerchantId(message.getMerchantId());
        entity.setModuleName(message.getOperationModule());
        entity.setOperationName(message.getOperationName());
        entity.setBusinessType(parseInteger(message.getOperationType()));
        entity.setMethodName(message.getMethodName());
        entity.setRequestMethod(message.getRequestMethod());
        entity.setOperatorType(resolveOperatorType(message));
        entity.setOperatorId(message.getOperatorId());
        entity.setOperatorName(message.getOperatorName());
        entity.setOperUrl(message.getRequestUri());
        entity.setOperIp(message.getClientIp());
        entity.setStoreId(message.getStoreId());
        entity.setUserAgent(message.getUserAgent());
        entity.setRequestParam(message.getRequestParams());
        entity.setResponseResult(message.getResponseResult());
        entity.setCostTime(message.getCostTimeMs());
        entity.setStatus(message.getOperationStatus() == null ? SUCCESS_STATUS : message.getOperationStatus());
        entity.setErrorCode(truncate(message.getErrorCode(), OperationLogMessage.ERROR_CODE_MAX_LENGTH));
        entity.setErrorMsg(truncate(message.getErrorMessage(), OperationLogMessage.ERROR_MESSAGE_MAX_LENGTH));
        entity.setOperatedAt(message.getOperationTime() == null ? now : message.getOperationTime());
        entity.setCreatedAt(now);
        return entity;
    }

    /**
     * 在数据库持久化边界收敛受限文本，兼容历史消息和非标准生产者。
     *
     * @param value     原始文本
     * @param maxLength 数据库列允许的最大字符数
     * @return 不超过数据库列上限的文本
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 解析消息中的受控整数编码。
     *
     * @param value 整数字符串；允许为空
     * @return 对应整数；空文本返回 null
     */
    private Integer parseInteger(String value) {
        return StringUtils.hasText(value) ? Integer.valueOf(value.trim()) : null;
    }

    /**
     * 优先使用生产端提供的操作人类型，缺失时根据来源系统补齐。
     *
     * @param message 操作日志消息
     * @return 操作人类型编码
     */
    private Integer resolveOperatorType(OperationLogMessage message) {
        Integer provided = parseInteger(message.getOperatorType());
        if (provided != null) {
            return provided;
        }
        return "MERCHANT".equalsIgnoreCase(message.getSystemCode())
                ? MERCHANT_OPERATOR_TYPE : ADMIN_OPERATOR_TYPE;
    }
}
