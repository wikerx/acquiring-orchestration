package com.scott.payment.merchant.client.admin;

import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.IpWhitelistItem;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.IpWhitelistSubmitRequest;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlItem;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlSubmitRequest;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAccessAdminClient
 * @date : 2026-08-06 00:00
 * @description : service-admin 商户访问配置内部客户端契约，只接收应用层传入的已认证商户号。
 * @status : create
 */
public interface MerchantAccessAdminClient {

    /**
     * 查询指定已认证商户的来源网址。
     *
     * @param merchantId 已认证商户号
     * @return 商户全部来源网址记录
     */
    List<SourceUrlItem> listSourceUrls(String merchantId);

    /**
     * 提交指定已认证商户的来源网址。
     *
     * @param merchantId 已认证商户号
     * @param request    来源网址和提交说明
     * @return 新增待审核记录
     */
    List<SourceUrlItem> submitSourceUrls(String merchantId, SourceUrlSubmitRequest request);

    /**
     * 查询指定已认证商户的 IP 白名单。
     *
     * @param merchantId 已认证商户号
     * @return 商户全部 IP 白名单记录
     */
    List<IpWhitelistItem> listIpWhitelists(String merchantId);

    /**
     * 提交指定已认证商户的 IP 白名单。
     *
     * @param merchantId 已认证商户号
     * @param request    IP 地址和提交说明
     * @return 新增待审核记录
     */
    List<IpWhitelistItem> submitIpWhitelists(String merchantId, IpWhitelistSubmitRequest request);
}
