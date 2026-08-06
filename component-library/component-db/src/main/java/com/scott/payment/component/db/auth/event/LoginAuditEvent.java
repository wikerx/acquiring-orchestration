package com.scott.payment.component.db.auth.event;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LoginAuditEvent
 * @date : 2026-08-02 22:00
 * @email : scott_x@163.com
 * @description : 管理端与商户端登录审计事件，隔离认证事务和异步审计持久化，事件不得携带密码、验证码或令牌
 * @status : create
 *
 * @param eventId 审计事件唯一编号，用于 Outbox 和消费端数据库幂等
 * @param appId 登录应用主键
 * @param accountId 登录账号主键，账号不存在时为空
 * @param userId 用户主键，账号不存在时为空
 * @param merchantId 商户号，管理端或未知账号时为空
 * @param loginAccount 登录账号输入值，日志和消息链路不得明文打印
 * @param clientIp 客户端 IP，仅用于安全审计
 * @param userAgent 客户端 User-Agent，发布前需限制长度
 * @param loginStatus 登录结果状态
 * @param failReason 失败原因摘要，成功时为空
 * @param loginAt 登录发生时间
 */
public record LoginAuditEvent(String eventId,
                              Long appId,
                              Long accountId,
                              Long userId,
                              String merchantId,
                              String loginAccount,
                              String clientIp,
                              String userAgent,
                              int loginStatus,
                              String failReason,
                              LocalDateTime loginAt) {
}
