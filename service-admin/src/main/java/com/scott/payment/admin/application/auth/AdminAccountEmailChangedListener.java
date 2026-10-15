package com.scott.payment.admin.application.auth;

import com.scott.payment.admin.service.AdminEmailVerificationService;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.event.AccountEmailChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminAccountEmailChangedListener
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 管理账号个人中心邮箱变更监听器；仅在资料事务提交后创建新验证请求，避免回滚事务误发邮件。
 * @status : create
 */
@Slf4j
@Component
public class AdminAccountEmailChangedListener {

    private final AdminEmailVerificationService emailVerificationService;

    /** 创建邮箱变更监听器。 */
    public AdminAccountEmailChangedListener(AdminEmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    /** 在管理账号邮箱变更提交后签发新的邮箱验证请求。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountEmailChanged(AccountEmailChangedEvent event) {
        if (event == null || !AuthConstants.APP_ADMIN.equals(event.appCode())) {
            return;
        }
        try {
            emailVerificationService.issue(event.accountId(),
                    AdminEmailVerificationService.PURPOSE_EMAIL_CHANGE,
                    event.locale(), "self-service", null, false);
        } catch (RuntimeException exception) {
            log.warn("admin account email verification issue failed after profile update, accountId: {}, exceptionType: {}",
                    event.accountId(), exception.getClass().getSimpleName());
        }
    }
}
