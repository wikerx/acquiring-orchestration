package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistConfigRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistApprovalRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistCreateRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistQuery;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistUpdateRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistSubmissionRequest;
import com.scott.payment.component.core.model.PageResult;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantIpWhitelistService
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI IP 白名单领域服务，位于 service-admin 服务层，负责精确 IP 配置、启停和商户维度白名单开关。
 * @status : create
 */
public interface AdminMerchantIpWhitelistService {

    /**
     * 分页查询商户 IP 白名单记录。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<MerchantIpWhitelistResponse> pageWhitelists(MerchantIpWhitelistQuery query);

    /**
     * 按查询条件查询商户维度聚合后的 IP 白名单记录，用于统一 Excel 导出。
     *
     * @param query 查询条件
     * @return 商户维度白名单列表
     */
    List<MerchantIpWhitelistResponse> listWhitelists(MerchantIpWhitelistQuery query);

    /**
     * 查询单条 IP 白名单详情。
     *
     * @param id 白名单记录 ID
     * @return 白名单详情
     */
    MerchantIpWhitelistResponse getWhitelist(Long id);

    /**
     * 批量新增同一商户的精确 IP 白名单记录。
     *
     * @param request 新增请求
     * @return 新增后的记录集合
     */
    List<MerchantIpWhitelistResponse> createWhitelists(MerchantIpWhitelistCreateRequest request);

    /**
     * 更新单条精确 IP 白名单记录。
     *
     * @param id      白名单记录 ID
     * @param request 更新请求
     * @return 更新后的记录
     */
    MerchantIpWhitelistResponse updateWhitelist(Long id, MerchantIpWhitelistUpdateRequest request);

    /**
     * 更新单条 IP 白名单记录状态。
     *
     * @param id     白名单记录 ID
     * @param status 状态，1 启用，0 停用
     * @return 更新后的记录
     */
    MerchantIpWhitelistResponse updateWhitelistStatus(Long id, Integer status);

    /**
     * 审批商户提交的 IP 白名单记录，只允许待审核记录执行一次终态审批。
     *
     * @param id      白名单记录 ID
     * @param request 审批结果、说明和审核通过后的交易状态
     * @return 审批后的记录
     */
    MerchantIpWhitelistResponse approveWhitelist(Long id, MerchantIpWhitelistApprovalRequest request);

    /**
     * 查询指定商户自己的 IP 白名单记录，不跨商户聚合。
     *
     * @param merchantId 已认证商户号
     * @return 该商户全部未删除记录
     */
    List<MerchantIpWhitelistResponse> listMerchantWhitelists(String merchantId);

    /**
     * 以商户来源提交待审核 IP 白名单，交易状态固定为禁止。
     *
     * @param merchantId 已认证商户号
     * @param request    IP 列表和提交说明
     * @return 新增的待审核记录
     */
    List<MerchantIpWhitelistResponse> submitMerchantWhitelists(
            String merchantId, MerchantIpWhitelistSubmissionRequest request);

    /**
     * 软删除单条 IP 白名单记录。
     *
     * @param id 白名单记录 ID
     */
    void deleteWhitelist(Long id);

    /**
     * 更新商户维度 IP 白名单校验开关。
     *
     * @param request 开关请求
     * @return 商户当前任一白名单记录视图；没有 IP 记录时返回仅含商户与开关信息的视图
     */
    MerchantIpWhitelistResponse updateConfig(MerchantIpWhitelistConfigRequest request);
}
