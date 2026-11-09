package com.scott.payment.settlement.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.settlement.api.internal.dto.SettlementPrincipalCorrectionDTOs.CommandResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementPrincipalCorrectionDTOs.DecisionRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementPrincipalCorrectionDTOs.SubmitRequest;
import com.scott.payment.settlement.application.SettlementPrincipalCorrectionApplicationService;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.dto.SettlementPrincipalCorrectionCommandResult;
import com.scott.payment.settlement.dto.SettlementPrincipalCorrectionDecisionCommand;
import com.scott.payment.settlement.dto.SettlementPrincipalCorrectionSubmitCommand;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * service-admin 专用的历史标签本金差额调整内部入口；浏览器不得直接调用。
 */
@RestController
@RequestMapping("/internal/settlement/v1/principal-corrections")
public class SettlementPrincipalCorrectionInternalController {

    private final SettlementPrincipalCorrectionApplicationService applicationService;

    public SettlementPrincipalCorrectionInternalController(
            SettlementPrincipalCorrectionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 冻结一个已入账原批次的历史本金差额，申请阶段不改变账户余额。
     */
    @PostMapping
    public CommonResult<CommandResponse> submit(@RequestBody SubmitRequest request) {
        if (request == null || request.getExpectedBatchVersion() == null) {
            throw new IllegalArgumentException("principal correction submit request is invalid");
        }
        return success(response(applicationService.submit(new SettlementPrincipalCorrectionSubmitCommand(
                request.getRequestKey(), request.getOriginalBatchNo(), request.getExpectedBatchVersion(),
                request.getReason(), operator(request.getOperatorId(), request.getOperatorName(),
                request.getRoleSnapshot(), request.getClientIp(), request.getUserAgent(),
                request.getOperationTime())))));
    }

    /**
     * 由异于 Maker 的 Checker 批准或拒绝；批准才追加差额资金流水并激活投影修正。
     */
    @PostMapping("/{correctionNo}/decisions")
    public CommonResult<CommandResponse> decide(
            @PathVariable("correctionNo") String correctionNo,
            @RequestBody DecisionRequest request) {
        if (request == null || request.getExpectedVersion() == null) {
            throw new IllegalArgumentException("principal correction decision request is invalid");
        }
        return success(response(applicationService.decide(correctionNo,
                new SettlementPrincipalCorrectionDecisionCommand(
                        request.getRequestKey(), request.getExpectedVersion(), request.getDecision(),
                        request.getComment(), operator(request.getOperatorId(), request.getOperatorName(),
                        request.getRoleSnapshot(), request.getClientIp(), request.getUserAgent(),
                        request.getOperationTime())))));
    }

    private SettlementOperatorSnapshot operator(Long accountId,
                                                String accountName,
                                                String roleSnapshot,
                                                String clientIp,
                                                String userAgent,
                                                LocalDateTime operationTime) {
        return new SettlementOperatorSnapshot(accountId, accountName, roleSnapshot,
                clientIp, userAgent, operationTime);
    }

    private CommandResponse response(SettlementPrincipalCorrectionCommandResult result) {
        CommandResponse response = new CommandResponse();
        response.setCorrectionNo(result.correctionNo());
        response.setCorrectionStatus(result.correctionStatus());
        response.setOriginalBatchNo(result.originalBatchNo());
        response.setMerchantId(result.merchantId());
        response.setCurrency(result.currency());
        response.setCorrectionDirection(result.correctionDirection());
        response.setCorrectionAmount(result.correctionAmount());
        response.setItemCount(result.itemCount());
        response.setProjectionCompletedCount(result.projectionCompletedCount());
        response.setVersion(result.version());
        return response;
    }
}
