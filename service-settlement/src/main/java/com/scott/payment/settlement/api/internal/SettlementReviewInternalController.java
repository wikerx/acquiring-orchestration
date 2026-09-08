package com.scott.payment.settlement.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReviewCandidateReference;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReviewCommandResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReviewDecisionRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReviewSubmitRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ManualReviewPreviewLineResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ManualReviewPreviewRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ManualReviewStartRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ManualReviewTaskResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReviewDecisionTaskResponse;
import com.scott.payment.settlement.application.SettlementManualReviewApplicationService;
import com.scott.payment.settlement.application.SettlementReviewDecisionApplicationService;
import com.scott.payment.settlement.application.SettlementReviewOrderApplicationService;
import com.scott.payment.settlement.domain.model.SettlementBatchType;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.dto.SettlementReviewCommandResult;
import com.scott.payment.settlement.dto.SettlementReviewCreateCommand;
import com.scott.payment.settlement.dto.SettlementReviewDecisionCommand;
import com.scott.payment.settlement.dto.SettlementManualReviewModels.PreviewCommand;
import com.scott.payment.settlement.dto.SettlementManualReviewModels.StartCommand;
import com.scott.payment.settlement.dto.SettlementManualReviewModels.TaskResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewInternalController
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 仅供 service-admin 内部签名调用的人工结算预审命令入口；负责协议转换，不承载 Admin RBAC、数据范围或页面查询。
 * @status : create
 */
@RestController
@RequestMapping("/internal/settlement/v1/reviews")
public class SettlementReviewInternalController {

    private final SettlementReviewOrderApplicationService applicationService;
    private final SettlementManualReviewApplicationService manualReviewService;
    private final SettlementReviewDecisionApplicationService decisionTaskService;

    public SettlementReviewInternalController(SettlementReviewOrderApplicationService applicationService) {
        this(applicationService, null, null);
    }

    public SettlementReviewInternalController(SettlementReviewOrderApplicationService applicationService,
                                              SettlementManualReviewApplicationService manualReviewService) {
        this(applicationService, manualReviewService, null);
    }

    @Autowired
    public SettlementReviewInternalController(SettlementReviewOrderApplicationService applicationService,
                                              SettlementManualReviewApplicationService manualReviewService,
                                              SettlementReviewDecisionApplicationService decisionTaskService) {
        this.applicationService = applicationService;
        this.manualReviewService = manualReviewService;
        this.decisionTaskService = decisionTaskService;
    }

    @PostMapping("/manual-transaction-tasks/preview")
    public CommonResult<ManualReviewTaskResponse> previewManualTransaction(
            @RequestBody ManualReviewPreviewRequest request) {
        return previewManual(request, "REGULAR");
    }

    @PostMapping("/manual-reserve-tasks/preview")
    public CommonResult<ManualReviewTaskResponse> previewManualReserve(
            @RequestBody ManualReviewPreviewRequest request) {
        return previewManual(request, "RESERVE_RELEASE");
    }

    private CommonResult<ManualReviewTaskResponse> previewManual(
            ManualReviewPreviewRequest request, String reviewType) {
        if (request == null) {
            throw new IllegalArgumentException("manual settlement preview request is required");
        }
        TaskResult result = manualReviews().preview(new PreviewCommand(
                reviewType, request.getRequestKey(), request.getMerchantId(), request.getSettlementProfileId(),
                request.getPaymentType(), request.getPaymentMethod(), request.getReason(),
                operator(request.getOperatorId(), request.getOperatorName(), request.getRoleSnapshot(),
                        request.getClientIp(), request.getUserAgent(), request.getOperationTime())));
        return success(manualResponse(result));
    }

    @PostMapping("/manual-transaction-tasks/{taskNo}/start")
    public CommonResult<ManualReviewTaskResponse> startManualTransaction(
            @PathVariable("taskNo") String taskNo,
            @RequestBody ManualReviewStartRequest request) {
        if (request == null || request.getExpectedVersion() == null) {
            throw new IllegalArgumentException("manual settlement start request is required");
        }
        return success(manualResponse(manualReviews().start(taskNo,
                new StartCommand(request.getRequestKey(), request.getExpectedVersion()), "REGULAR")));
    }

    @GetMapping("/manual-transaction-tasks/{taskNo}")
    public CommonResult<ManualReviewTaskResponse> manualTransactionTask(
            @PathVariable("taskNo") String taskNo) {
        return success(manualResponse(manualReviews().get(taskNo, "REGULAR")));
    }

    @PostMapping("/manual-reserve-tasks/{taskNo}/start")
    public CommonResult<ManualReviewTaskResponse> startManualReserve(
            @PathVariable("taskNo") String taskNo,
            @RequestBody ManualReviewStartRequest request) {
        if (request == null || request.getExpectedVersion() == null) {
            throw new IllegalArgumentException("manual settlement start request is required");
        }
        return success(manualResponse(manualReviews().start(taskNo,
                new StartCommand(request.getRequestKey(), request.getExpectedVersion()),
                "RESERVE_RELEASE")));
    }

