package com.scott.payment.component.mq.email;

import javax.crypto.AEADBadTagException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EmailDeliveryFailureSummary
 * @date : 2026-08-02 23:59
 * @email : scott_x@163.com
 * @description : 将邮件投递异常收敛为不含地址、账号、凭据和基础设施信息的持久化摘要，并为主密钥不匹配提供可操作提示
 * @status : create
 */
public final class EmailDeliveryFailureSummary {

    /** 无可识别异常类型时使用的非敏感兜底摘要。 */
    private static final String UNKNOWN_FAILURE = "UnknownEmailDeliveryFailure";
    /** AES-GCM 认证失败时面向操作人员返回的安全处理建议。 */
    private static final String ENCRYPTION_KEY_MISMATCH =
            "邮件加密数据无法解密，请确认邮件加密主密钥一致，或重新录入 SMTP 授权码";

    private EmailDeliveryFailureSummary() {
    }

    /**
     * 返回安全的失败摘要；主密钥不匹配时给出处理建议，其他异常仅保留最深层类型。
     *
     * @param throwable 邮件投递链路捕获的异常
     * @return 不含第三方异常 message 的安全摘要
     */
    public static String summarize(Throwable throwable) {
        if (throwable == null) {
            return UNKNOWN_FAILURE;
        }
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        if (rootCause instanceof AEADBadTagException) {
            return ENCRYPTION_KEY_MISMATCH;
        }
        String simpleName = rootCause.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? UNKNOWN_FAILURE : simpleName;
    }
}
