package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.clearing.ClearingInternalClient;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ActionRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.CommandResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.DetailResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalActionRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalRecalculateRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchItem;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchItemResult;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculationOptionsResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalReserveAdjustmentReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalReserveAdjustmentSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ReserveAdjustmentResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ReserveAdjustmentReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ReserveAdjustmentSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.SearchRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.Summary;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalTierPeriodReplayReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalTierPeriodReplaySubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.TierPeriodReplayResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.TierPeriodReplayReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.TierPeriodReplaySubmitRequest;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.admin.service.AdminClearingQueryService;
import com.scott.payment.admin.service.AdminClearingFeeVersionQueryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminClearingApplicationService
 * @date : 2026-09-01 23:00
 * @email : scott_x@163.com
 * @description : Admin 清分查询与人工命令编排；查询使用管理侧数据源，命令在校验版本和请求范围后注入可信操作人并调用 clearing 服务。
 * @status : update
 */
@Service
public class AdminClearingApplicationService {

    private static final Set<String> ADJUSTMENT_DIRECTIONS = Set.of("DEBIT", "CREDIT");
    private static final Set<String> REVIEW_DECISIONS = Set.of("APPROVE", "REJECT");
    private static final Set<String> RECALCULABLE_STATUSES = Set.of("CLEARED", "NOT_REQUIRED");
    /**
     * {@code MAX_RECALCULATION_BATCH_SIZE}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int MAX_RECALCULATION_BATCH_SIZE = 20;

    private final ClearingInternalClient client;
    private final AdminClearingQueryService queryService;
    private final AdminClearingFeeVersionQueryService feeVersionQueryService;

    public AdminClearingApplicationService(ClearingInternalClient client,
                                           AdminClearingQueryService queryService,
                                           AdminClearingFeeVersionQueryService feeVersionQueryService) {
        this.client = client;
        this.queryService = queryService;
        this.feeVersionQueryService = feeVersionQueryService;
    }

    /**
     * 查询当前 Admin 数据范围内的清分记录。
     *
     * @param request 清分查询条件
     * @return 清分记录分页
     */
    public PageResult<Summary> search(SearchRequest request) {
        currentOperator();
        return queryService.search(request);
    }

