package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.settlement.SettlementInternalClient;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSummary;
import com.scott.payment.admin.service.AdminSettlementQueryService;
import com.scott.payment.admin.service.AdminSettlementReviewQueryService;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalBatchCommandRequest;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.CandidateSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.CandidateSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewDecisionTaskResumeRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDecisionTaskResumeRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalManualReviewPreviewRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ManualReviewPreviewRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ManualReviewStartRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ManualReviewTaskResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDecisionTaskResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewCandidateLine;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewCandidateSearchRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementApplicationService
 * @date : 2026-08-26 21:20
 * @email : scott_x@163.com
 * @description : Admin 结算管理用例编排；列表和详情调用本地只读查询，取消绑定可信操作人后调用结算服务。
 * @status : create
 */
@Service
public class AdminSettlementApplicationService {

    private static final String TRANSACTION_BATCH_DOMAIN = "TRANSACTION";
    private static final String RESERVE_BATCH_DOMAIN = "RESERVE";
    private static final Set<String> TRANSACTION_BATCH_TYPES = Set.of("REGULAR");
    private static final Set<String> RESERVE_BATCH_TYPES = Set.of("RESERVE_RELEASE", "ADJUSTMENT");

    private final SettlementInternalClient client;
    private final AdminSettlementQueryService queryService;
    private final AdminMerchantDataScopeResolver dataScopeResolver;
    private final AdminSettlementReviewQueryService reviewQueryService;

    @Autowired
    public AdminSettlementApplicationService(SettlementInternalClient client,
                                             AdminSettlementQueryService queryService,
                                             AdminMerchantDataScopeResolver dataScopeResolver,
                                             AdminSettlementReviewQueryService reviewQueryService) {
        this.client = client;
        this.queryService = queryService;
        this.dataScopeResolver = dataScopeResolver;
        this.reviewQueryService = reviewQueryService;
    }

    public AdminSettlementApplicationService(SettlementInternalClient client,
                                             AdminSettlementQueryService queryService,
                                             AdminMerchantDataScopeResolver dataScopeResolver) {
        this(client, queryService, dataScopeResolver, null);
    }

    /**
     * 在当前 Admin 商户数据范围内查询正式结算批次。
     *
     * @param request 最多 92 天业务日期窗口及分页过滤条件
     * @return 按业务日期和主键稳定倒序的结算批次标准分页
     */
    public PageResult<BatchSummary> search(BatchSearchRequest request) {
        validateBatchSearchWindow(request);
        return searchInCurrentScope(request);
    }

    /** 查询交易结算工作台中的正式批次，服务端固定为 REGULAR，拒绝跨域批次类型。 */
    public PageResult<BatchSummary> searchTransactionBatches(BatchSearchRequest request) {
        return searchByDomain(request, TRANSACTION_BATCH_DOMAIN, TRANSACTION_BATCH_TYPES);
    }

    /** 查询保证金结算工作台中的正式批次，服务端固定为释放或调整批次。 */
    public PageResult<BatchSummary> searchReserveBatches(BatchSearchRequest request) {
        return searchByDomain(request, RESERVE_BATCH_DOMAIN, RESERVE_BATCH_TYPES);
    }

