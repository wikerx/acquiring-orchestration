package com.scott.payment.settlement.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReversalCommandResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReversalDecisionRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReversalSubmitRequest;
import com.scott.payment.settlement.application.SettlementReversalOrderApplicationService;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.dto.SettlementReversalCommandResult;
import com.scott.payment.settlement.dto.SettlementReversalCreateCommand;
import com.scott.payment.settlement.dto.SettlementReversalDecisionCommand;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReversalInternalController
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 仅供 service-admin 内部签名调用的结算冲正命令入口；浏览器不得直接访问，操作人和权限上下文必须由管理服务可信注入。
 * @status : create
 */
@RestController
@RequestMapping("/internal/settlement/v1/reversal-orders")
public class SettlementReversalInternalController {

    private final SettlementReversalOrderApplicationService applicationService;

    public SettlementReversalInternalController(SettlementReversalOrderApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 提交已入账结算批次的冲正申请，冻结原批次资金事实并进入待复核状态。
     *
     * @param request 包含请求幂等键、原批次版本、原因和可信 Maker 快照的请求
     * @return 冲正单号、状态、原批次和冻结净额
     * @throws IllegalArgumentException 请求或批次版本参数缺失时抛出
     * @throws IllegalStateException 原批次不可冲正、事实不完整或幂等身份冲突时抛出
     */
    @PostMapping
    public CommonResult<ReversalCommandResponse> submit(@RequestBody ReversalSubmitRequest request) {
        if (request == null || request.getExpectedBatchVersion() == null) {
            throw new IllegalArgumentException("settlement reversal submit request is invalid");
        }
        return success(response(applicationService.submit(new SettlementReversalCreateCommand(
                request.getRequestKey(), request.getOriginalBatchNo(), request.getExpectedBatchVersion(),
                request.getReason(), operator(request.getOperatorId(), request.getOperatorName(),
                request.getRoleSnapshot(), request.getClientIp(), request.getUserAgent(),
                request.getOperationTime())))));
    }

    /**
     * 由异于 Maker 的 Checker 审批或拒绝冲正申请；批准后在同一事务创建独立反向结算批次。
     *
     * @param reversalOrderNo 待决策冲正申请单号
     * @param request 决策幂等键、期望版本、意见和可信 Checker 快照
     * @return 决策后的冲正状态及反向批次号
     * @throws IllegalArgumentException 请求或版本参数缺失时抛出
     * @throws IllegalStateException 状态、版本、Maker-Checker 或冻结事实校验失败时抛出
     */
    @PostMapping("/{reversalOrderNo}/decisions")
    public CommonResult<ReversalCommandResponse> decide(
            @PathVariable("reversalOrderNo") String reversalOrderNo,
            @RequestBody ReversalDecisionRequest request) {
        if (request == null || request.getExpectedVersion() == null) {
            throw new IllegalArgumentException("settlement reversal decision request is invalid");
        }
        return success(response(applicationService.decide(reversalOrderNo,
                new SettlementReversalDecisionCommand(request.getRequestKey(), request.getExpectedVersion(),
                        request.getDecision(), request.getComment(),
                        operator(request.getOperatorId(), request.getOperatorName(), request.getRoleSnapshot(),
                                request.getClientIp(), request.getUserAgent(), request.getOperationTime())))));
    }

    private SettlementOperatorSnapshot operator(Long accountId,
                                                String accountName,
                                                String roleSnapshot,
                                                String clientIp,
                                                String userAgent,
                                                java.time.LocalDateTime operationTime) {
        return new SettlementOperatorSnapshot(accountId, accountName, roleSnapshot,
                clientIp, userAgent, operationTime);
    }

    private ReversalCommandResponse response(SettlementReversalCommandResult result) {
        ReversalCommandResponse response = new ReversalCommandResponse();
        response.setReversalOrderNo(result.reversalOrderNo());
        response.setReversalStatus(result.reversalStatus());
        response.setOriginalBatchNo(result.originalBatchNo());
        response.setReversalBatchNo(result.reversalBatchNo());
        response.setMerchantId(result.merchantId());
        response.setCurrency(result.currency());
        response.setNetDirection(result.netDirection());
        response.setNetAmount(result.netAmount());
        response.setVersion(result.version());
        return response;
    }
}