    /**
     * 按交易号和交易时间从本地逻辑数据源查询清分详情。
     *
     * @param transactionId 平台交易号
     * @param transactionDateTime 交易时间，用于物理季度定位
     * @return 清分详情
     */
    public DetailResponse detail(String transactionId, LocalDateTime transactionDateTime) {
        currentOperator();
        if (!StringUtils.hasText(transactionId) || transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return queryService.detail(transactionId.trim(), transactionDateTime);
    }

    /**
     * 查询当前商户方案允许用于重算的不可变费用版本。
     *
     * @param merchantId 商户号
     * @param feePlanId 费用模板主键
     * @return 已发布且属于该商户方案的版本列表
     */
    public RecalculationOptionsResponse recalculationOptions(String merchantId, Long feePlanId) {
        currentOperator();
        return feeVersionQueryService.listOptions(merchantId, feePlanId);
    }

    /**
     * 使用可信 Admin 操作人提交幂等清分重试。
     *
     * @param transactionId 待重试平台交易号
     * @param request 动作分片时间、期望版本和原因
     * @return 清分服务幂等命令结果
     */
    public CommandResponse retry(String transactionId, ActionRequest request) {
        return client.retry(transactionId, action(transactionId, request));
    }

    /**
     * 使用可信 Admin 操作人将清分记录升级为人工复核。
     *
     * @param transactionId 待升级平台交易号
     * @param request 动作分片时间、期望版本和原因
     * @return 清分服务幂等命令结果
     */
    public CommandResponse review(String transactionId, ActionRequest request) {
        return client.review(transactionId, action(transactionId, request));
    }

    /**
     * 对尚未结算的清分事实执行单笔版本 CAS 重算。
     *
     * @param transactionId 平台交易号
     * @param request 交易时间、期望版本、目标费用版本和原因
     * @return 清分服务幂等命令结果
     */
    public CommandResponse recalculate(String transactionId, RecalculateRequest request) {
        validateAction(transactionId, request);
        if (request.getExpectedClearingRevision() == null || request.getExpectedClearingRevision() < 1
                || request.getTargetFeePlanId() == null || request.getTargetFeePlanId() < 1
                || request.getTargetFeePlanVersionId() == null || request.getTargetFeePlanVersionId() < 1) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalRecalculateRequest command = new InternalRecalculateRequest();
        command.setTransactionDateTime(request.getTransactionDateTime());
        command.setExpectedVersion(request.getExpectedVersion());
        command.setExpectedClearingRevision(request.getExpectedClearingRevision());
        command.setTargetFeePlanId(request.getTargetFeePlanId());
        command.setTargetFeePlanVersionId(request.getTargetFeePlanVersionId());
        command.setReason(request.getReason().trim());
        command.setOperator(currentOperator());
        return client.recalculate(transactionId, command);
    }

    /**
     * 有界批量重算，同一商户方案共用目标不可变版本，但每笔交易独立 CAS 和返回结果。
     *
     * @param request 最多 20 笔交易、统一目标费用版本和原因
     * @return 不因单笔失败回滚其他交易的逐笔结果汇总
     */
    public RecalculateBatchResponse batchRecalculate(RecalculateBatchRequest request) {
        validateBatchRequest(request);
        String operator = currentOperator();
        List<Summary> currentRows = queryService.findByReferences(request.getRecords());
        validateBatchScope(request, currentRows);
        Map<String, Summary> currentByReference = new HashMap<>();
        currentRows.forEach(row -> currentByReference.put(referenceKey(
                row.getTransactionId(), row.getTransactionDateTime()), row));

        List<RecalculateBatchItemResult> results = new ArrayList<>(request.getRecords().size());
        for (RecalculateBatchItem item : request.getRecords()) {
            Summary current = currentByReference.get(referenceKey(
                    item.getTransactionId(), item.getTransactionDateTime()));
            if (!isCurrentAndRecalculable(item, current)) {
                results.add(batchResult(item, false, "STALE_OR_INELIGIBLE",
                        "clearing record is missing, stale, ineligible or already settled"));
                continue;
            }
            try {
                CommandResponse response = client.recalculate(item.getTransactionId().trim(),
                        batchCommand(item, request, operator));
                results.add(batchResult(item, true,
                        response == null || !StringUtils.hasText(response.getResult())
                                ? "COMPLETED" : response.getResult(), null));
            } catch (RuntimeException exception) {
                results.add(batchResult(item, false, "FAILED",
                        StringUtils.hasText(exception.getMessage())
                                ? exception.getMessage() : "clearing recalculation failed"));
            }
        }
        int successCount = (int) results.stream().filter(RecalculateBatchItemResult::isSuccess).count();
        RecalculateBatchResponse response = new RecalculateBatchResponse();
        response.setRequestedCount(results.size());
        response.setSuccessCount(successCount);
        response.setFailureCount(results.size() - successCount);
        response.setResults(results);
        return response;
    }

    /** 校验批量数量、请求内唯一交易引用、期望版本和统一目标费用版本。 */
    private void validateBatchRequest(RecalculateBatchRequest request) {
        if (request == null || request.getRecords() == null || request.getRecords().size() < 2
                || request.getRecords().size() > MAX_RECALCULATION_BATCH_SIZE
                || request.getTargetFeePlanId() == null || request.getTargetFeePlanId() < 1
                || request.getTargetFeePlanVersionId() == null || request.getTargetFeePlanVersionId() < 1
                || !validText(request.getReason(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        Set<String> references = new HashSet<>();
        for (RecalculateBatchItem item : request.getRecords()) {
            if (item == null || !StringUtils.hasText(item.getTransactionId())
                    || item.getTransactionDateTime() == null || item.getExpectedVersion() == null
                    || item.getExpectedVersion() < 0 || item.getExpectedClearingRevision() == null
                    || item.getExpectedClearingRevision() < 1
                    || !references.add(referenceKey(item.getTransactionId(), item.getTransactionDateTime()))) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID);
            }
        }
    }

    /** 确保当前命中的交易属于同一商户、同一费用方案且与目标方案一致。 */
    private void validateBatchScope(RecalculateBatchRequest request, List<Summary> currentRows) {
        if (currentRows == null || currentRows.isEmpty()) {
            return;
        }
        Set<String> merchantIds = new HashSet<>();
        Set<Long> planIds = new HashSet<>();
        for (Summary row : currentRows) {
            if (!StringUtils.hasText(row.getMerchantId()) || row.getFeePlanId() == null) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID);
            }
            merchantIds.add(row.getMerchantId());
            planIds.add(row.getFeePlanId());
        }
        if (merchantIds.size() != 1 || planIds.size() != 1
                || !planIds.contains(request.getTargetFeePlanId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    /** 仅允许 CLEARED/NOT_REQUIRED 且 NOT_SETTLED 的当前版本重算，阻止终态和已结算事实被覆盖。 */
    private boolean isCurrentAndRecalculable(RecalculateBatchItem item, Summary current) {
        return current != null
                && RECALCULABLE_STATUSES.contains(current.getClearingStatus())
                && "NOT_SETTLED".equals(current.getSettlementStatus())
                && current.getClearingRevision() != null && current.getClearingRevision() >= 1
                && Objects.equals(item.getExpectedVersion(), current.getVersion())
                && Objects.equals(item.getExpectedClearingRevision(), current.getClearingRevision());
    }

    /**
     * 将批量重算项转换为内部命令，并把可信登录操作人和浏览器不可覆盖的版本前置条件注入命令。
     *
     * @param item 当前交易及其分片时间、期望版本
     * @param request 目标费用版本与重算原因
     * @param operator 从 Admin 登录上下文解析的操作人
     * @return 发往清分服务的单笔重算命令
     */
    private InternalRecalculateRequest batchCommand(RecalculateBatchItem item,
                                                    RecalculateBatchRequest request,
                                                    String operator) {
        InternalRecalculateRequest command = new InternalRecalculateRequest();
        command.setTransactionDateTime(item.getTransactionDateTime());
        command.setExpectedVersion(item.getExpectedVersion());
        command.setExpectedClearingRevision(item.getExpectedClearingRevision());
        command.setTargetFeePlanId(request.getTargetFeePlanId());
        command.setTargetFeePlanVersionId(request.getTargetFeePlanVersionId());
        command.setReason(request.getReason().trim());
        command.setOperator(operator);
        return command;
    }

    private RecalculateBatchItemResult batchResult(RecalculateBatchItem item, boolean success,
                                                   String result, String message) {
        RecalculateBatchItemResult response = new RecalculateBatchItemResult();
        response.setTransactionId(item.getTransactionId().trim());
        response.setTransactionDateTime(item.getTransactionDateTime());
        response.setSuccess(success);
        response.setResult(result);
        response.setMessage(message);
        return response;
    }

    private String referenceKey(String transactionId, LocalDateTime transactionDateTime) {
        return (transactionId == null ? "" : transactionId.trim()) + "\u0000" + transactionDateTime;
    }

    /**
     * 提交保证金标签币种差额申请，操作人只能来自 Admin 登录上下文。
     *
     * @param request 浏览器申请，不含操作人
     * @return 待复核申请状态
     */
    public ReserveAdjustmentResponse submitReserveAdjustment(ReserveAdjustmentSubmitRequest request) {
        validateReserveAdjustmentSubmit(request);
        InternalReserveAdjustmentSubmitRequest command = new InternalReserveAdjustmentSubmitRequest();
        command.setRequestKey(request.getRequestKey().trim());
        command.setReserveStateId(request.getReserveStateId().trim());
        command.setOriginalTransactionId(request.getOriginalTransactionId().trim());
        command.setOriginalTransactionDateTime(request.getOriginalTransactionDateTime());
        command.setExpectedReserveStateVersion(request.getExpectedReserveStateVersion());
        command.setDirection(request.getDirection().trim().toUpperCase(java.util.Locale.ROOT));
        command.setAdjustmentAmount(request.getAdjustmentAmount());
        command.setRequestedReleaseDate(request.getRequestedReleaseDate());
        command.setReason(request.getReason().trim());
        command.setSubmitOperator(currentOperator());
        return client.submitReserveAdjustment(command);
    }

    /**
     * 复核保证金差额申请，复核人只能来自 Admin 登录上下文。
     *
     * @param adjustmentNo 清分服务生成的调整申请号
     * @param request 复核决定和期望版本
     * @return 终态或幂等复核结果
     */
    public ReserveAdjustmentResponse reviewReserveAdjustment(
            String adjustmentNo, ReserveAdjustmentReviewRequest request) {
        if (!StringUtils.hasText(adjustmentNo) || request == null
                || request.getExpectedRequestVersion() == null || request.getExpectedRequestVersion() < 0
                || !StringUtils.hasText(request.getDecision())
                || !REVIEW_DECISIONS.contains(request.getDecision().trim().toUpperCase(java.util.Locale.ROOT))
                || !validText(request.getReviewComment(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalReserveAdjustmentReviewRequest command = new InternalReserveAdjustmentReviewRequest();
        command.setExpectedRequestVersion(request.getExpectedRequestVersion());
        command.setDecision(request.getDecision().trim().toUpperCase(java.util.Locale.ROOT));
        command.setReviewComment(request.getReviewComment().trim());
        command.setReviewOperator(currentOperator());
        return client.reviewReserveAdjustment(adjustmentNo.trim(), command);
    }

    /**
     * 提交不可变费用版本月度阶梯重放申请，操作人只能来自登录上下文。
     *
     * @param request 商户、费用版本、触发规则、月份、请求键和原因
     * @return 待复核重放申请
     */
    public TierPeriodReplayResponse submitTierPeriodReplay(TierPeriodReplaySubmitRequest request) {
        if (request == null || !validText(request.getRequestKey(), 128)
                || !validText(request.getMerchantId(), 64) || request.getFeePlanId() == null
                || request.getFeePlanId() < 1 || request.getFeePlanVersionId() == null
                || request.getFeePlanVersionId() < 1 || request.getTriggerFeeRuleId() == null
                || request.getTriggerFeeRuleId() < 1 || request.getPeriodKey() == null
                || !request.getPeriodKey().matches("\\d{6}") || !validText(request.getReason(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalTierPeriodReplaySubmitRequest command = new InternalTierPeriodReplaySubmitRequest();
        command.setRequestKey(request.getRequestKey().trim());
        command.setMerchantId(request.getMerchantId().trim());
        command.setFeePlanId(request.getFeePlanId());
        command.setFeePlanVersionId(request.getFeePlanVersionId());
        command.setTriggerFeeRuleId(request.getTriggerFeeRuleId());
        command.setPeriodKey(request.getPeriodKey());
        command.setReason(request.getReason().trim());
        command.setSubmitOperator(currentOperator());
        return client.submitTierPeriodReplay(command);
    }

    /**
     * 复核阶梯期间重放申请，复核人只能来自登录上下文。
     *
     * @param replayNo 重放申请号
     * @param request 决策、期望版本和复核意见
     * @return 终态或幂等复核结果
     */
    public TierPeriodReplayResponse reviewTierPeriodReplay(
            String replayNo, TierPeriodReplayReviewRequest request) {
        if (!StringUtils.hasText(replayNo) || request == null
                || request.getExpectedRequestVersion() == null || request.getExpectedRequestVersion() < 0
                || !StringUtils.hasText(request.getDecision())
                || !REVIEW_DECISIONS.contains(request.getDecision().trim().toUpperCase(java.util.Locale.ROOT))
                || !validText(request.getReviewComment(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalTierPeriodReplayReviewRequest command = new InternalTierPeriodReplayReviewRequest();
        command.setExpectedRequestVersion(request.getExpectedRequestVersion());
        command.setDecision(request.getDecision().trim().toUpperCase(java.util.Locale.ROOT));
        command.setReviewComment(request.getReviewComment().trim());
        command.setReviewOperator(currentOperator());
        return client.reviewTierPeriodReplay(replayNo.trim(), command);
    }

    private InternalActionRequest action(String transactionId, ActionRequest request) {
        validateAction(transactionId, request);
        InternalActionRequest command = new InternalActionRequest();
        command.setTransactionDateTime(request.getTransactionDateTime());
        command.setExpectedVersion(request.getExpectedVersion());
        command.setReason(request.getReason().trim());
        command.setOperator(currentOperator());
        return command;
    }

    private void validateAction(String transactionId, ActionRequest request) {
        if (!StringUtils.hasText(transactionId) || request == null || request.getTransactionDateTime() == null
                || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || !StringUtils.hasText(request.getReason()) || request.getReason().trim().length() > 400) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    /**
     * 校验保证金差额申请的方向、正金额、精度和释放日期组合，阻止非法资金语义进入清分状态机。
     *
     * @param request 浏览器提交的保证金差额申请
     * @throws ServiceException 身份、版本、金额或方向组合不合法时抛出
     */
    private void validateReserveAdjustmentSubmit(ReserveAdjustmentSubmitRequest request) {
        String direction = request == null || request.getDirection() == null
                ? null : request.getDirection().trim().toUpperCase(java.util.Locale.ROOT);
        if (request == null || !validText(request.getRequestKey(), 128)
                || !validText(request.getReserveStateId(), 64)
                || !validText(request.getOriginalTransactionId(), 64)
                || request.getOriginalTransactionDateTime() == null
                || request.getExpectedReserveStateVersion() == null
                || request.getExpectedReserveStateVersion() < 0
                || !ADJUSTMENT_DIRECTIONS.contains(direction)
                || request.getAdjustmentAmount() == null || request.getAdjustmentAmount().signum() <= 0
                || request.getAdjustmentAmount().scale() > 8
                || !validText(request.getReason(), 400)
                || ("DEBIT".equals(direction) && request.getRequestedReleaseDate() == null)
                || ("CREDIT".equals(direction) && request.getRequestedReleaseDate() != null)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private boolean validText(String value, int maxLength) {
        return StringUtils.hasText(value) && value.trim().length() <= maxLength;
    }

    /** 从可信内部认证上下文解析 Admin 操作人；不存在可信身份时拒绝请求。 */
    private String currentOperator() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        String name = StringUtils.hasText(account.getRealName()) ? account.getRealName() : account.getLoginAccount();
        return "admin-account:" + account.getAccountId() + "/" + name;
    }
}
