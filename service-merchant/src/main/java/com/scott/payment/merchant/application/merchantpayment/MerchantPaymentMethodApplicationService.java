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

    /**
     * 使用已核验的商户范围读取分页支付方式及当前授权状态。
     */
    public PageResult<MethodRow> methods(Search query) {
        String merchant = merchant();
        requireOwnFilter(merchant, query);
        return reads.read(merchant, scope -> governance.methods(scope, merchant, query));
    }

    /**
     * 读取指定支付方式的开通、审批与配置结果。
     */
    public MethodRow method(String item) {
        String merchant = merchant();
        return reads.read(merchant, scope -> governance.method(scope, merchant, item));
    }

    /**
     * 在商户本人作用域内发起支付方式申请并返回可重放回执。
     */
    public BatchReceipt apply(Apply command) {
        String merchant = merchant();
        return governance.apply(reads.scope(merchant), merchant, command);
    }

    /**
     * 按商户范围和筛选条件读取申请批次。
     */
    public PageResult<ApplicationRow> applications(Search query) {
        String merchant = merchant();
        requireOwnFilter(merchant, query);
        return reads.read(merchant, scope -> governance.applications(scope, query));
    }

    /**
     * 读取由当前商户拥有的申请批次详情。
     */
    public ApplicationRow application(String application) {
        return reads.read(merchant(), scope -> governance.application(scope, application));
    }

    /**
     * 以操作类型区分补充材料和撤回，复用治理服务的轮次校验。
     */
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
