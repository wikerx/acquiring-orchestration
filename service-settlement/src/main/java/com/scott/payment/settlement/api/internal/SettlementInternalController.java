package com.scott.payment.settlement.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchCommandRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchCommandResponse;
import com.scott.payment.settlement.application.SettlementBatchCommandApplicationService;
import com.scott.payment.settlement.dto.SettlementCommandAudit;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementInternalController
 * @date : 2026-08-26 21:10
 * @email : scott_x@163.com
 * @description : 仅供 service-admin 通过 HMAC 调用的结算批次命令接口。
 * @status : create
 */
@RestController
@RequestMapping("/internal/settlement/v1/batches")
public class SettlementInternalController {

    private final SettlementBatchCommandApplicationService commandService;

    public SettlementInternalController(SettlementBatchCommandApplicationService commandService) {
        this.commandService = commandService;
    }

    /**
     * 入账前取消并释放候选；期望版本防止页面基于过期状态操作。
     *
     * @param settlementBatchNo 待取消正式结算批次号
     * @param request service-admin 注入可信操作人与请求幂等键的内部命令
     * @return 取消后的批次状态和实际释放候选数
     */
    @PostMapping("/{settlementBatchNo}/cancel")
    public CommonResult<BatchCommandResponse> cancel(
            @PathVariable("settlementBatchNo") String settlementBatchNo,
            @RequestBody BatchCommandRequest request) {
        validateCommand(request);
        int released = commandService.cancelBeforePosting(
                settlementBatchNo, request.getExpectedVersion(), commandAudit(request), LocalDateTime.now());
        BatchCommandResponse response = new BatchCommandResponse();
        response.setSettlementBatchNo(settlementBatchNo);
        response.setResultBatchNo(settlementBatchNo);
        response.setResultStatus("CANCELLED");
        response.setReleasedCandidateCount(released);
        return success(response);
    }

    /**
     * 校验可信 Admin 注入的命令字段，防止不完整主体进入业务审计。
     *
     * @param request service-admin 构造的内部批次命令
     * @throws IllegalArgumentException 操作人、版本、原因或审计环境不完整时抛出
     */
    private void validateCommand(BatchCommandRequest request) {
        if (request == null || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || request.getRequestKey() == null || request.getRequestKey().isBlank()
                || request.getRequestKey().trim().length() > 64
                || request.getReason() == null || request.getReason().isBlank()
                || request.getReason().trim().length() > 400
                || request.getOperatorId() == null || request.getOperatorId() <= 0
                || request.getOperatorName() == null || request.getOperatorName().isBlank()
                || request.getOperatorName().trim().length() > 128
                || request.getRoleSnapshot() == null || request.getRoleSnapshot().isBlank()
                || request.getRoleSnapshot().trim().length() > 1000
                || request.getClientIp() == null || request.getClientIp().isBlank()
                || request.getClientIp().trim().length() > 64
                || request.getUserAgent() == null || request.getUserAgent().isBlank()
                || request.getUserAgent().trim().length() > 500
                || request.getOperationTime() == null) {
            throw new IllegalArgumentException("settlement management command is invalid");
        }
    }

    /**
     * 将内部请求转换为不可变命令审计，service-settlement 不从浏览器字段自行推断操作人。
     *
     * @param request 已完成可信字段校验的内部批次命令
     * @return 请求幂等键、原因和可信操作人快照
     */
    private SettlementCommandAudit commandAudit(BatchCommandRequest request) {
        SettlementOperatorSnapshot operator = new SettlementOperatorSnapshot(
                request.getOperatorId(), request.getOperatorName(), request.getRoleSnapshot(),
                request.getClientIp(), request.getUserAgent(), request.getOperationTime());
        return new SettlementCommandAudit(request.getRequestKey(), request.getReason(), operator);
    }
}
