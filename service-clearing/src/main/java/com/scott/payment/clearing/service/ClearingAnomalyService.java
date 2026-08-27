package com.scott.payment.clearing.service;

import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingAnomalyTypeEnum;

import java.time.LocalDateTime;

/** 持久化和关闭清分异常案件，不改变交易或清分权威状态。 */
public interface ClearingAnomalyService {

    /** 记录或累加同一修订、同一类别、同一失败码的异常案件。 */
    void record(ClearingOperationFacts operation, String financeStateId, int revision,
                ClearingAnomalyTypeEnum anomalyType, String failureCode,
                String summary, LocalDateTime now);

    /** 清分恢复完成后关闭同一交易分片的活动清分异常案件。 */
    void resolve(String transactionId, LocalDateTime transactionDateTime,
                 String referenceId, LocalDateTime now);
}
