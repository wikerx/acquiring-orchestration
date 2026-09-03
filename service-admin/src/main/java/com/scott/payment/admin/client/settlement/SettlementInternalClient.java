package com.scott.payment.admin.client.settlement;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalBatchCommandRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReversalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReversalSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalCommandResponse;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementInternalClient
 * @date : 2026-08-26 21:20
 * @email : scott_x@163.com
 * @description : service-settlement 结算命令内部客户端边界，隔离 Admin 应用层与 HTTP/HMAC 细节。
 * @status : create
 */
public interface SettlementInternalClient {

    /**
     * 发送已注入可信操作人与权限快照的批次取消命令。
     *
     * @param settlementBatchNo 待取消正式批次号
     * @param request 期望版本、请求幂等键、原因和可信操作审计
     * @return 入账前取消状态和实际释放候选数
     */
    BatchCommandResponse cancel(String settlementBatchNo, InternalBatchCommandRequest request);

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
}
