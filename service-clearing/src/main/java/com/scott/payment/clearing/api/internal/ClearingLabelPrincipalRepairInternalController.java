package com.scott.payment.clearing.api.internal;

import com.scott.payment.clearing.api.internal.dto.ClearingLabelPrincipalRepairDTOs.Response;
import com.scott.payment.clearing.api.internal.dto.ClearingLabelPrincipalRepairDTOs.ReviewRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingLabelPrincipalRepairDTOs.SubmitRequest;
import com.scott.payment.clearing.service.ClearingLabelPrincipalRepairService;
import com.scott.payment.clearing.service.ClearingLabelPrincipalRepairService.RepairResult;
import com.scott.payment.clearing.service.ClearingLabelPrincipalRepairService.ReviewDecision;
import com.scott.payment.component.core.model.CommonResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Locale;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 标签本金历史修复内部入口；仅接受通过内部 HMAC、防重放和调用方白名单的 Admin 请求。
 */
@RestController
@RequestMapping("/internal/clearing/v1/principal-repairs")
public class ClearingLabelPrincipalRepairInternalController {

    private final ClearingLabelPrincipalRepairService repairService;

    public ClearingLabelPrincipalRepairInternalController(
            ClearingLabelPrincipalRepairService repairService) {
        this.repairService = repairService;
    }

    /**
     * 提交未结算历史本金修复申请，提交阶段不修改资金事实。
     */
    @PostMapping
    public CommonResult<Response> submit(@RequestBody SubmitRequest request) {
        RepairResult result = repairService.submit(new ClearingLabelPrincipalRepairService.SubmitCommand(
                request.getRequestKey(), request.getTransactionId(), request.getTransactionDateTime(),
                required(request.getExpectedFinanceStateVersion(), "expected finance state version"),
                required(request.getExpectedClearingRevision(), "expected clearing revision"),
                required(request.getExpectedCandidateVersion(), "expected candidate version"),
                request.getReason(), request.getSubmitOperator(), Instant.now()));
        return success(response(result));
    }

    /**
     * 复核修复申请，批准后由清分服务原子生成新修订和新候选。
     */
    @PostMapping("/{repairNo}/review")
    public CommonResult<Response> review(@PathVariable("repairNo") String repairNo,
                                         @RequestBody ReviewRequest request) {
        RepairResult result = repairService.review(new ClearingLabelPrincipalRepairService.ReviewCommand(
                repairNo, required(request.getExpectedRequestVersion(), "expected request version"),
                ReviewDecision.valueOf(requiredDecision(request.getDecision())),
                request.getReviewComment(), request.getReviewOperator(), Instant.now()));
        return success(response(result));
    }

    private int required(Integer value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private long required(Long value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private String requiredDecision(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("review decision is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Response response(RepairResult result) {
        Response response = new Response();
        response.setRepairNo(result.repairNo());
        response.setStatus(result.status());
        response.setTransactionId(result.transactionId());
        response.setTransactionDateTime(result.transactionDateTime());
        response.setOldClearingRevision(result.oldClearingRevision());
        response.setNewClearingRevision(result.newClearingRevision());
        response.setNewCandidateNo(result.newCandidateNo());
        response.setVersion(result.version());
        return response;
    }
}
