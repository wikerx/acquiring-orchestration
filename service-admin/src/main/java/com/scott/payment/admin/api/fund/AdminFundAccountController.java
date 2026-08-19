package com.scott.payment.admin.api.fund;

import com.scott.payment.admin.application.fund.AdminFundAccountApplicationService;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountStatusRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDetailQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundLedgerResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeCreateRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeRejectRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeReviewRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFundAccountController
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端资金账户、不可变余额流水和充值审批接口；在途与保证金只展示汇总余额。
 * @status : create
 */
@RestController
@RequestMapping("/admin/fund-accounts")
public class AdminFundAccountController {

    private final AdminFundAccountApplicationService applicationService;

    /**
     * 构造资金账户接口。
     *
     * @param applicationService 资金账户查询、导出和充值审批应用服务
     */
    public AdminFundAccountController(AdminFundAccountApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询商户资金账户基础信息，列表不触发在途和保证金实时统计。
     *
     * @param query 商户、状态、结算币种和分页条件
     * @return 资金账户分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("fund:account:list")
    public CommonResult<PageResult<FundAccountResponse>> pageAccounts(
            @RequestBody(required = false) FundAccountQuery query) {
        return success(applicationService.pageAccounts(query));
    }

    /**
     * 按筛选条件导出资金账户基础信息。
     *
     * @param query 商户、状态和结算币种条件
     * @param response Excel 文件响应，写入后不再返回 JSON
     */
    @PostMapping("/export")
    @RequiresPermission("fund:account:export")
    @OperationLog(moduleName = "资金账户", businessType = OperationTypeConstants.EXPORT, operation = "导出资金账户")
    public void exportAccounts(@RequestBody(required = false) FundAccountQuery query,
                               HttpServletResponse response) {
        applicationService.exportAccounts(query, response);
    }

    /**
     * 查询资金账户详情，并实时汇总在途和保证金余额。
     *
     * @param id 资金账户主键
     * @return 账户详情和能力快照
     */
    @GetMapping("/{id}")
    @RequiresPermission("fund:account:detail")
    public CommonResult<FundAccountResponse> getAccount(@PathVariable("id") Long id) {
        return success(applicationService.getAccount(id));
    }

    /**
     * 分页查询指定账户不可变余额流水。
     *
     * @param id 资金账户主键
     * @param query 业务类型、方向、入账时间和分页条件
     * @return 指定账户余额流水分页结果
     */
    @PostMapping("/{id}/ledgers/search")
    @RequiresPermission("fund:ledger:list")
    public CommonResult<PageResult<FundLedgerResponse>> pageLedgers(
            @PathVariable("id") Long id,
            @RequestBody(required = false) FundDetailQuery query) {
        return success(applicationService.pageLedgers(id, query));
    }

    /**
     * 按筛选条件导出指定账户全部余额明细。
     *
     * @param id 资金账户主键
     * @param query 业务类型、方向和入账时间条件
     * @param response Excel 文件响应，写入后不再返回 JSON
     */
    @PostMapping("/{id}/ledgers/export")
    @RequiresPermission("fund:ledger:export")
    @OperationLog(moduleName = "资金账户", businessType = OperationTypeConstants.EXPORT, operation = "导出余额明细")
    public void exportLedgers(@PathVariable("id") Long id,
                              @RequestBody(required = false) FundDetailQuery query,
                              HttpServletResponse response) {
        applicationService.exportLedgers(id, query, response);
    }

    /**
     * 分页查询所有商户、所有账户的不可变余额流水。
     *
     * @param query 商户、账户、业务类型、方向、币种和入账时间条件
     * @return 全局余额流水分页结果
     */
    @PostMapping("/ledgers/search")
    @RequiresPermission("fund:ledger:all:list")
    public CommonResult<PageResult<FundLedgerResponse>> pageAllLedgers(
            @RequestBody(required = false) FundDetailQuery query) {
        return success(applicationService.pageAllLedgers(query));
    }

    /**
     * 按筛选条件导出所有商户的余额明细。
     *
     * @param query 全局余额流水筛选条件
     * @param response Excel 文件响应，写入后不再返回 JSON
     */
    @PostMapping("/ledgers/export")
    @RequiresPermission("fund:ledger:all:export")
    @OperationLog(moduleName = "余额明细", businessType = OperationTypeConstants.EXPORT, operation = "导出全局余额明细")
    public void exportAllLedgers(@RequestBody(required = false) FundDetailQuery query,
                                 HttpServletResponse response) {
        applicationService.exportAllLedgers(query, response);
    }

    /**
     * 冻结资金账户，保留入账和结算能力并禁止主动资金流出。
     *
     * @param id 资金账户主键
     * @param request 期望版本号和状态变更原因
     * @return 冻结后的账户能力快照
     */
    @PutMapping("/{id}/freeze")
    @RequiresPermission("fund:account:freeze")
    @OperationLog(moduleName = "资金账户", businessType = OperationTypeConstants.FREEZE, operation = "冻结资金账户")
    public CommonResult<FundAccountResponse> freezeAccount(
            @PathVariable("id") Long id, @Valid @RequestBody FundAccountStatusRequest request) {
        return success(applicationService.freezeAccount(id, request));
    }

    /**
     * 解冻资金账户并恢复正常人工状态，负余额限制仍独立生效。
     *
     * @param id 资金账户主键
     * @param request 期望版本号和状态变更原因
     * @return 解冻后的账户能力快照
     */
    @PutMapping("/{id}/unfreeze")
    @RequiresPermission("fund:account:unfreeze")
    @OperationLog(moduleName = "资金账户", businessType = OperationTypeConstants.UNFREEZE, operation = "解冻资金账户")
    public CommonResult<FundAccountResponse> unfreezeAccount(
            @PathVariable("id") Long id, @Valid @RequestBody FundAccountStatusRequest request) {
        return success(applicationService.unfreezeAccount(id, request));
    }

    /**
     * 关闭资金账户，仅保留人工充值入口并禁止结算、提现和主动资金转出。
     *
     * @param id 资金账户主键
     * @param request 期望版本号和状态变更原因
     * @return 关闭后的账户能力快照
     */
    @PutMapping("/{id}/close")
    @RequiresPermission("fund:account:close")
    @OperationLog(moduleName = "资金账户", businessType = OperationTypeConstants.UPDATE, operation = "关闭资金账户")
    public CommonResult<FundAccountResponse> closeAccount(
            @PathVariable("id") Long id, @Valid @RequestBody FundAccountStatusRequest request) {
        return success(applicationService.closeAccount(id, request));
    }

    /**
     * 恢复已关闭资金账户，负余额限制仍独立生效。
     *
     * @param id 资金账户主键
     * @param request 期望版本号和状态变更原因
     * @return 恢复后的账户能力快照
     */
    @PutMapping("/{id}/reopen")
    @RequiresPermission("fund:account:reopen")
    @OperationLog(moduleName = "资金账户", businessType = OperationTypeConstants.UPDATE, operation = "恢复资金账户")
    public CommonResult<FundAccountResponse> reopenAccount(
            @PathVariable("id") Long id, @Valid @RequestBody FundAccountStatusRequest request) {
        return success(applicationService.reopenAccount(id, request));
    }

    /**
     * 分页查询充值申请及审核、复核、驳回信息。
     *
     * @param query 商户、充值状态、关键字和分页条件
     * @return 充值申请分页结果
     */
    @PostMapping("/recharges/search")
    @RequiresPermission("fund:recharge:list")
    public CommonResult<PageResult<FundRechargeResponse>> pageRecharges(
            @RequestBody(required = false) FundRechargeQuery query) {
        return success(applicationService.pageRecharges(query));
    }

    /**
     * 创建待审核充值申请，客户端请求号用于幂等保护。
     *
     * @param request 账户、金额、请求号和充值说明
     * @return 已创建或幂等命中的充值申请
     */
    @PostMapping("/recharges")
    @RequiresPermission("fund:recharge:add")
    @OperationLog(moduleName = "账户充值", businessType = OperationTypeConstants.CREATE, operation = "提交充值申请")
    public CommonResult<FundRechargeResponse> createRecharge(
            @Valid @RequestBody FundRechargeCreateRequest request) {
        return success(applicationService.createRecharge(request));
    }

    /**
     * 审核充值申请并转为待复核，提交人不得审核自己的申请。
     *
     * @param id 充值申请主键
     * @param request 审核意见，可为空
     * @return 待复核充值申请
     */
    @PostMapping("/recharges/{id}/audit")
    @RequiresPermission("fund:recharge:audit")
    @OperationLog(moduleName = "账户充值", businessType = OperationTypeConstants.UPDATE, operation = "审核充值申请")
    public CommonResult<FundRechargeResponse> auditRecharge(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) FundRechargeReviewRequest request) {
        return success(applicationService.auditRecharge(id, request == null ? null : request.getComment()));
    }