    @GetMapping("/manual-reserve-tasks/{taskNo}")
    public CommonResult<ManualReviewTaskResponse> manualReserveTask(
            @PathVariable("taskNo") String taskNo) {
        return success(manualResponse(manualReviews().get(taskNo, "RESERVE_RELEASE")));
    }

    /**
     * 提交人工结算预审，按候选版本独占锁定候选、汇率矩阵和计算指纹。
     *
     * @param request 候选引用、业务窗口、请求幂等键和可信 Maker 快照
     * @return 待复核预审单的稳定标识、金额摘要和版本
     * @throws IllegalArgumentException 请求结构或候选引用不合法时抛出
     * @throws IllegalStateException 候选混维、版本过期或资金事实不完整时抛出
     */
    @PostMapping
    public CommonResult<ReviewCommandResponse> submit(@RequestBody ReviewSubmitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("settlement review submit request is required");
        }
        List<SettlementReviewCreateCommand.CandidateReference> candidates = request.getCandidates() == null
                ? List.of() : request.getCandidates().stream().map(this::candidate).toList();
        SettlementReviewCommandResult result = applicationService.submit(new SettlementReviewCreateCommand(
                request.getRequestKey(), SettlementBatchType.valueOf(request.getReviewType()),
                request.getBusinessDate(), request.getCutoffBeginTime(), request.getCutoffEndTime(),
                candidates, request.getReason(), operator(request.getOperatorId(), request.getOperatorName(),
                request.getRoleSnapshot(), request.getClientIp(), request.getUserAgent(),
                request.getOperationTime())));
        return success(response(result));
    }

    /**
     * 审批、拒绝或取消待处理预审单；批准时复算指纹并将锁定候选转入正式批次。
     *
     * @param reviewOrderNo 待决策预审单号
     * @param request 决策幂等键、期望版本、意见和可信 Checker 快照
     * @return 决策后的预审状态、正式批次号和冻结金额摘要
     * @throws IllegalArgumentException 请求结构不合法时抛出
     * @throws IllegalStateException Maker-Checker、状态、版本或复算一致性失败时抛出
     */
    @PostMapping("/{reviewOrderNo}/decisions")
    public CommonResult<ReviewCommandResponse> decide(
            @PathVariable("reviewOrderNo") String reviewOrderNo,
            @RequestBody ReviewDecisionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("settlement review decision request is required");
        }
        SettlementReviewCommandResult result = applicationService.decide(reviewOrderNo,
                new SettlementReviewDecisionCommand(request.getRequestKey(), request.getExpectedVersion(),
                        request.getDecision(), request.getComment(),
                        operator(request.getOperatorId(), request.getOperatorName(), request.getRoleSnapshot(),
                                request.getClientIp(), request.getUserAgent(), request.getOperationTime())));
        return success(response(result));
    }

    /** 大批量手动预审单使用后台分段决策，接口立即返回可恢复任务。 */
    @PostMapping("/{reviewOrderNo}/decision-tasks")
    public CommonResult<ReviewDecisionTaskResponse> submitDecisionTask(
            @PathVariable("reviewOrderNo") String reviewOrderNo,
            @RequestBody ReviewDecisionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("settlement review decision request is required");
        }
        return success(decisionResponse(decisionTasks().submit(reviewOrderNo,
                new SettlementReviewDecisionCommand(request.getRequestKey(), request.getExpectedVersion(),
                        request.getDecision(), request.getComment(),
                        operator(request.getOperatorId(), request.getOperatorName(), request.getRoleSnapshot(),
                                request.getClientIp(), request.getUserAgent(), request.getOperationTime())))));
    }

    @GetMapping("/decision-tasks/{taskNo}")
    public CommonResult<ReviewDecisionTaskResponse> decisionTask(
            @PathVariable("taskNo") String taskNo) {
        return success(decisionResponse(decisionTasks().get(taskNo)));
    }

    private SettlementReviewCreateCommand.CandidateReference candidate(ReviewCandidateReference row) {
        if (row == null || row.getExpectedVersion() == null) {
            throw new IllegalArgumentException("settlement review candidate reference is invalid");
        }
        return new SettlementReviewCreateCommand.CandidateReference(
                row.getCandidateId(), row.getExpectedVersion());
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

    private ReviewCommandResponse response(SettlementReviewCommandResult result) {
        ReviewCommandResponse response = new ReviewCommandResponse();
        response.setReviewOrderNo(result.reviewOrderNo());
        response.setReviewStatus(result.reviewStatus());
        response.setSettlementBatchNo(result.settlementBatchNo());
        response.setCandidateCount(result.candidateCount());
        response.setTargetCurrency(result.targetCurrency());
        response.setTargetCurrencyExponent(result.targetCurrencyExponent());
        response.setNetDirection(result.netDirection());
        response.setNetAmount(result.netAmount());
        response.setVersion(result.version());
        return response;
    }

    private SettlementManualReviewApplicationService manualReviews() {
        if (manualReviewService == null) {
            throw new IllegalStateException("manual settlement review service is unavailable");
        }
        return manualReviewService;
    }

    private SettlementReviewDecisionApplicationService decisionTasks() {
        if (decisionTaskService == null) {
            throw new IllegalStateException("settlement review decision task service is unavailable");
        }
        return decisionTaskService;
    }

    private ManualReviewTaskResponse manualResponse(TaskResult result) {
        ManualReviewTaskResponse response = new ManualReviewTaskResponse();
        response.setTaskNo(result.taskNo());
        response.setReviewOrderNo(result.reviewOrderNo());
        response.setTaskStatus(result.taskStatus());
        response.setReviewType(result.reviewType());
        response.setMerchantId(result.merchantId());
        response.setSettlementProfileId(result.settlementProfileId());
        response.setSettlementAccountId(result.settlementAccountId());
        response.setTargetCurrency(result.targetCurrency());
        response.setTargetCurrencyExponent(result.targetCurrencyExponent());
        response.setPaymentType(result.paymentType());
        response.setPaymentMethod(result.paymentMethod());
        response.setSubmitReason(result.submitReason());
        response.setBusinessDate(result.businessDate());
        response.setCutoffEndTime(result.cutoffEndTime());
        response.setSnapshotMaxCandidateId(result.snapshotMaxCandidateId());
        response.setExpectedCandidateCount(result.expectedCandidateCount());
        response.setProcessedCandidateCount(result.processedCandidateCount());
        response.setLockedCandidateCount(result.lockedCandidateCount());
        response.setProgressPercent(result.progressPercent());
        response.setInitialDelayUnit(result.initialDelayUnit());
        response.setInitialDelayDays(result.initialDelayDays());
        response.setRegularDelayDays(result.regularDelayDays());
        response.setSettlementFrequency(result.settlementFrequency());
        response.setFrequencyDay(result.frequencyDay());
        response.setPreview(result.preview().stream().map(line -> {
            ManualReviewPreviewLineResponse value = new ManualReviewPreviewLineResponse();
            value.setSourceCurrency(line.sourceCurrency());
            value.setSourceCurrencyExponent(line.sourceCurrencyExponent());
            value.setTransactionCount(line.transactionCount());
            value.setGrossAmount(line.grossAmount());
            value.setPlatformFeeAmount(line.platformFeeAmount());
            value.setReserveAmount(line.reserveAmount());
            value.setReleasedReserveAmount(line.releasedReserveAmount());
            value.setNetSettlementAmount(line.netSettlementAmount());
            value.setPendingFeeCount(line.pendingFeeCount());
            value.setReserveDelayUnit(line.reserveDelayUnit());
            value.setMinimumReserveDelayDays(line.minimumReserveDelayDays());
            value.setMaximumReserveDelayDays(line.maximumReserveDelayDays());
            value.setEarliestExpectedReleaseDate(line.earliestExpectedReleaseDate());
            value.setLatestExpectedReleaseDate(line.latestExpectedReleaseDate());
            return value;
        }).toList());
        response.setRetryCount(result.retryCount());
        response.setFailureCode(result.failureCode());
        response.setFailureMessage(result.failureMessage());
        response.setStartedTime(result.startedTime());
        response.setCompletedTime(result.completedTime());
        response.setVersion(result.version());
        return response;
    }

    private ReviewDecisionTaskResponse decisionResponse(
            com.scott.payment.settlement.dto.SettlementReviewDecisionModels.TaskResult result) {
        ReviewDecisionTaskResponse response = new ReviewDecisionTaskResponse();
        response.setTaskNo(result.taskNo());
        response.setReviewOrderNo(result.reviewOrderNo());
        response.setDecisionAction(result.decisionAction());
        response.setTaskStatus(result.taskStatus());
        response.setTotalSegmentCount(result.totalSegmentCount());
        response.setProcessedSegmentCount(result.processedSegmentCount());
        response.setResultBatchCount(result.resultBatchCount());
        response.setProgressPercent(result.progressPercent());
        response.setFirstSettlementBatchNo(result.firstSettlementBatchNo());
        response.setRetryCount(result.retryCount());
        response.setFailureCode(result.failureCode());
        response.setFailureMessage(result.failureMessage());
        response.setStartedTime(result.startedTime());
        response.setCompletedTime(result.completedTime());
        response.setVersion(result.version());
        return response;
    }
}
