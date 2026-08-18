package com.scott.payment.data.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.security.entity.SecurityInterceptEventDO;
import com.scott.payment.component.db.security.mapper.SecurityInterceptEventMapper;
import com.scott.payment.component.mq.message.SecurityInterceptAuditMessage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SecurityInterceptAuditPersistenceService
 * @date : 2026-08-01 18:00
 * @email : scott_x@163.com
 * @description : service-data 安全拦截审计写入服务，以 event_no 数据库唯一键吸收 RocketMQ 重复投递
 * @status : create
 */
@Service
public class SecurityInterceptAuditPersistenceService {

    /** 安全拦截事件数据访问入口。 */
    private final SecurityInterceptEventMapper eventMapper;

    /**
     * 创建安全拦截审计写入服务。
     *
     * @param eventMapper 安全拦截事件 Mapper
     */
    public SecurityInterceptAuditPersistenceService(SecurityInterceptEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    /**
     * 将已脱敏的安全拦截事件写入主库。
     * <p>
     * event_no 唯一键冲突表示相同事件已成功持久化，可正常确认消费；其他数据库异常
     * 必须上抛，由 RocketMQ 重投。
     * </p>
     *
     * @param message 已通过基础校验的安全拦截审计消息
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void persist(SecurityInterceptAuditMessage message) {
        try {
            eventMapper.insert(toEntity(message));
        } catch (DuplicateKeyException exception) {
            // event_no 唯一索引是最终幂等依据，命中即表示该审计事实已持久化。
        }
    }

    /**
     * 将公共 MQ 契约转换为安全拦截事件数据库记录。
     *
     * @param message 已脱敏审计消息
     * @return 可直接写入 security_intercept_event 的数据库实体
     */
    private SecurityInterceptEventDO toEntity(SecurityInterceptAuditMessage message) {
        LocalDateTime now = LocalDateTime.now();
        SecurityInterceptEventDO entity = new SecurityInterceptEventDO();
        entity.setEventNo(message.getEventNo());
        entity.setEventTime(message.getEventTime() == null ? now : message.getEventTime());
        entity.setSourceLayer(message.getSourceLayer());
        entity.setEventType(message.getEventType());
        entity.setRiskLevel(message.getRiskLevel());
        entity.setAction(message.getAction());
        entity.setMerchantId(message.getMerchantId());
        entity.setClientIp(message.getClientIp());
        entity.setRequestMethod(message.getRequestMethod());
        entity.setRequestPath(message.getRequestPath());
        entity.setTraceId(message.getTraceId());
        entity.setRequestId(message.getRequestId());
        entity.setUserAgent(message.getUserAgent());
        entity.setReasonCode(message.getReasonCode());
        entity.setReasonMessage(message.getReasonMessage());
        entity.setServiceName(message.getServiceName());
        entity.setHitRuleCode(message.getHitRuleCode());
        entity.setHeaderSummary(message.getHeaderSummary());
        entity.setProcessStatus(0);
        entity.setGmtCreate(now);
        entity.setGmtModified(now);
        return entity;
    }
}
