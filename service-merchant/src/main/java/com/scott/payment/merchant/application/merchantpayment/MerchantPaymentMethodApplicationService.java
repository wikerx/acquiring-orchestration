package com.scott.payment.merchant.application.merchantpayment;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.client.admin.MerchantPaymentGovernanceClient;
import com.scott.payment.merchant.client.admin.MerchantPaymentGovernanceClient.Identity;
import com.scott.payment.merchant.dto.merchantpayment.MerchantPaymentModels.*;
import org.springframework.stereotype.Service;

/** 将商户登录态绑定到权威支付方式用例，客户端不决定租户或操作者。 */
@Service
public class MerchantPaymentMethodApplicationService {
    private final MerchantPaymentGovernanceClient governance;

    public MerchantPaymentMethodApplicationService(MerchantPaymentGovernanceClient governance) {
        this.governance = governance;
    }

    public PageResult<MethodRow> methods(Search query) {
        InternalAuthAccount session = account();
        requireOwnFilter(session, query);
        return governance.methods(session.getMerchantId(), actor(session), query);
    }

    public MethodRow method(String item) {
        InternalAuthAccount session = account();
        return governance.method(session.getMerchantId(), actor(session), item);
    }

    public BatchReceipt apply(Apply command) {
        InternalAuthAccount session = account();
        return governance.apply(session.getMerchantId(), actor(session), command);
    }

    public PageResult<ApplicationRow> applications(Search query) {
        InternalAuthAccount session = account();
        requireOwnFilter(session, query);
        return governance.applications(session.getMerchantId(), actor(session), query);
    }

    public ApplicationRow application(String application) {
        InternalAuthAccount session = account();
        return governance.application(session.getMerchantId(), actor(session), application);
    }

    public ItemReceipt change(long item, Change command, boolean resubmit) {
        InternalAuthAccount session = account();
        return governance.change(session.getMerchantId(), actor(session), item, command, resubmit);
    }

    private static InternalAuthAccount account() {
        InternalAuthAccount value = InternalAuthContextHolder.get();
        if (value == null || !"MERCHANT".equals(value.getAppCode()) || value.getMerchantId() == null
                || value.getMerchantId().isBlank() || value.getAccountId() == null || value.getUserId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return value;
    }

    private static Identity actor(InternalAuthAccount value) {
        return new Identity(value.getAccountId(), value.getUserId());
    }

    private static void requireOwnFilter(InternalAuthAccount session, Search query) {
        if (query != null && query.getMerchantId() != null && !query.getMerchantId().isBlank()
                && !session.getMerchantId().equals(query.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
    }
}
