package com.scott.payment.clearing.api.internal;

import com.scott.payment.clearing.api.internal.dto.ClearingCompensationDTOs.CompensationScanRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingCompensationDTOs.CompensationScanResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingCommandResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordDetailResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordSearchRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordSearchResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecalculateRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRetryRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingReviewRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ReserveAdjustmentResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ReserveAdjustmentReviewRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ReserveAdjustmentSubmitRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.TierPeriodReplayResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.TierPeriodReplayReviewRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.TierPeriodReplaySubmitRequest;
import com.scott.payment.clearing.service.ClearingCompensationService;
import com.scott.payment.clearing.service.ClearingManagementCommandService;
import com.scott.payment.clearing.service.ClearingQueryService;
import com.scott.payment.clearing.service.ReserveAdjustmentService;
import com.scott.payment.clearing.service.ReserveAdjustmentService.ReviewDecision;
import com.scott.payment.clearing.service.TierPeriodReplayService;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentDirection;
import com.scott.payment.component.core.model.CommonResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Locale;

import static com.scott.payment.component.core.model.CommonResult.success;

/** 仅供 Admin 与 Job 通过 HMAC 调用的清分查询、处置和补偿接口。 */
@RestController
@RequestMapping("/internal/clearing/v1")
public class ClearingInternalController {

    private final ClearingQueryService queryService;
    private final ClearingManagementCommandService commandService;
    private final ClearingCompensationService compensationService;
    private final ReserveAdjustmentService reserveAdjustmentService;
    private final TierPeriodReplayService tierPeriodReplayService;

