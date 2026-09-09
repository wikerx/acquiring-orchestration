package com.scott.payment.admin.client.clearing;

import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.CommandResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.DetailResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalActionRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalRecalculateRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalReserveAdjustmentReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalReserveAdjustmentSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ReserveAdjustmentResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.SearchRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.SearchResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalTierPeriodReplayReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalTierPeriodReplaySubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.TierPeriodReplayResponse;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingInternalClient
 * @date : 2026-09-01 22:45
 * @email : scott_x@163.com
 * @description : service-admin 调用 service-clearing 的内部协议边界；人工命令必须由 Admin 先完成权限、数据范围、审计和可信操作人注入。
 * @status : update
 */
public interface ClearingInternalClient {
    /** 按 Admin 已校验条件查询清分记录。 */
    SearchResponse search(SearchRequest request);
    /** 按平台交易号和交易时间定位清分详情。 */
    DetailResponse detail(String transactionId, LocalDateTime transactionDateTime);
    /** 请求清分服务幂等重试指定交易。 */
    CommandResponse retry(String transactionId, InternalActionRequest request);
    /** 提交指定交易的人工复核结论。 */
    CommandResponse review(String transactionId, InternalActionRequest request);
    /** 按冻结费用版本请求重新计算清分事实。 */
    CommandResponse recalculate(String transactionId, InternalRecalculateRequest request);
    /** 提交保证金人工调整申请，尚不直接改变资金。 */
    ReserveAdjustmentResponse submitReserveAdjustment(InternalReserveAdjustmentSubmitRequest request);
    /** Maker-Checker 复核保证金人工调整申请。 */
    ReserveAdjustmentResponse reviewReserveAdjustment(
            String adjustmentNo, InternalReserveAdjustmentReviewRequest request);
    /** 提交阶梯周期重放申请。 */
    TierPeriodReplayResponse submitTierPeriodReplay(InternalTierPeriodReplaySubmitRequest request);
    /** Maker-Checker 复核阶梯周期重放申请。 */
    TierPeriodReplayResponse reviewTierPeriodReplay(
            String replayNo, InternalTierPeriodReplayReviewRequest request);
}
