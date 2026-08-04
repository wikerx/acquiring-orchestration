package com.scott.payment.component.mq.email;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EmailDeliveryFailureSummary
 * @date : 2026-08-02 23:59
 * @email : scott_x@163.com
 * @description : 将邮件投递异常收敛为不含地址、账号、凭据和基础设施信息的持久化摘要
 * @status : create
 */
public final class EmailDeliveryFailureSummary {

    /** 无可识别异常类型时使用的非敏感兜底摘要。 */
    private static final String UNKNOWN_FAILURE = "UnknownEmailDeliveryFailure";

    private EmailDeliveryFailureSummary() {
    }

    /**
     * 只返回最深层异常类型，禁止把第三方异常消息写入邮件记录。
     *
     * @param throwable 邮件投递链路捕获的异常
     * @return 不含异常 message 的类型摘要
     */
    public static String summarize(Throwable throwable) {
        if (throwable == null) {
            return UNKNOWN_FAILURE;
        }
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String simpleName = rootCause.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? UNKNOWN_FAILURE : simpleName;
    }
}
