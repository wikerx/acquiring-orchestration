package com.scott.payment.merchant.application.merchantpayment;

import com.scott.payment.capability.governance.MerchantPaymentGovernanceService.Scope;
import com.scott.payment.capability.governance.MerchantPaymentRepository.ReadVersion;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.function.Function;

/** 商户查询先取得当前商户的提交标记，复杂查询和权限内投影优先由副本处理。 */
@Service
public class MerchantPaymentReads {
    private final MerchantPaymentReadDataSources sources;

    public MerchantPaymentReads(MerchantPaymentReadDataSources sources) {
        this.sources = sources;
    }

    public <T> T read(String merchant, Function<Scope, T> query) {
        Scope scope = scope(merchant);
        ReadVersion committed = sources.committedVersion(merchant);
        if (committed == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        try {
            var result = sources.readReplica(merchant, committed, () -> query.apply(scope));
            if (result.matched()) {
                return result.value();
            }
        } catch (DataAccessException | CannotCreateTransactionException replicaUnavailable) {
            // 仅当前请求回主。连接失败或延迟期间不会保留任何副本返回状态。
        }
        return sources.readMaster(() -> query.apply(scope));
    }

    public Scope scope(String merchant) {
        InternalAuthAccount account = account();
        if (!account.getMerchantId().equals(merchant)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        return sources.verifyMerchant(merchant, account.getAccountId(), account.getUserId());
    }

    static InternalAuthAccount account() {
        InternalAuthAccount value = InternalAuthContextHolder.get();
        if (value == null || !"MERCHANT".equals(value.getAppCode()) || value.getMerchantId() == null
                || value.getMerchantId().isBlank() || value.getAccountId() == null || value.getUserId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return value;
    }
}