    /**
     * 复核充值申请，并在同一事务中锁定账户、增加可用余额和写入流水。
     *
     * @param id 充值申请主键
     * @param request 复核意见，可为空
     * @return 已入账充值申请
     */
    @PostMapping("/recharges/{id}/recheck")
    @RequiresPermission("fund:recharge:recheck")
    @OperationLog(moduleName = "账户充值", businessType = OperationTypeConstants.UPDATE, operation = "复核充值并入账")
    public CommonResult<FundRechargeResponse> recheckRecharge(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) FundRechargeReviewRequest request) {
        return success(applicationService.recheckRecharge(id, request == null ? null : request.getComment()));
    }

    /**
     * 驳回待审核或待复核充值申请，不产生余额变动。
     *
     * @param id 充值申请主键
     * @param request 必填驳回原因
     * @return 已驳回充值申请
     */
    @PostMapping("/recharges/{id}/reject")
    @RequiresPermission("fund:recharge:reject")
    @OperationLog(moduleName = "账户充值", businessType = OperationTypeConstants.UPDATE, operation = "驳回充值申请")
    public CommonResult<FundRechargeResponse> rejectRecharge(
            @PathVariable("id") Long id,
            @Valid @RequestBody FundRechargeRejectRequest request) {
        return success(applicationService.rejectRecharge(id, request.getComment()));
    }

    /**
     * 按筛选条件导出全部充值申请及审批信息。
     *
     * @param query 商户、状态和关键字条件
     * @param response Excel 文件响应，写入后不再返回 JSON
     */
    @PostMapping("/recharges/export")
    @RequiresPermission("fund:recharge:export")
    @OperationLog(moduleName = "账户充值", businessType = OperationTypeConstants.EXPORT, operation = "导出充值申请")
    public void exportRecharges(@RequestBody(required = false) FundRechargeQuery query,
                                HttpServletResponse response) {
        applicationService.exportRecharges(query, response);
    }
}
