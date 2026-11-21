package com.scott.payment.admin.application.merchantpayment;

import com.scott.payment.capability.governance.MerchantPaymentGovernanceService;

import com.scott.payment.capability.governance.MerchantPaymentGovernanceService.Scope;
import com.scott.payment.capability.governance.MerchantPaymentModels.BatchDecision;
import com.scott.payment.capability.governance.MerchantPaymentModels.BatchDecisionReceipt;
import com.scott.payment.capability.governance.MerchantPaymentModels.DecisionItem;
import com.scott.payment.capability.governance.MerchantPaymentModels.DecisionOutcome;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 批量编排不持有外层事务，每个决定沿用单项事务和原请求键。 */
@Service
public class MerchantPaymentBatchReviewService {
    private static final Logger LOG = LoggerFactory.getLogger(MerchantPaymentBatchReviewService.class);
    private static final Set<String> REJECTED_CODES = Set.of(ApiResultEnum.FORBIDDEN.getCode(),
            ApiResultEnum.PARAM_INVALID.getCode(), ApiResultEnum.ORDER_NOT_FOUND.getCode(),
            ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT.getCode());
    private final MerchantPaymentGovernanceService governance;

    public MerchantPaymentBatchReviewService(MerchantPaymentGovernanceService governance) {
        this.governance = governance;
    }

    public BatchDecisionReceipt decide(Scope scope, BatchDecision command) {
        List<Long> itemIds;
        try {
            itemIds = command.items().stream().map(DecisionItem::itemId).map(Long::parseLong).toList();
        } catch (NumberFormatException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        if (itemIds.stream().distinct().count() != itemIds.size()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "同一次批量审核不能重复提交同一申请项");
        }

        List<DecisionOutcome> results = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;
        int unknown = 0;
        int unprocessed = 0;
        for (int index = 0; index < itemIds.size(); index++) {
            DecisionItem item = command.items().get(index);
            if (unknown > 0) {
                unprocessed++;
                results.add(new DecisionOutcome(item.itemId(), "NOT_PROCESSED", null, null, null));
                continue;
            }
            try {
                var receipt = governance.decide(scope, itemIds.get(index), item.command());
                results.add(new DecisionOutcome(item.itemId(), "SUCCEEDED", receipt, null, null));
                succeeded++;
            } catch (RuntimeException exception) {
                if (exception instanceof ServiceException denial && REJECTED_CODES.contains(denial.getCode())) {
                    failed++;
                    results.add(new DecisionOutcome(item.itemId(), "FAILED", null, denial.getCode(), denial.getMessage()));
                    continue;
                }
                unknown++;
                LOG.warn("商户支付方式批量审核单项结果未知, event: MERCHANT_PAYMENT_BATCH_REVIEW_UNKNOWN, itemId: {}, exceptionType: {}",
                        item.itemId(), exception.getClass().getSimpleName());
                results.add(new DecisionOutcome(item.itemId(), "UNKNOWN", null, ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                        "审核结果待确认，请使用原请求号重试"));
            }
        }
        return new BatchDecisionReceipt(List.copyOf(results), succeeded, failed, unknown, unprocessed);
    }
}
