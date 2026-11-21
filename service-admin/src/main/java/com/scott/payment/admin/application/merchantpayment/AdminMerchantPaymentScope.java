package com.scott.payment.admin.application.merchantpayment;

import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.capability.governance.MerchantPaymentGovernanceService.Scope;
import com.scott.payment.capability.governance.MerchantPaymentRepository.Actor;
import com.scott.payment.capability.governance.MerchantPaymentScope;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Service;

/** 从平台登录态生成支付方式用例的数据范围。 */
@Service
public class AdminMerchantPaymentScope {
    private final AdminMerchantDataScopeResolver scopes;

    public AdminMerchantPaymentScope(AdminMerchantDataScopeResolver scopes) {
        this.scopes = scopes;
    }

    /**
     * 从当前平台操作员与数据权限派生支付治理访问范围。
     */
    public Scope current() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !"ADMIN".equals(account.getAppCode())
                || account.getAccountId() == null || account.getUserId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        var merchants = scopes.resolve(account);
        return new Scope(new MerchantPaymentScope(merchants.allMerchants(), merchants.merchantIds()),
                new Actor("ADMIN", account.getAccountId(), account.getUserId(), account.getRealName()));
    }
}
