package com.scott.payment.admin.client.settlement;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalBatchCommandRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewDecisionTaskResumeRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReversalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReversalSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalManualReviewPreviewRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ManualReviewStartRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ManualReviewTaskResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDecisionTaskResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalProfileCreateRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalProfileVersionCreateRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileReadinessResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.CommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.InternalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.InternalSubmitRequest;

/**
 * service-settlement 结算命令内部客户端边界，隔离 Admin 应用层与 HTTP/HMAC 细节。
 */
public interface SettlementInternalClient {

    /**
     * 创建商户首个活动结算档案。
     */
    ProfileCommandResponse createProfile(InternalProfileCreateRequest request);

    /**
     * 从当前活动档案创建新版本。
     */
    ProfileCommandResponse createProfileVersion(
            String settlementProfileNo, InternalProfileVersionCreateRequest request);

    /**
     * 查询商户结算档案和目标账户是否满足最终开通条件。
     */
    ProfileReadinessResponse getProfileReadiness(String merchantId);

    /**
     * 发送已注入可信操作人与权限快照的批次取消命令。
     *
     * @param settlementBatchNo 待取消正式批次号
     * @param request 期望版本、请求幂等键、原因和可信操作审计
     * @return 入账前取消状态和实际释放候选数
     */
    BatchCommandResponse cancel(String settlementBatchNo, InternalBatchCommandRequest request);

    /**
     * 发送仅限汇率锁定重试耗尽场景的人工恢复命令。
     */
    BatchCommandResponse retry(String settlementBatchNo, InternalBatchCommandRequest request);

    /**
     * 发送已注入可信 Maker 的结算预审提交命令。
     *
     * @param request 候选版本、业务窗口、原因和可信 Maker 快照
     * @return 新建或幂等回放的预审结果
     */
    ReviewCommandResponse submitReview(InternalReviewSubmitRequest request);

    /**
     * 发送已注入可信 Checker 的预审批准、拒绝或取消命令。
     *
     * @param reviewOrderNo 待决策预审单号
     * @param request 决策、预期版本、幂等键和可信 Checker 快照
     * @return 决策后的预审状态和正式批次关联
     */
    ReviewCommandResponse decideReview(String reviewOrderNo, InternalReviewDecisionRequest request);

    /**
     * 创建手动交易结算预审的只读预览。
     */
    ManualReviewTaskResponse previewManualReview(InternalManualReviewPreviewRequest request);

    /**
     * 启动手动交易结算预审任务。
     */
    ManualReviewTaskResponse startManualReview(String taskNo, ManualReviewStartRequest request);

    /**
     * 查询手动交易结算预审任务。
     */
    ManualReviewTaskResponse getManualReviewTask(String taskNo);

    /**
     * 创建手动保证金结算预审的只读预览。
     */
    ManualReviewTaskResponse previewManualReserveReview(InternalManualReviewPreviewRequest request);

    /**
     * 启动手动保证金结算预审任务。
     */
    ManualReviewTaskResponse startManualReserveReview(String taskNo, ManualReviewStartRequest request);

    /**
     * 查询手动保证金结算预审任务。
     */
    ManualReviewTaskResponse getManualReserveReviewTask(String taskNo);

    /**
     * 提交可恢复的结算预审异步决策任务。
     */
    ReviewDecisionTaskResponse submitReviewDecisionTask(
            String reviewOrderNo, InternalReviewDecisionRequest request);

    /**
     * 查询结算复核异步决策任务。
     */
    ReviewDecisionTaskResponse getReviewDecisionTask(String taskNo);

    /**
     * 恢复可重试的结算预审决策任务。
     */
    ReviewDecisionTaskResponse resumeReviewDecisionTask(
            String taskNo, InternalReviewDecisionTaskResumeRequest request);

    /**
     * 发送已注入可信 Maker 的已入账批次冲正申请。
     *
     * @param request 原批次号、预期版本、原因和可信 Maker 快照
     * @return 新建或幂等回放的冲正申请结果
     */
    ReversalCommandResponse submitReversal(InternalReversalSubmitRequest request);

    /**
     * 发送已注入可信 Checker 的冲正批准或拒绝命令。
     *
     * @param reversalOrderNo 待决策冲正单号
     * @param request 决策、预期版本、幂等键和可信 Checker 快照
     * @return 决策后的冲正状态和逆向批次关联
     */
    ReversalCommandResponse decideReversal(String reversalOrderNo,
                                            InternalReversalDecisionRequest request);

    /** 提交已入账历史标签本金差额调整申请。 */
    CommandResponse submitPrincipalCorrection(InternalSubmitRequest request);

    /** 复核已入账历史标签本金差额调整申请。 */
    CommandResponse decidePrincipalCorrection(String correctionNo, InternalDecisionRequest request);
}
