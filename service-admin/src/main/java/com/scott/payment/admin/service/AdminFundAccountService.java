package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDetailQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDeductionCreateRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDeductionQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDeductionResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundLedgerResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeCreateRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeResponse;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFundAccountService
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端资金账户、余额流水、充值和扣减审批服务契约。
 * @status : create
 */
public interface AdminFundAccountService {
    /**
     * 分页查询商户资金账户基础信息，不在列表阶段聚合在途和保证金。
     *
     * @param query 账户筛选和分页条件
     * @return 资金账户分页结果
     */
    PageResult<FundAccountResponse> pageAccounts(FundAccountQuery query);
    /**
     * 查询账户详情，并实时汇总该商户的在途余额和账户保证金。
     *
     * @param id 资金账户主键
     * @return 账户详情和动态余额汇总
     */
    FundAccountResponse getAccount(Long id);
    /**
     * 分页查询指定账户的不可变余额流水。
     *
     * @param accountId 资金账户主键
     * @param query 流水筛选和分页条件
     * @return 指定账户的余额流水分页结果
     */
    PageResult<FundLedgerResponse> pageLedgers(Long accountId, FundDetailQuery query);
    /**
     * 分页查询所有商户、所有账户的不可变余额流水。
     *
     * @param query 全局流水筛选和分页条件
     * @return 全局余额流水分页结果
     */
    PageResult<FundLedgerResponse> pageAllLedgers(FundDetailQuery query);
    /**
     * 分页查询充值申请及完整审批状态。
     *
     * @param query 充值单号、商户号、状态和分页条件
     * @return 充值申请分页结果
     */
    PageResult<FundRechargeResponse> pageRecharges(FundRechargeQuery query);
    /**
     * 创建待审核充值申请，同一请求号、账户和金额的重试返回原申请。
     *
     * @param request 账户、金额、请求号和充值说明
     * @param operatorId 提交人账号主键
     * @param operatorName 提交人名称快照
     * @param loginAccount 提交人登录账号，用于内置 admin 隔离规则
     * @return 已创建或幂等命中的充值申请
     */
    FundRechargeResponse createRecharge(FundRechargeCreateRequest request, Long operatorId,
                                        String operatorName, String loginAccount);
    /**
     * 审核充值申请并转入待复核；仅内置 admin 可审核自己提交的申请。
     *
     * @param id 充值申请主键
     * @param comment 审核意见，允许为空
     * @param operatorId 审核人账号主键
     * @param operatorName 审核人名称快照
     * @param loginAccount 审核人登录账号，用于识别内置 admin 自审特例
     * @return 待复核充值申请
     */
    FundRechargeResponse auditRecharge(Long id, String comment, Long operatorId,
                                       String operatorName, String loginAccount);
    /**
     * 复核充值申请并原子增加可用余额、写入不可变流水。
     *
     * @param id 充值申请主键
     * @param comment 复核意见，允许为空
     * @param operatorId 复核人账号主键
     * @param operatorName 复核人名称快照
     * @param loginAccount 复核人登录账号；仅内置 admin 可同时作为提交人、审核人和复核人
     * @return 已入账充值申请
     */
    FundRechargeResponse recheckRecharge(Long id, String comment, Long operatorId,
                                         String operatorName, String loginAccount);
    /**
     * 在审核或复核阶段驳回充值申请，不产生余额变动。
     *
     * @param id 充值申请主键
     * @param comment 驳回原因，不允许为空
     * @param operatorId 驳回人账号主键
     * @param operatorName 驳回人名称快照
     * @param loginAccount 驳回人登录账号
     * @return 已驳回充值申请
     */
    FundRechargeResponse rejectRecharge(Long id, String comment, Long operatorId,
                                        String operatorName, String loginAccount);

    /**
     * 分页查询账户扣减申请及完整审批状态。
     *
     * @param query 商户号、扣减类型、状态和分页条件
     * @return 账户扣减申请分页结果
     */
    PageResult<FundDeductionResponse> pageDeductions(FundDeductionQuery query);

    /**
     * 查询账户扣减申请详情。
     *
     * @param id 扣减申请主键
     * @return 完整扣减审批快照
     */
    FundDeductionResponse getDeduction(Long id);

    /**
     * 创建待审核账户扣减申请，同一请求号、账户、类型和金额的重试返回原申请。
     *
     * @param request 账户、扣减类型、金额、请求号和商户可见说明
     * @param operatorId 提交人账号主键
     * @param operatorName 提交人名称快照
     * @param loginAccount 提交人登录账号，用于内置 admin 隔离规则
     * @return 已创建或幂等命中的扣减申请
     */
    FundDeductionResponse createDeduction(FundDeductionCreateRequest request, Long operatorId,
                                          String operatorName, String loginAccount);

    /**
     * 审核扣减申请并转入待复核；仅内置 admin 可审核自己提交的申请。
     *
     * @param id 扣减申请主键
     * @param comment 审核意见，允许为空
     * @param operatorId 审核人账号主键
     * @param operatorName 审核人名称快照
     * @param loginAccount 审核人登录账号
     * @return 待复核扣减申请
     */
    FundDeductionResponse auditDeduction(Long id, String comment, Long operatorId,
                                         String operatorName, String loginAccount);

    /**
     * 复核扣减申请并原子减少可用余额、写入不可变流水。
     *
     * @param id 扣减申请主键
     * @param comment 复核意见，允许为空
     * @param operatorId 复核人账号主键
     * @param operatorName 复核人名称快照
     * @param loginAccount 复核人登录账号
     * @return 已入账扣减申请
     */
    FundDeductionResponse recheckDeduction(Long id, String comment, Long operatorId,
                                           String operatorName, String loginAccount);

    /**
     * 在审核或复核阶段驳回扣减申请，不产生余额变动。
     *
     * @param id 扣减申请主键
     * @param comment 驳回原因，不允许为空
     * @param operatorId 驳回人账号主键
     * @param operatorName 驳回人名称快照
     * @param loginAccount 驳回人登录账号
     * @return 已驳回扣减申请
     */
    FundDeductionResponse rejectDeduction(Long id, String comment, Long operatorId,
                                          String operatorName, String loginAccount);

    /**
     * 在账户行锁和期望版本保护下变更人工账户状态。
     *
     * @param id 资金账户主键
     * @param expectedVersion 页面读取到的账户版本号
     * @param targetStatus NORMAL、FROZEN 或 CLOSED
     * @param reason 状态变更原因
     * @param operatorId 操作人账号主键
     * @param operatorName 操作人名称快照
     * @return 状态变更后的账户详情
     */
    FundAccountResponse changeAccountStatus(Long id, Long expectedVersion, String targetStatus,
                                            String reason, Long operatorId, String operatorName);
}
