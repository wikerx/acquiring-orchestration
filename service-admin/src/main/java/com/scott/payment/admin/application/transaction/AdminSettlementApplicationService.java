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
import java.util.Set;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.CandidateSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.CandidateSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSummary;

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
        if (request == null || request.getBeginBusinessDate() == null
                || request.getEndBusinessDate() == null
                || request.getBeginBusinessDate().isAfter(request.getEndBusinessDate())
                || ChronoUnit.DAYS.between(request.getBeginBusinessDate(),
                request.getEndBusinessDate()) > 92) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalAuthAccount account = currentAdminAccount();
        return queryService.search(request, dataScopeResolver.resolve(account));
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