    public ClearingInternalController(ClearingQueryService queryService,
                                      ClearingManagementCommandService commandService,
                                      ClearingCompensationService compensationService,
                                      ReserveAdjustmentService reserveAdjustmentService,
                                      TierPeriodReplayService tierPeriodReplayService) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.compensationService = compensationService;
        this.reserveAdjustmentService = reserveAdjustmentService;
        this.tierPeriodReplayService = tierPeriodReplayService;
    }

    @PostMapping("/transactions/search")
    public CommonResult<ClearingRecordSearchResponse> search(
            @RequestBody ClearingRecordSearchRequest request) {
        return success(queryService.search(request));
    }

    @GetMapping("/transactions/{transactionId}")
    public CommonResult<ClearingRecordDetailResponse> detail(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime) {
        return success(queryService.detail(transactionId, transactionDateTime));
    }

    @PostMapping("/transactions/{transactionId}/retry")
    public CommonResult<ClearingCommandResponse> retry(
            @PathVariable("transactionId") String transactionId,
            @RequestBody ClearingRetryRequest request) {
        return success(commandService.retry(transactionId, request));
    }

    @PostMapping("/transactions/{transactionId}/review")
    public CommonResult<ClearingCommandResponse> review(
            @PathVariable("transactionId") String transactionId,
            @RequestBody ClearingReviewRequest request) {
        return success(commandService.review(transactionId, request));
    }

    @PostMapping("/transactions/{transactionId}/recalculate")
    public CommonResult<ClearingCommandResponse> recalculate(
            @PathVariable("transactionId") String transactionId,
            @RequestBody ClearingRecalculateRequest request) {
        return success(commandService.recalculate(transactionId, request));
    }

    @PostMapping("/compensations/scan")
    public CommonResult<CompensationScanResponse> compensate(
            @RequestBody CompensationScanRequest request) {
        return success(compensationService.scan(request, LocalDateTime.now(java.time.Clock.systemUTC())));
    }

    /** 提交冻结原保证金状态版本的标签币种差额申请。 */
    @PostMapping("/reserve-adjustments")
    public CommonResult<ReserveAdjustmentResponse> submitReserveAdjustment(
            @RequestBody ReserveAdjustmentSubmitRequest request) {
        ReserveAdjustmentService.ReserveAdjustmentResult result = reserveAdjustmentService.submit(
                new ReserveAdjustmentService.SubmitCommand(
                        request.getRequestKey(), request.getReserveStateId(), request.getOriginalTransactionId(),
                        request.getOriginalTransactionDateTime(), requiredVersion(request.getExpectedReserveStateVersion()),
                        ReserveAdjustmentDirection.valueOf(requiredEnum(request.getDirection())),
                        request.getAdjustmentAmount(), request.getRequestedReleaseDate(), request.getReason(),
                        request.getSubmitOperator(), Instant.now()));
        return success(adjustmentResponse(result));
    }

    /** 以申请版本 CAS 执行双人复核；核心服务负责同人拦截和资金事实原子提交。 */
    @PostMapping("/reserve-adjustments/{adjustmentNo}/review")
    public CommonResult<ReserveAdjustmentResponse> reviewReserveAdjustment(
            @PathVariable("adjustmentNo") String adjustmentNo,
            @RequestBody ReserveAdjustmentReviewRequest request) {
        ReserveAdjustmentService.ReserveAdjustmentResult result = reserveAdjustmentService.review(
                new ReserveAdjustmentService.ReviewCommand(
                        adjustmentNo, requiredVersion(request.getExpectedRequestVersion()),
                        ReviewDecision.valueOf(requiredEnum(request.getDecision())), request.getReviewComment(),
                        request.getReviewOperator(), Instant.now()));
        return success(adjustmentResponse(result));
    }

    /** 提交同商户、同不可变费用版本、同月份的阶梯期间重放申请。 */
    @PostMapping("/tier-period-replays")
    public CommonResult<TierPeriodReplayResponse> submitTierPeriodReplay(
            @RequestBody TierPeriodReplaySubmitRequest request) {
        TierPeriodReplayService.ReplayResult result = tierPeriodReplayService.submit(
                new TierPeriodReplayService.SubmitCommand(
                        request.getRequestKey(), request.getMerchantId(), requiredVersion(request.getFeePlanId()),
                        requiredVersion(request.getFeePlanVersionId()), requiredVersion(request.getTriggerFeeRuleId()),
                        request.getPeriodKey(), request.getReason(), request.getSubmitOperator(), Instant.now()));
        return success(tierReplayResponse(result));
    }

    /** 双人复核阶梯期间重放；批准后自动冻结并由服务内调度器推进。 */
    @PostMapping("/tier-period-replays/{replayNo}/review")
    public CommonResult<TierPeriodReplayResponse> reviewTierPeriodReplay(
            @PathVariable("replayNo") String replayNo,
            @RequestBody TierPeriodReplayReviewRequest request) {
        TierPeriodReplayService.ReplayResult result = tierPeriodReplayService.review(
                new TierPeriodReplayService.ReviewCommand(
                        replayNo, requiredVersion(request.getExpectedRequestVersion()),
                        TierPeriodReplayService.ReviewDecision.valueOf(requiredEnum(request.getDecision())),
                        request.getReviewComment(), request.getReviewOperator(), Instant.now()));
        return success(tierReplayResponse(result));
    }

    private long requiredVersion(Long version) {
        if (version == null) {
            throw new IllegalArgumentException("expected version is required");
        }
        return version;
    }

    private String requiredEnum(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("enum value is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private ReserveAdjustmentResponse adjustmentResponse(
            ReserveAdjustmentService.ReserveAdjustmentResult result) {
        ReserveAdjustmentResponse response = new ReserveAdjustmentResponse();
        response.setAdjustmentNo(result.adjustmentNo());
        response.setStatus(result.status());
        response.setTransactionId(result.transactionId());
        response.setSourceRevision(result.sourceRevision());
        response.setVersion(result.version());
        return response;
    }

    private TierPeriodReplayResponse tierReplayResponse(TierPeriodReplayService.ReplayResult result) {
        TierPeriodReplayResponse response = new TierPeriodReplayResponse();
        response.setReplayNo(result.replayNo());
        response.setStatus(result.status());
        response.setItemCount(result.itemCount());
        response.setCompletedCount(result.completedCount());
        response.setVersion(result.version());
        return response;
    }
}
