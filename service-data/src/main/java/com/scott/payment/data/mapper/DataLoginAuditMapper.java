package com.scott.payment.data.mapper;

import com.scott.payment.component.mq.message.LoginAuditMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataLoginAuditMapper
 * @date : 2026-08-02 22:30
 * @email : scott_x@163.com
 * @description : 登录审计消费幂等记录和 sys_login_log 数据访问接口
 * @status : create
 */
public interface DataLoginAuditMapper {

    /** 以 consumerGroup 和 messageId 唯一键登记消费，重复时返回 0。 */
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

    /** 写入登录审计事实。 */
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
