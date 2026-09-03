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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingInternalController
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分内部协议适配层，仅允许 Admin 与 Job 通过 HMAC、防重放和调用方白名单访问；只做参数转换并委托领域服务，不信任浏览器传入的操作人或目标状态。
 * @status : update
 */
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

    /**
     * 查询单季度清分记录，调用方必须先通过内部服务鉴权。
     *
     * @param request 半开时间窗口、筛选项和主键游标
     * @return 当前权威修订的游标查询结果
     */
    @PostMapping("/transactions/search")
    public CommonResult<ClearingRecordSearchResponse> search(
            @RequestBody ClearingRecordSearchRequest request) {
        return success(queryService.search(request));
    }

    /**
     * 按交易号和真实季度分片时间查询清分详情。
     *
     * @param transactionId 动作交易号
     * @param transactionDateTime 动作季度分片时间
     * @return 清分状态、修订和不可变资金明细
     */
    @GetMapping("/transactions/{transactionId}")
    public CommonResult<ClearingRecordDetailResponse> detail(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime) {
        return success(queryService.detail(transactionId, transactionDateTime));
    }

    /**
     * 重新排期一条可重试清分失败，不允许调用方指定目标状态。
     *
     * @param transactionId 动作交易号
     * @param request 预期版本、分片时间、原因和可信操作人
     * @return 已持久化的排期结果
     */
    @PostMapping("/transactions/{transactionId}/retry")
    public CommonResult<ClearingCommandResponse> retry(
            @PathVariable("transactionId") String transactionId,
            @RequestBody ClearingRetryRequest request) {
        return success(commandService.retry(transactionId, request));
    }

    /**
     * 提交人工复核固定命令，由领域状态机决定合法目标状态。
     *
     * @param transactionId 动作交易号
     * @param request 复核决定、预期版本和操作审计信息
     * @return 状态机提交结果
     */
    @PostMapping("/transactions/{transactionId}/review")
    public CommonResult<ClearingCommandResponse> review(
            @PathVariable("transactionId") String transactionId,
            @RequestBody ClearingReviewRequest request) {
        return success(commandService.review(transactionId, request));
    }

    /**
     * 对未结算动作执行指定费用版本重算并保留旧修订。
     *
     * @param transactionId 动作交易号
     * @param request 目标费用版本、当前修订和操作审计信息
     * @return 新清分修订结果
     */
    @PostMapping("/transactions/{transactionId}/recalculate")
    public CommonResult<ClearingCommandResponse> recalculate(
            @PathVariable("transactionId") String transactionId,
            @RequestBody ClearingRecalculateRequest request) {
        return success(commandService.recalculate(transactionId, request));
    }

    /**
     * 扫描清分补偿候选；是否真正恢复由请求模式和服务端状态共同决定。
     *
     * @param request 单季度扫描范围、数量上限和预览/执行模式
     * @return 每条候选的稳定处置结果
     */
    @PostMapping("/compensations/scan")
    public CommonResult<CompensationScanResponse> compensate(
            @RequestBody CompensationScanRequest request) {
        return success(compensationService.scan(request, LocalDateTime.now(java.time.Clock.systemUTC())));
    }

    /**
     * 提交冻结原保证金状态版本的标签币种差额申请。
     *
     * @param request 已含可信 Maker 快照和预期保证金状态版本的内部命令
     * @return 新建或幂等回放的调整申请
     */
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

    /**
     * 以申请版本 CAS 执行双人复核；核心服务负责同人拦截和资金事实原子提交。
     *
     * @param adjustmentNo 待复核保证金调整单号
     * @param request 已含可信 Checker 快照、决策和申请预期版本的内部命令
     * @return 复核后的调整状态及资金化动作身份
     */
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

    /**
     * 提交同商户、同不可变费用版本、同月份的阶梯期间重放申请。
     *
     * @param request 已含可信 Maker 快照和目标费用版本的内部命令
     * @return 新建或幂等回放的重放申请
     */
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

    /**
     * 双人复核阶梯期间重放；批准后自动冻结并由服务内调度器推进。
     *
     * @param replayNo 待复核阶梯期间重放单号
     * @param request 已含可信 Checker 快照、决策和申请预期版本的内部命令
     * @return 复核后的重放状态和处理统计
     */
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
