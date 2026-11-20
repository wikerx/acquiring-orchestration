package com.scott.payment.link.service;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import java.time.LocalDateTime;

/** 链接发布时一次冻结截止时间；相对时长兼容旧请求，绝对时刻不因重试延长。 */
public final class PaymentLinkExpiryPolicy {
    /** 未指定有效期时采用一小时，仅在创建链接时冻结。 */
    public static final int DEFAULT_MINUTES = 60;
    /** 链接有效期上限为三十天，不放宽普通收银台会话期限。 */
    public static final int MAX_MINUTES = 30 * 24 * 60;

    private PaymentLinkExpiryPolicy() { }

    /** 校验相对时长与绝对截止时间互斥，并将截止时刻冻结为数据库兼容的毫秒精度。 */
    public static LocalDateTime resolve(Integer minutes, LocalDateTime requested, LocalDateTime now) {
        if (minutes != null && requested != null) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        if (requested != null) {
            if (requested.isBefore(now.plusMinutes(15)) || requested.isAfter(now.plusDays(30))) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID);
            }
            // 与 DATETIME(3) 和签名上下文保持同一精度，防止数据库舍入导致再次分享的凭据变化。
            return requested.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        }
        int duration = minutes == null ? DEFAULT_MINUTES : minutes;
        if (duration < 15 || duration > MAX_MINUTES) throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        return now.plusMinutes(duration);
    }
}
