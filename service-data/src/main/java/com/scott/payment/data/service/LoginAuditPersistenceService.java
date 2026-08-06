package com.scott.payment.data.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.LoginAuditMessage;
import com.scott.payment.data.mapper.DataLoginAuditMapper;
import com.scott.payment.data.mq.DataMqConsumerGroups;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LoginAuditPersistenceService
 * @date : 2026-08-02 22:30
 * @email : scott_x@163.com
 * @description : 登录审计消费事务边界，以数据库唯一键和登录日志插入形成原子幂等
 * @status : create
 */
@Service
public class LoginAuditPersistenceService {

    /** 登录审计 Mapper。 */
    private final DataLoginAuditMapper mapper;

    /** 创建登录审计持久化服务。 */
    public LoginAuditPersistenceService(DataLoginAuditMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 幂等写入一条登录审计；重复消息直接确认且不新增日志。
     *
     * @param message 登录审计消息
     * @return 本次是否新增登录日志
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public boolean persist(LoginAuditMessage message) {
        LocalDateTime now = LocalDateTime.now();
        int acquired = mapper.insertConsumeRecord(
                DataMqConsumerGroups.LOGIN_AUDIT,
                message.getMessageId(),
                MqTopic.LOGIN_AUDIT,
                now);
        if (acquired != 1) {
            return false;
        }
        if (message.getLoginAt() == null) {
            message.setLoginAt(message.getCreatedAt() == null ? now : message.getCreatedAt());
        }
        mapper.insertLoginLog(message, now);
        return true;
    }
}
