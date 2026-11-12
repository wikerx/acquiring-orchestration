package com.scott.payment.admin.application.export;

import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundAccountPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundAccountPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundAccountRow;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundDeductionPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundDeductionPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundDeductionRow;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundLedgerPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundLedgerPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundLedgerRow;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundRechargePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundRechargePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundRechargeRow;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDeductionQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDeductionResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDetailQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundLedgerResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeResponse;
import com.scott.payment.admin.service.AdminFundAccountService;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * 管理端资金账户域异步导出来源服务，使用冻结商户范围输出最小安全投影。
 */
@Service
public class AdminFundExportSourceApplicationService {
    private final AdminFundAccountService fundAccountService;

    /**
     * 创建资金账户域异步导出来源服务。
     *
     * @param fundAccountService 资金账户、流水和申请权威查询服务
     */
    public AdminFundExportSourceApplicationService(AdminFundAccountService fundAccountService) {
        this.fundAccountService = fundAccountService;
    }

    /** 按冻结条件和商户范围分页读取资金账户。 */
    public FundAccountPageResponse pageAccounts(FundAccountPageRequest request) {
        PageResult<FundAccountResponse> page = fundAccountService.pageAccountsForExport(
                toAccountQuery(request), scope(request.isAllMerchants(), request.getMerchantIds()));
        FundAccountPageResponse response = new FundAccountPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toAccountRow).toList());
        return response;
    }

    /** 按冻结条件和商户范围分页读取余额流水。 */
    public FundLedgerPageResponse pageLedgers(FundLedgerPageRequest request) {
        FundDetailQuery query = toLedgerQuery(request);
        AdminMerchantDataScope scope = scope(request.isAllMerchants(), request.getMerchantIds());
        PageResult<FundLedgerResponse> page = request.getQuery().getAccountId() == null
                ? fundAccountService.pageAllLedgersForExport(query, scope)
                : fundAccountService.pageLedgersForExport(request.getQuery().getAccountId(), query, scope);
        FundLedgerPageResponse response = new FundLedgerPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toLedgerRow).toList());
        return response;
    }

    /** 按冻结条件和商户范围分页读取充值申请。 */
    public FundRechargePageResponse pageRecharges(FundRechargePageRequest request) {
        PageResult<FundRechargeResponse> page = fundAccountService.pageRechargesForExport(
                toRechargeQuery(request), scope(request.isAllMerchants(), request.getMerchantIds()));
        FundRechargePageResponse response = new FundRechargePageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toRechargeRow).toList());
        return response;
    }

    /** 按冻结条件和商户范围分页读取扣减申请。 */
    public FundDeductionPageResponse pageDeductions(FundDeductionPageRequest request) {
        PageResult<FundDeductionResponse> page = fundAccountService.pageDeductionsForExport(
                toDeductionQuery(request), scope(request.isAllMerchants(), request.getMerchantIds()));
        FundDeductionPageResponse response = new FundDeductionPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toDeductionRow).toList());
        return response;
    }

    /** 恢复资金账户筛选条件并以内部分页覆盖浏览器分页。 */
    private FundAccountQuery toAccountQuery(FundAccountPageRequest request) {
        FundAccountQuery target = new FundAccountQuery();
        target.setKeyword(request.getQuery().getKeyword());
        target.setAccountStatus(request.getQuery().getAccountStatus());
        target.setSettlementCurrency(request.getQuery().getSettlementCurrency());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 恢复余额流水筛选条件并以内部分页覆盖浏览器分页。 */
    private FundDetailQuery toLedgerQuery(FundLedgerPageRequest request) {
        FundDetailQuery target = new FundDetailQuery();
        target.setKeyword(request.getQuery().getKeyword());
        target.setMerchantId(request.getQuery().getMerchantId());
        target.setAccountNo(request.getQuery().getAccountNo());
        target.setCurrency(request.getQuery().getCurrency());
        target.setBalanceType(request.getQuery().getBalanceType());
        target.setBusinessType(request.getQuery().getBusinessType());
        target.setDirection(request.getQuery().getDirection());
        target.setPostedStartTime(request.getQuery().getPostedStartTime());
        target.setPostedEndTime(request.getQuery().getPostedEndTime());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 恢复充值申请筛选条件并以内部分页覆盖浏览器分页。 */
    private FundRechargeQuery toRechargeQuery(FundRechargePageRequest request) {
        FundRechargeQuery target = new FundRechargeQuery();
        target.setKeyword(request.getQuery().getKeyword());
        target.setMerchantId(request.getQuery().getMerchantId());
        target.setRechargeStatus(request.getQuery().getRechargeStatus());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 恢复扣减申请筛选条件并以内部分页覆盖浏览器分页。 */
    private FundDeductionQuery toDeductionQuery(FundDeductionPageRequest request) {
        FundDeductionQuery target = new FundDeductionQuery();
        target.setKeyword(request.getQuery().getKeyword());
        target.setMerchantId(request.getQuery().getMerchantId());
        target.setDeductionCategory(request.getQuery().getDeductionCategory());
        target.setDeductionStatus(request.getQuery().getDeductionStatus());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 将资金账户页面结果裁剪为导出行。 */
    private FundAccountRow toAccountRow(FundAccountResponse source) {
        FundAccountRow row = new FundAccountRow();
        row.setAccountNo(source.getAccountNo());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setSettlementCurrency(source.getSettlementCurrency());
        row.setAvailableBalance(source.getAvailableBalance());
        row.setAccountStatus(source.getAccountStatus());
        row.setReverseRestricted(source.getReverseRestricted());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /** 将不可变余额流水复制为导出安全投影，不改变金额或币种。 */
    private FundLedgerRow toLedgerRow(FundLedgerResponse source) {
        FundLedgerRow row = new FundLedgerRow();
        row.setLedgerNo(source.getLedgerNo());
        row.setAccountNo(source.getAccountNo());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setBusinessType(source.getBusinessType());
        row.setSummary(source.getSummary());
        row.setBusinessNo(source.getBusinessNo());
        row.setBalanceType(source.getBalanceType());
        row.setDirection(source.getDirection());
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setBalanceBefore(source.getBalanceBefore());
        row.setBalanceAfter(source.getBalanceAfter());
        row.setAccountSequence(source.getAccountSequence());
        row.setOperatorName(source.getOperatorName());
        row.setReviewerName(source.getReviewerName());
        row.setOperationReason(source.getOperationReason());
        row.setReviewComment(source.getReviewComment());
        row.setBusinessTime(source.getBusinessTime());
        row.setPostedTime(source.getPostedTime());
        row.setRequestId(source.getRequestId());
        row.setIdempotencyKey(source.getIdempotencyKey());
        return row;
    }

    /** 将充值申请复制为导出安全投影。 */
    private FundRechargeRow toRechargeRow(FundRechargeResponse source) {
        FundRechargeRow row = new FundRechargeRow();
        row.setRechargeNo(source.getRechargeNo());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setAccountNo(source.getAccountNo());
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setRechargeStatus(source.getRechargeStatus());
        row.setRemark(source.getRemark());
        row.setSubmitByName(source.getSubmitByName());
        row.setSubmitTime(source.getSubmitTime());
        row.setAuditByName(source.getAuditByName());
        row.setAuditTime(source.getAuditTime());
        row.setRecheckByName(source.getRecheckByName());
        row.setRecheckTime(source.getRecheckTime());
        row.setLedgerNo(source.getLedgerNo());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    /** 将扣减申请复制为导出安全投影。 */
    private FundDeductionRow toDeductionRow(FundDeductionResponse source) {
        FundDeductionRow row = new FundDeductionRow();
        row.setDeductionNo(source.getDeductionNo());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setAccountNo(source.getAccountNo());
        row.setDeductionCategory(source.getDeductionCategory());
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setDeductionStatus(source.getDeductionStatus());
        row.setReason(source.getReason());
        row.setSubmitByName(source.getSubmitByName());
        row.setSubmitTime(source.getSubmitTime());
        row.setAuditByName(source.getAuditByName());
        row.setAuditTime(source.getAuditTime());
        row.setRecheckByName(source.getRecheckByName());
        row.setRecheckTime(source.getRecheckTime());
        row.setLedgerNo(source.getLedgerNo());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    /** 将冻结范围恢复为显式全量或有限商户集合。 */
    private AdminMerchantDataScope scope(boolean allMerchants, java.util.Set<String> merchantIds) {
        return allMerchants ? AdminMerchantDataScope.all() : AdminMerchantDataScope.limited(merchantIds);
    }
}
