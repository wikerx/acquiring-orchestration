package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LoginAuditMessage
 * @date : 2026-08-02 22:30
 * @email : scott_x@163.com
 * @description : Admin 与 Merchant 登录审计消息，不包含密码、验证码、会话 Token 或密钥材料
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoginAuditMessage extends BaseMqMessage {

    /** 登录应用主键。 */
    private Long appId;
    /** 登录账号主键，未知账号时为空。 */
    private Long accountId;
    /** 用户主键，未知账号时为空。 */
    private Long userId;
    /** 商户号，管理端或未知账号时为空。 */
    private String merchantId;
    /** 登录账号输入值，禁止写入应用日志。 */
    private String loginAccount;
    /** 客户端 IP，仅用于安全审计。 */
    private String clientIp;
    /** 客户端 User-Agent。 */
    private String userAgent;
    /** 登录结果：0 失败、1 成功。 */
    private Integer loginStatus;
    /** 失败原因摘要。 */
    private String failReason;
    /** 登录发生时间。 */
    private LocalDateTime loginAt;
}
