package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminSettlementApplicationService;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.CandidateSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.CandidateSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSummary;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementReviewController
 * @date : 2026-09-01 22:50
 * @email : scott_x@163.com
 * @description : Admin 交易/保证金候选、预审单和 Maker-Checker 命令入口；查询使用本地逻辑数据源，状态命令由应用层注入可信身份后远程执行。
 * @status : update
 */
@RestController
@RequestMapping("/admin/settlement")
public class AdminSettlementReviewController {

    private final AdminSettlementApplicationService applicationService;

    public AdminSettlementReviewController(AdminSettlementApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询管理数据范围内的真实交易结算候选。
     *
     * @param request 候选过滤和分页条件
     * @return CLEARING_REVISION 候选标准分页
     */
    @PostMapping("/transaction-candidates/search")
    @RequiresPermission("settlement:transaction-candidate:list")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询交易结算候选")
    public CommonResult<PageResult<CandidateSummary>> transactionCandidates(
            @RequestBody CandidateSearchRequest request) {
        return success(applicationService.searchTransactionCandidates(request));
    }

    /**
     * 查询真实交易结算候选详情。
     *
     * @param candidateNo 候选业务编号
     * @return 当前 Admin 数据范围内的交易候选详情
     */
    @GetMapping("/transaction-candidates/{candidateNo}")
    @RequiresPermission("settlement:transaction-candidate:detail")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询交易结算候选详情")
    public CommonResult<CandidateSummary> transactionCandidateDetail(
            @PathVariable("candidateNo") String candidateNo) {
        return success(applicationService.transactionCandidateDetail(candidateNo));
    }

    /**
     * 分页查询管理数据范围内的保证金结算候选。
     *
     * @param request 候选过滤和分页条件
     * @return RESERVE_RELEASE 和 ADJUSTMENT 候选标准分页
     */
    @PostMapping("/reserve-candidates/search")
    @RequiresPermission("settlement:reserve-candidate:list")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询保证金结算候选")
    public CommonResult<PageResult<CandidateSummary>> reserveCandidates(
            @RequestBody CandidateSearchRequest request) {
        return success(applicationService.searchReserveCandidates(request));
    }

    /**
     * 查询保证金结算候选详情。
     *
     * @param candidateNo 候选业务编号
     * @return 当前 Admin 数据范围内的保证金候选详情
     */
    @GetMapping("/reserve-candidates/{candidateNo}")
    @RequiresPermission("settlement:reserve-candidate:detail")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询保证金结算候选详情")
    public CommonResult<CandidateSummary> reserveCandidateDetail(
            @PathVariable("candidateNo") String candidateNo) {
        return success(applicationService.reserveCandidateDetail(candidateNo));
    }

    /**
     * 分页查询交易或保证金结算预审单。
     *
     * @param request 预审过滤和分页条件
     * @return 当前 Admin 数据范围内的预审单标准分页
     */
    @PostMapping("/review-orders/search")
    @RequiresPermission("settlement:review-order:list")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算预审单")
    public CommonResult<PageResult<ReviewSummary>> reviews(@RequestBody ReviewSearchRequest request) {
        return success(applicationService.searchReviews(request));
    }

    /**
     * 查询预审选择、汇率、结果和审批指纹详情。
     *
     * @param reviewOrderNo 预审单号
     * @return 当前 Admin 数据范围内的预审运营详情
     */
    @GetMapping("/review-orders/{reviewOrderNo}")
    @RequiresPermission("settlement:review-order:detail")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算预审单详情")
    public CommonResult<ReviewDetailResponse> reviewDetail(
            @PathVariable("reviewOrderNo") String reviewOrderNo) {
        return success(applicationService.reviewDetail(reviewOrderNo));
    }

    /**
     * 创建并提交仅包含真实交易候选的预审单。
     *
     * @param request 浏览器预审命令，不接受 Maker 字段
     * @param servletRequest 可信客户端环境来源
     * @return 新建或幂等回放的预审结果
     */
    @PostMapping("/transaction-review-orders")
    @RequiresPermission("settlement:transaction-review:create")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.CREATE,
            operation = "创建并提交交易结算预审")
    public CommonResult<ReviewCommandResponse> submitTransactionReview(
            @RequestBody ReviewSubmitRequest request, HttpServletRequest servletRequest) {
        return success(applicationService.submitTransactionReview(request, servletRequest));
    }

    /**
     * 创建并提交仅包含保证金动作候选的预审单。
     *
     * @param request 浏览器预审命令，不接受 Maker 字段
     * @param servletRequest 可信客户端环境来源
     * @return 新建或幂等回放的预审结果
     */
    @PostMapping("/reserve-review-orders")
    @RequiresPermission("settlement:reserve-review:create")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.CREATE,
            operation = "创建并提交保证金结算预审")
    public CommonResult<ReviewCommandResponse> submitReserveReview(
            @RequestBody ReviewSubmitRequest request, HttpServletRequest servletRequest) {
        return success(applicationService.submitReserveReview(request, servletRequest));
    }

    /**
     * 由不同操作人审批预审单并触发正式结算命令。
     *
     * @param reviewOrderNo 待审批预审单号
     * @param request 浏览器决策命令，不接受 Checker 字段
     * @param servletRequest 可信客户端环境来源
     * @return 审批后的预审状态和正式批次关联
     */
    @PostMapping("/review-orders/{reviewOrderNo}/approve")
    @RequiresPermission("settlement:review-order:approve")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.UPDATE,
            operation = "审批通过结算预审")
    public CommonResult<ReviewCommandResponse> approve(
            @PathVariable("reviewOrderNo") String reviewOrderNo,
            @RequestBody ReviewDecisionRequest request, HttpServletRequest servletRequest) {
        return success(applicationService.decideReview(
                reviewOrderNo, "APPROVE", request, servletRequest));
    }

    /**
     * 由不同操作人拒绝待审批预审单。
     *
     * @param reviewOrderNo 待拒绝预审单号
     * @param request 浏览器决策命令，不接受 Checker 字段
     * @param servletRequest 可信客户端环境来源
     * @return 拒绝后的预审状态和版本
     */
    @PostMapping("/review-orders/{reviewOrderNo}/reject")
    @RequiresPermission("settlement:review-order:reject")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.UPDATE,
            operation = "拒绝结算预审")
    public CommonResult<ReviewCommandResponse> reject(
            @PathVariable("reviewOrderNo") String reviewOrderNo,
            @RequestBody ReviewDecisionRequest request, HttpServletRequest servletRequest) {
        return success(applicationService.decideReview(
                reviewOrderNo, "REJECT", request, servletRequest));
    }

    /**
     * 由有权限操作人取消尚未终态的预审单。
     *
     * @param reviewOrderNo 待取消预审单号
     * @param request 浏览器决策命令，不接受操作人字段
     * @param servletRequest 可信客户端环境来源
     * @return 取消后的预审状态和候选释放结果
     */
    @PostMapping("/review-orders/{reviewOrderNo}/cancel")
    @RequiresPermission("settlement:review-order:cancel")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.UPDATE,
            operation = "取消待审批结算预审")
    public CommonResult<ReviewCommandResponse> cancel(
            @PathVariable("reviewOrderNo") String reviewOrderNo,
            @RequestBody ReviewDecisionRequest request, HttpServletRequest servletRequest) {
        return success(applicationService.decideReview(
                reviewOrderNo, "CANCEL", request, servletRequest));
    }
}
