package com.scott.payment.merchant.application.access;

import com.scott.payment.merchant.client.admin.MerchantAccessAdminClient;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.IpWhitelistItem;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.IpWhitelistSubmitRequest;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlItem;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlSubmitRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAccessConfigApplicationService
 * @date : 2026-08-06 00:00
 * @description : 商户访问配置应用服务，编排当前认证商户的来源网址和 IP 白名单查询及待审提交。
 * @status : create
 */
@Service
public class MerchantAccessConfigApplicationService {

    private final MerchantAccessAdminClient adminClient;

    /**
     * 创建商户访问配置应用服务。
     *
     * @param adminClient 管理服务内部客户端
     */
    public MerchantAccessConfigApplicationService(MerchantAccessAdminClient adminClient) {
        this.adminClient = adminClient;
    }

    /**
     * 查询当前认证商户的来源网址。
     *
     * @param merchantId 已认证商户号
     * @return 商户全部来源网址记录
     */
    public List<SourceUrlItem> listSourceUrls(String merchantId) {
        return adminClient.listSourceUrls(merchantId);
    }

    /**
     * 提交当前认证商户的来源网址。
     *
     * @param merchantId 已认证商户号
     * @param request    来源网址和提交说明
     * @return 新增待审核记录
     */
    public List<SourceUrlItem> submitSourceUrls(String merchantId, SourceUrlSubmitRequest request) {
        return adminClient.submitSourceUrls(merchantId, request);
    }

    /**
     * 查询当前认证商户的 IP 白名单。
     *
     * @param merchantId 已认证商户号
     * @return 商户全部 IP 白名单记录
     */
    public List<IpWhitelistItem> listIpWhitelists(String merchantId) {
        return adminClient.listIpWhitelists(merchantId);
    }

    /**
     * 提交当前认证商户的 IP 白名单。
     *
     * @param merchantId 已认证商户号
     * @param request    IP 地址和提交说明
     * @return 新增待审核记录
     */
    public List<IpWhitelistItem> submitIpWhitelists(String merchantId, IpWhitelistSubmitRequest request) {
        return adminClient.submitIpWhitelists(merchantId, request);
    }
}
