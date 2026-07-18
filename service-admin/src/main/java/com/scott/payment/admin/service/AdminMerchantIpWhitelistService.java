package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistConfigRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistCreateRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistQuery;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistUpdateRequest;
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
