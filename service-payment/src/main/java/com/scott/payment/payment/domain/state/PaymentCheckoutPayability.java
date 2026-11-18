package com.scott.payment.payment.domain.state;

import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import java.time.LocalDateTime;

/**
 * 收银台新尝试的既有可付款判定，供会话视图和支付链接入口复用，避免显示与真实表单相反的操作。
 */
public final class PaymentCheckoutPayability {
    private PaymentCheckoutPayability() { }

    /**
     * 只判断当前快照是否可显示新付款入口；实际提交仍须在事务中重新校验并执行 CAS。
     */
    public static boolean mayStart(PaymentCheckoutSessionDO session, LocalDateTime now) {
        if (session == null || session.getExpireTime() == null || !session.getExpireTime().isAfter(now)) return false;
        if (PaymentCheckoutSessionStatusEnum.PENDING.getCode().equals(session.getCheckoutStatus())) {
            return session.getLastSubmitTime() == null
                    && PaymentCheckoutProcessStageEnum.WAITING_PAYER.getCode().equals(session.getProcessStage());
        }
        return PaymentCheckoutSessionStatusEnum.FAILED.getCode().equals(session.getCheckoutStatus())
                && session.getLastSubmitTime() != null && Integer.valueOf(1).equals(session.getRetryAllowed());
    }
}