    private PageResult<BatchSummary> searchByDomain(BatchSearchRequest request,
                                                    String domain,
                                                    Set<String> allowedBatchTypes) {
        validateBatchSearchWindow(request);
        String requestedDomain = normalizedUpper(request.getBatchDomain());
        String requestedBatchType = normalizedUpper(request.getBatchType());
        if ((requestedDomain != null && !domain.equals(requestedDomain))
                || (requestedBatchType != null && !allowedBatchTypes.contains(requestedBatchType))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        request.setBatchDomain(domain);
        request.setBatchType(requestedBatchType);
        return searchInCurrentScope(request);
    }

    private void validateBatchSearchWindow(BatchSearchRequest request) {
        if (request == null || request.getBeginBusinessDate() == null
                || request.getEndBusinessDate() == null
                || request.getBeginBusinessDate().isAfter(request.getEndBusinessDate())
                || ChronoUnit.DAYS.between(request.getBeginBusinessDate(),
                request.getEndBusinessDate()) > 92) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private PageResult<BatchSummary> searchInCurrentScope(BatchSearchRequest request) {
        InternalAuthAccount account = currentAdminAccount();
        return queryService.search(request, dataScopeResolver.resolve(account));
    }

    private String normalizedUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    /**
     * 在当前 Admin 商户数据范围内读取批次运营详情。
     *
     * @param settlementBatchNo 全局正式结算批次号
     * @return 批次、候选、汇率、结果、资金和投影运维详情
     */
    public BatchDetailResponse detail(String settlementBatchNo) {
        requireBatchNo(settlementBatchNo);
        InternalAuthAccount account = currentAdminAccount();
        return queryService.detail(settlementBatchNo.trim(), dataScopeResolver.resolve(account));
    }

    /** 下载正式结算凭证前重新应用当前 Admin 商户数据范围。 */
    public BatchDetailResponse voucher(String settlementBatchNo) {
        requireBatchNo(settlementBatchNo);
        InternalAuthAccount account = currentAdminAccount();
        return queryService.voucherDetail(
                settlementBatchNo.trim(), dataScopeResolver.resolve(account));
    }

    /**
     * 校验数据范围后注入可信操作人并远程执行入账前取消。
     *
     * @param settlementBatchNo 待取消正式结算批次号
     * @param request 浏览器命令，不接受操作人字段
     * @param servletRequest 用于提取可信客户端 IP 和 User-Agent
     * @return 取消状态和实际释放候选数
     */
    public BatchCommandResponse cancel(String settlementBatchNo,
                                       BatchCommandRequest request,
                                       HttpServletRequest servletRequest) {
        requireBatchNo(settlementBatchNo);
        InternalAuthAccount account = currentAdminAccount();
        AdminMerchantDataScope dataScope = dataScopeResolver.resolve(account);
        queryService.requireBatchAccess(settlementBatchNo.trim(), dataScope);
        return client.cancel(settlementBatchNo.trim(), command(request, account, servletRequest));
    }

    /** 校验数据范围后注入可信操作人并恢复汇率锁定重试耗尽批次。 */
    public BatchCommandResponse retry(String settlementBatchNo,
                                      BatchCommandRequest request,
                                      HttpServletRequest servletRequest) {
        requireBatchNo(settlementBatchNo);
        InternalAuthAccount account = currentAdminAccount();
        AdminMerchantDataScope dataScope = dataScopeResolver.resolve(account);
        queryService.requireBatchAccess(settlementBatchNo.trim(), dataScope);
        return client.retry(settlementBatchNo.trim(), command(request, account, servletRequest));
    }

    /**
     * 查询当前数据范围内仅来源于真实 CLEARING_REVISION 的交易候选。
     *
     * @param request 候选过滤和分页条件
     * @return 交易结算候选分页
     */
    public PageResult<CandidateSummary> searchTransactionCandidates(CandidateSearchRequest request) {
        return reviewQueries().searchCandidates(request, Set.of("CLEARING_REVISION"),
                currentDataScope());
    }

    /**
     * 查询当前数据范围内 RESERVE_RELEASE 和 ADJUSTMENT 保证金候选。
     *
     * @param request 候选过滤和分页条件
     * @return 保证金结算候选分页
     */
    public PageResult<CandidateSummary> searchReserveCandidates(CandidateSearchRequest request) {
        return reviewQueries().searchCandidates(request, Set.of("RESERVE_RELEASE", "ADJUSTMENT"),
                currentDataScope());
    }

    /**
     * 读取当前数据范围内真实交易候选详情。
     *
     * @param candidateNo 结算候选业务编号
     * @return CLEARING_REVISION 候选详情
     */
    public CandidateSummary transactionCandidateDetail(String candidateNo) {
        return reviewQueries().candidateDetail(candidateNo, Set.of("CLEARING_REVISION"), currentDataScope());
    }

    /**
     * 读取当前数据范围内保证金候选详情。
     *
     * @param candidateNo 结算候选业务编号
     * @return RESERVE_RELEASE 或 ADJUSTMENT 候选详情
     */
    public CandidateSummary reserveCandidateDetail(String candidateNo) {
        return reviewQueries().candidateDetail(candidateNo, Set.of("RESERVE_RELEASE", "ADJUSTMENT"),
                currentDataScope());
    }

    /**
     * 查询当前数据范围内的人工或自动结算预审单。
     *
     * @param request 预审单过滤和分页条件
     * @return Maker-Checker 预审单分页
     */
    public PageResult<ReviewSummary> searchReviews(ReviewSearchRequest request) {
        return reviewQueries().searchReviews(request, currentDataScope());
    }

    /**
     * 读取当前数据范围内的预审主单、候选、锁定汇率和试算结果。
     *
     * @param reviewOrderNo 预审单号
     * @return 预审运营详情
     */
    public ReviewDetailResponse reviewDetail(String reviewOrderNo) {
        requireReviewNo(reviewOrderNo);
        return reviewQueries().reviewDetail(reviewOrderNo.trim(), currentDataScope());
    }

    /** 下载预审凭证前重新执行相同的数据范围校验。 */
    public ReviewDetailResponse reviewVoucher(String reviewOrderNo) {
        return reviewDetail(reviewOrderNo);
    }

    /** 大预审单候选明细使用标准分页，不随详情一次返回。 */
    public PageResult<ReviewCandidateLine> reviewCandidates(
            String reviewOrderNo,
            ReviewCandidateSearchRequest request) {
        requireReviewNo(reviewOrderNo);
        return reviewQueries().reviewCandidates(reviewOrderNo.trim(), request, currentDataScope());
    }

    /**
     * 校验交易候选数据范围后注入可信 Maker，提交 REGULAR 人工预审。
     *
     * @param request 浏览器预审请求，不接受 Maker 字段
     * @param servletRequest 用于提取可信客户端 IP 和 User-Agent
     * @return 新建或幂等回放的待复核预审结果
     */
    public ReviewCommandResponse submitTransactionReview(ReviewSubmitRequest request,
                                                         HttpServletRequest servletRequest) {
        validateReviewSubmit(request, Set.of("REGULAR"));
        return submitReview(request, servletRequest);
    }

    /** 创建服务端冻结的全量交易结算预览，不接受页面候选 ID 列表。 */
    public ManualReviewTaskResponse previewManualTransactionReview(
            ManualReviewPreviewRequest request,
            HttpServletRequest servletRequest) {
        if (request == null || !validText(request.getRequestKey(), 128)
                || !validText(request.getMerchantId(), 64)
                || request.getSettlementProfileId() == null || request.getSettlementProfileId() <= 0
                || !optionalCode(request.getPaymentType(), 64)
                || !optionalCode(request.getPaymentMethod(), 64)
                || !validText(request.getReason(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalAuthAccount account = currentAdminAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        reviewQueries().requireMerchantAccess(request.getMerchantId().trim(), scope);
        InternalManualReviewPreviewRequest internal = new InternalManualReviewPreviewRequest();
        internal.setRequestKey(request.getRequestKey().trim());
        internal.setMerchantId(request.getMerchantId().trim());
        internal.setSettlementProfileId(request.getSettlementProfileId());
        internal.setPaymentType(trimToNull(request.getPaymentType()));
        internal.setPaymentMethod(trimToNull(request.getPaymentMethod()));
        internal.setReason(request.getReason().trim());
        enrichOperator(internal, account, servletRequest);
        return client.previewManualReview(internal);
    }

    /** 将冻结预览提交后台异步生成，操作人可离开页面后再按任务号查看进度。 */
    public ManualReviewTaskResponse startManualTransactionReview(
            String taskNo,
            ManualReviewStartRequest request) {
        requireManualTaskNo(taskNo);
        if (request == null || !validText(request.getRequestKey(), 128)
                || request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        reviewQueries().requireManualTaskAccess(taskNo.trim(), "REGULAR", currentDataScope());
        ManualReviewStartRequest internal = new ManualReviewStartRequest();
        internal.setRequestKey(request.getRequestKey().trim());
        internal.setExpectedVersion(request.getExpectedVersion());
        return client.startManualReview(taskNo.trim(), internal);
    }

    /** 查询服务端冻结范围、只读结算周期和后台任务进度。 */
    public ManualReviewTaskResponse manualTransactionReviewTask(String taskNo) {
        requireManualTaskNo(taskNo);
        reviewQueries().requireManualTaskAccess(taskNo.trim(), "REGULAR", currentDataScope());
        return client.getManualReviewTask(taskNo.trim());
    }

    /** 创建服务端冻结的全量到期保证金释放预览，不接受页面候选 ID 列表。 */
    public ManualReviewTaskResponse previewManualReserveReview(
            ManualReviewPreviewRequest request,
            HttpServletRequest servletRequest) {
        if (request == null || !validText(request.getRequestKey(), 128)
                || !validText(request.getMerchantId(), 64)
                || request.getSettlementProfileId() == null || request.getSettlementProfileId() <= 0
                || !optionalCode(request.getPaymentType(), 64)
                || !optionalCode(request.getPaymentMethod(), 64)
                || !validText(request.getReason(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalAuthAccount account = currentAdminAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        reviewQueries().requireMerchantAccess(request.getMerchantId().trim(), scope);
        InternalManualReviewPreviewRequest internal = new InternalManualReviewPreviewRequest();
        internal.setRequestKey(request.getRequestKey().trim());
        internal.setMerchantId(request.getMerchantId().trim());
        internal.setSettlementProfileId(request.getSettlementProfileId());
        internal.setPaymentType(trimToNull(request.getPaymentType()));
        internal.setPaymentMethod(trimToNull(request.getPaymentMethod()));
        internal.setReason(request.getReason().trim());
        enrichOperator(internal, account, servletRequest);
        return client.previewManualReserveReview(internal);
    }

    /** 将冻结的保证金释放预览提交后台异步生成。 */
    public ManualReviewTaskResponse startManualReserveReview(
            String taskNo,
            ManualReviewStartRequest request) {
        requireManualTaskNo(taskNo);
        if (request == null || !validText(request.getRequestKey(), 128)
                || request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        reviewQueries().requireManualTaskAccess(
                taskNo.trim(), "RESERVE_RELEASE", currentDataScope());
        ManualReviewStartRequest internal = new ManualReviewStartRequest();
        internal.setRequestKey(request.getRequestKey().trim());
        internal.setExpectedVersion(request.getExpectedVersion());
        return client.startManualReserveReview(taskNo.trim(), internal);
    }

    /** 查询保证金释放预览、独立释放周期快照和后台任务进度。 */
    public ManualReviewTaskResponse manualReserveReviewTask(String taskNo) {
        requireManualTaskNo(taskNo);
        reviewQueries().requireManualTaskAccess(
                taskNo.trim(), "RESERVE_RELEASE", currentDataScope());
        return client.getManualReserveReviewTask(taskNo.trim());
    }

    /**
     * 校验保证金候选数据范围后注入可信 Maker，提交释放或调整预审。
     *
     * @param request 浏览器预审请求，不接受 Maker 字段
     * @param servletRequest 用于提取可信客户端 IP 和 User-Agent
     * @return 新建或幂等回放的待复核预审结果
     */
    public ReviewCommandResponse submitReserveReview(ReviewSubmitRequest request,
                                                     HttpServletRequest servletRequest) {
        validateReviewSubmit(request, Set.of("RESERVE_RELEASE", "ADJUSTMENT"));
        return submitReview(request, servletRequest);
    }

    /**
     * 校验预审数据范围后注入可信 Checker，远程执行批准、拒绝或取消。
     *
     * @param reviewOrderNo 待决策预审单号
     * @param decision APPROVE、REJECT 或 CANCEL
     * @param request 浏览器决策请求，不接受 Checker 字段
     * @param servletRequest 用于提取可信客户端 IP 和 User-Agent
     * @return 决策后的预审状态、正式批次号和乐观锁版本
     */
    public ReviewCommandResponse decideReview(String reviewOrderNo,
                                              String decision,
                                              ReviewDecisionRequest request,
                                              HttpServletRequest servletRequest) {
        requireReviewNo(reviewOrderNo);
        if (!Set.of("APPROVE", "REJECT", "CANCEL").contains(decision)
                || request == null || !validText(request.getRequestKey(), 128)
                || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || !validText(request.getComment(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalAuthAccount account = currentAdminAccount();
        AdminMerchantDataScope dataScope = dataScopeResolver.resolve(account);
        reviewQueries().requireReviewAccess(reviewOrderNo.trim(), dataScope);
        InternalReviewDecisionRequest internal = new InternalReviewDecisionRequest();
        internal.setRequestKey(request.getRequestKey().trim());
        internal.setExpectedVersion(request.getExpectedVersion());
        internal.setComment(request.getComment().trim());
        internal.setDecision(decision);
        enrichOperator(internal, account, servletRequest);
        return client.decideReview(reviewOrderNo.trim(), internal);
    }

    /** 大批量 MANUAL_ASYNC 预审单使用可恢复后台决策任务。 */
    public ReviewDecisionTaskResponse submitReviewDecisionTask(
            String reviewOrderNo,
            String decision,
            ReviewDecisionRequest request,
            HttpServletRequest servletRequest) {
        requireReviewNo(reviewOrderNo);
        if (!Set.of("APPROVE", "REJECT", "CANCEL").contains(decision)
                || request == null || !validText(request.getRequestKey(), 128)
                || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || !validText(request.getComment(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalAuthAccount account = currentAdminAccount();
        AdminMerchantDataScope dataScope = dataScopeResolver.resolve(account);
        reviewQueries().requireReviewAccess(reviewOrderNo.trim(), dataScope);
        InternalReviewDecisionRequest internal = new InternalReviewDecisionRequest();
        internal.setRequestKey(request.getRequestKey().trim());
        internal.setExpectedVersion(request.getExpectedVersion());
        internal.setComment(request.getComment().trim());
        internal.setDecision(decision);
        enrichOperator(internal, account, servletRequest);
        return client.submitReviewDecisionTask(reviewOrderNo.trim(), internal);
    }

    public ReviewDecisionTaskResponse reviewDecisionTask(String taskNo) {
        requireDecisionTaskNo(taskNo);
        reviewQueries().requireDecisionTaskAccess(taskNo.trim(), currentDataScope());
        return client.getReviewDecisionTask(taskNo.trim());
    }

    /** 校验任务数据范围后注入可信操作人，原地恢复可恢复的失败决策任务。 */
    public ReviewDecisionTaskResponse resumeReviewDecisionTask(
            String taskNo,
            ReviewDecisionTaskResumeRequest request,
            HttpServletRequest servletRequest) {
        requireDecisionTaskNo(taskNo);
        if (request == null || !validText(request.getRequestKey(), 64)
                || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || !validText(request.getReason(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalAuthAccount account = currentAdminAccount();
        AdminMerchantDataScope dataScope = dataScopeResolver.resolve(account);
        reviewQueries().requireDecisionTaskAccess(taskNo.trim(), dataScope);
        InternalReviewDecisionTaskResumeRequest internal =
                new InternalReviewDecisionTaskResumeRequest();
        internal.setRequestKey(request.getRequestKey().trim());
        internal.setExpectedVersion(request.getExpectedVersion());
        internal.setReason(request.getReason().trim());
        enrichOperator(internal, account, servletRequest);
        return client.resumeReviewDecisionTask(taskNo.trim(), internal);
    }

    private ReviewCommandResponse submitReview(ReviewSubmitRequest request,
                                               HttpServletRequest servletRequest) {
        InternalAuthAccount account = currentAdminAccount();
        AdminMerchantDataScope dataScope = dataScopeResolver.resolve(account);
        List<Long> candidateIds = request.getCandidates().stream()
                .map(com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewCandidateReference::getCandidateId)
                .toList();
        reviewQueries().requireCandidateAccess(candidateIds, dataScope);
        InternalReviewSubmitRequest internal = new InternalReviewSubmitRequest();
        internal.setRequestKey(request.getRequestKey().trim());
        internal.setReviewType(request.getReviewType().trim().toUpperCase());
        internal.setBusinessDate(request.getBusinessDate());
        internal.setCutoffBeginTime(request.getCutoffBeginTime());
        internal.setCutoffEndTime(request.getCutoffEndTime());
        internal.setCandidates(List.copyOf(request.getCandidates()));
        internal.setReason(request.getReason().trim());
        enrichOperator(internal, account, servletRequest);
        return client.submitReview(internal);
    }

    /**
     * 校验浏览器预审请求的类型、半开窗口、候选版本、唯一性和单次一千条上限。
     *
     * @param request 未含操作人字段的浏览器预审请求
     * @param permittedTypes 当前交易或保证金入口允许的预审类型
     */
    private void validateReviewSubmit(ReviewSubmitRequest request, Set<String> permittedTypes) {
        if (request == null || !validText(request.getRequestKey(), 128)
                || request.getReviewType() == null
                || !permittedTypes.contains(request.getReviewType().trim().toUpperCase())
                || request.getBusinessDate() == null || request.getCutoffBeginTime() == null
                || request.getCutoffEndTime() == null
                || !request.getCutoffEndTime().isAfter(request.getCutoffBeginTime())
                || request.getCandidates() == null || request.getCandidates().isEmpty()
                || request.getCandidates().size() > 1000 || !validText(request.getReason(), 400)
                || request.getCandidates().stream().anyMatch(row -> row == null
                || row.getCandidateId() == null || row.getCandidateId() <= 0
                || row.getExpectedVersion() == null || row.getExpectedVersion() < 0)
                || request.getCandidates().stream().map(row -> row.getCandidateId()).distinct().count()
                != request.getCandidates().size()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    /** 将当前可信登录账号、角色快照和客户端环境注入预审 Maker 命令。 */
    private void enrichOperator(InternalReviewSubmitRequest command,
                                InternalAuthAccount account,
                                HttpServletRequest request) {
        command.setOperatorId(account.getAccountId());
        command.setOperatorName(operatorName(account));
        command.setRoleSnapshot(roleSnapshot(account));
        command.setClientIp(clientIp(request));
        command.setUserAgent(userAgent(request));
        command.setOperationTime(LocalDateTime.now());
    }

    /** 将当前可信登录账号、角色快照和客户端环境注入预审 Checker 命令。 */
    private void enrichOperator(InternalReviewDecisionRequest command,
                                InternalAuthAccount account,
                                HttpServletRequest request) {
        command.setOperatorId(account.getAccountId());
        command.setOperatorName(operatorName(account));
        command.setRoleSnapshot(roleSnapshot(account));
        command.setClientIp(clientIp(request));
        command.setUserAgent(userAgent(request));
        command.setOperationTime(LocalDateTime.now());
    }

    private void enrichOperator(InternalReviewDecisionTaskResumeRequest command,
                                InternalAuthAccount account,
                                HttpServletRequest request) {
        command.setOperatorId(account.getAccountId());
        command.setOperatorName(operatorName(account));
        command.setRoleSnapshot(roleSnapshot(account));
        command.setClientIp(clientIp(request));
        command.setUserAgent(userAgent(request));
        command.setOperationTime(LocalDateTime.now());
    }

    private void enrichOperator(InternalManualReviewPreviewRequest command,
                                InternalAuthAccount account,
                                HttpServletRequest request) {
        command.setOperatorId(account.getAccountId());
        command.setOperatorName(operatorName(account));
        command.setRoleSnapshot(roleSnapshot(account));
        command.setClientIp(clientIp(request));
        command.setUserAgent(userAgent(request));
        command.setOperationTime(LocalDateTime.now());
    }

    private String roleSnapshot(InternalAuthAccount account) {
        String snapshot = account.getRoles() == null ? "" : account.getRoles().stream()
                .filter(StringUtils::hasText).map(String::trim).distinct().sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.joining(","));
        return snapshot.isBlank() ? "UNASSIGNED" : snapshot;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String value = StringUtils.hasText(forwarded)
                ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        if (!validText(value, 64)) throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        return value;
    }

    private String userAgent(HttpServletRequest request) {
        String value = request == null ? null : request.getHeader("User-Agent");
        if (!StringUtils.hasText(value)) return "UNKNOWN";
        value = value.trim();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    /** @return 当前可信 Admin 账号实时解析出的商户数据范围。 */
    private AdminMerchantDataScope currentDataScope() {
        InternalAuthAccount account = currentAdminAccount();
        return dataScopeResolver.resolve(account);
    }

    private AdminSettlementReviewQueryService reviewQueries() {
        if (reviewQueryService == null) {
            throw new IllegalStateException("admin settlement review query service is unavailable");
        }
        return reviewQueryService;
    }

    private void requireReviewNo(String value) {
        if (!StringUtils.hasText(value) || !value.trim().matches("SO\\d{8}-\\d{8}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private InternalBatchCommandRequest command(BatchCommandRequest request,
                                                InternalAuthAccount account,
                                                HttpServletRequest servletRequest) {
        if (request == null || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || !validText(request.getRequestKey(), 64) || !validText(request.getReason(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalBatchCommandRequest command = new InternalBatchCommandRequest();
        command.setRequestKey(request.getRequestKey().trim());
        command.setExpectedVersion(request.getExpectedVersion());
        command.setReason(request.getReason().trim());
        command.setOperatorId(account.getAccountId());
        command.setOperatorName(operatorName(account));
        command.setRoleSnapshot(roleSnapshot(account));
        command.setClientIp(clientIp(servletRequest));
        command.setUserAgent(userAgent(servletRequest));
        command.setOperationTime(LocalDateTime.now());
        return command;
    }

    private void requireBatchNo(String value) {
        if (!StringUtils.hasText(value) || !value.trim().matches("SB\\d{8}-\\d{8}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private void requireManualTaskNo(String value) {
        if (!StringUtils.hasText(value) || !value.trim().matches("MT[0-9a-f]{32}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private void requireDecisionTaskNo(String value) {
        if (!StringUtils.hasText(value) || !value.trim().matches("DT[0-9a-f]{32}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private boolean optionalCode(String value, int maxLength) {
        return !StringUtils.hasText(value)
                || value.trim().length() <= maxLength && value.trim().matches("[A-Za-z0-9_.:-]+" );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean validText(String value, int maxLength) {
        return StringUtils.hasText(value) && value.trim().length() <= maxLength;
    }

    private InternalAuthAccount currentAdminAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAppId() == null || account.getAccountId() == null
                || !"ADMIN".equalsIgnoreCase(account.getAppCode())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return account;
    }

    /**
     * 从内部鉴权上下文生成不可由浏览器覆盖的审计操作人名称，优先使用实名并回退登录账号。
     *
     * @param account 已验证为 Admin 的登录账号
     * @return 可持久化到结算命令审计快照的操作人名称
     * @throws ServiceException 操作人名称缺失或超长时按未授权拒绝命令
     */
    private String operatorName(InternalAuthAccount account) {
        String name = StringUtils.hasText(account.getRealName())
                ? account.getRealName().trim()
                : StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount().trim() : null;
        if (!StringUtils.hasText(name) || name.length() > 128) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return name;
    }
}
