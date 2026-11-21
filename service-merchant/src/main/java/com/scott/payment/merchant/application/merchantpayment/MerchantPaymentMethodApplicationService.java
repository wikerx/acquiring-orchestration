package com.scott.payment.merchant.application.merchantpayment;

import com.scott.payment.capability.governance.MerchantPaymentGovernanceService;
import com.scott.payment.capability.governance.MerchantPaymentModels.*;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/** 在商户应用内按登录态调用查询和申请用例。 */
@Service
public class MerchantPaymentMethodApplicationService {
    private final MerchantPaymentGovernanceService governance;
    private final MerchantPaymentReads reads;

    public MerchantPaymentMethodApplicationService(MerchantPaymentGovernanceService governance, MerchantPaymentReads reads) {
        this.governance = governance;
        this.reads = reads;
    }

    public PageResult<MethodRow> methods(Search query) {
        String merchant = merchant();
        requireOwnFilter(merchant, query);
        return reads.read(merchant, scope -> governance.methods(scope, merchant, query));
    }

    public MethodRow method(String item) {
        String merchant = merchant();
        return reads.read(merchant, scope -> governance.method(scope, merchant, item));
    }

    public BatchReceipt apply(Apply command) {
        String merchant = merchant();
        return governance.apply(reads.scope(merchant), merchant, command);
    }

    public PageResult<ApplicationRow> applications(Search query) {
        String merchant = merchant();
        requireOwnFilter(merchant, query);
        return reads.read(merchant, scope -> governance.applications(scope, query));
    }

    public ApplicationRow application(String application) {
        return reads.read(merchant(), scope -> governance.application(scope, application));
    }

    public ItemReceipt change(long item, Change command, boolean resubmit) {
        return governance.changeApplication(reads.scope(merchant()), item, command, resubmit);
    }

    private static String merchant() {
        return MerchantPaymentReads.account().getMerchantId();
    }

    private static void requireOwnFilter(String merchant, Search query) {
        if (query != null && query.getMerchantId() != null && !query.getMerchantId().isBlank()
                && !merchant.equals(query.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
    }
}
