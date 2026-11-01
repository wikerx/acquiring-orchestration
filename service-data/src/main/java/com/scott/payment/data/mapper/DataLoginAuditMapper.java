package com.scott.payment.data.mapper;

import com.scott.payment.component.mq.message.LoginAuditMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 登录审计消费幂等记录和 sys_login_log 数据访问接口。
 */
public interface DataLoginAuditMapper {

    /**
     * 以 consumerGroup 和 messageId 唯一键登记消费，重复时返回 0。
     *
     * @param consumerGroup 登录审计固定消费组
     * @param messageId MQ 消息唯一标识
     * @param topic 登录审计 Topic
     * @param now 消费记录创建时间
     * @return 1 表示首次登记，0 表示数据库幂等命中
     */
    @Insert("""
            INSERT IGNORE INTO sys_mq_consume_record
                (consumer_group, message_id, topic, consumed_time, create_time)
            VALUES
                (#{consumerGroup}, #{messageId}, #{topic}, #{now}, #{now})
            """)
    int insertConsumeRecord(@Param("consumerGroup") String consumerGroup,
                            @Param("messageId") String messageId,
                            @Param("topic") String topic,
                            @Param("now") LocalDateTime now);

    /**
     * 写入登录审计事实。
     *
     * @param message 已通过基础校验的登录审计消息
     * @param createdAt 登录审计记录创建时间
     * @return 成功插入的记录数
     */
    @Insert("""
            INSERT INTO sys_login_log
                (app_id, account_id, user_id, merchant_id, login_account,
                 login_ip, user_agent, login_status, fail_reason, login_at, created_at)
            VALUES
                (#{message.appId}, #{message.accountId}, #{message.userId}, #{message.merchantId},
                 #{message.loginAccount}, #{message.clientIp}, #{message.userAgent},
                 #{message.loginStatus}, #{message.failReason}, #{message.loginAt}, #{createdAt})
            """)
    int insertLoginLog(@Param("message") LoginAuditMessage message,
                       @Param("createdAt") LocalDateTime createdAt);
}
