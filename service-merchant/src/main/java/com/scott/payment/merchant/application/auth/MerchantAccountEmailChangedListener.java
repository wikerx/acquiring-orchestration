package com.scott.payment.merchant.application.auth;

import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.event.AccountEmailChangedEvent;
import com.scott.payment.merchant.service.MerchantEmailVerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAccountEmailChangedListener
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 商户个人邮箱变更监听器；仅在资料事务提交后签发待验证邮箱确认邮件。
 * @status : create
 */
@Slf4j
@Component
public class MerchantAccountEmailChangedListener {

    private final MerchantEmailVerificationService emailVerificationService;

    /** 创建商户邮箱变更监听器。 */
    public MerchantAccountEmailChangedListener(MerchantEmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    /** 事务提交后签发邮箱变更验证请求。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountEmailChanged(AccountEmailChangedEvent event) {
        if (event == null || !AuthConstants.APP_MERCHANT.equals(event.appCode())) {
            return;
        }
        try {
            emailVerificationService.issue(event.accountId(), MerchantEmailVerificationService.PURPOSE_EMAIL_CHANGE,
                    event.locale(), "self-service", null, false);
        } catch (RuntimeException exception) {
            log.warn("merchant email verification issue failed after profile update, accountId: {}, exceptionType: {}",
                    event.accountId(), exception.getClass().getSimpleName());
        }
    }
}
