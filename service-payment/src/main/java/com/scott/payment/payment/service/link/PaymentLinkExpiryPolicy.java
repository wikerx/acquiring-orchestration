package com.scott.payment.payment.service.link;

import java.time.LocalDateTime;

/** 保留 Payment 内既有调用路径；有效期语义由共享管理规则维护。 */
public final class PaymentLinkExpiryPolicy {
    /** 链接有效期上限为三十天，不放宽普通收银台会话期限。 */
    public static final int MAX_MINUTES = com.scott.payment.link.service.PaymentLinkExpiryPolicy.MAX_MINUTES;
    private PaymentLinkExpiryPolicy() { }
    /** 校验相对时长与绝对截止时间互斥，并将截止时刻冻结为数据库兼容的毫秒精度。 */
    public static LocalDateTime resolve(Integer minutes, LocalDateTime requested, LocalDateTime now) {
        return com.scott.payment.link.service.PaymentLinkExpiryPolicy.resolve(minutes, requested, now);
    }
}
