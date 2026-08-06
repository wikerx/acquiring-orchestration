package com.scott.payment.admin.api.internal;

import com.scott.payment.admin.application.risk.AdminRiskManagementApplicationService;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistSubmissionRequest;
import com.scott.payment.admin.dto.merchant.MerchantAccessInternalDTOs.IpWhitelistSubmitRequest;
import com.scott.payment.admin.dto.merchant.MerchantAccessInternalDTOs.SourceUrlSubmitRequest;
import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.admin.service.AdminMerchantIpWhitelistService;
import com.scott.payment.component.core.model.CommonResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAccessInternalController
 * @date : 2026-08-06 00:00
 * @description : 商户访问配置内部接口，仅供通过 HMAC 鉴权的 service-merchant 查询和提交当前认证商户数据。
 * @status : create
 */
@RestController
@RequestMapping("/internal/merchant/{merchantId}/access-config")
public class MerchantAccessInternalController {

    private final AdminRiskManagementApplicationService riskApplicationService;
    private final AdminMerchantIpWhitelistService ipWhitelistService;

    /**
     * 创建商户访问配置内部接口。
     *
     * @param riskApplicationService 来源网址应用服务
     * @param ipWhitelistService     IP 白名单领域服务
     */
    public MerchantAccessInternalController(AdminRiskManagementApplicationService riskApplicationService,
                                            AdminMerchantIpWhitelistService ipWhitelistService) {
        this.riskApplicationService = riskApplicationService;
        this.ipWhitelistService = ipWhitelistService;
    }

    /**
     * 查询签名路径指定商户的来源网址。
     *
     * @param merchantId 已认证商户号，作为 HMAC 签名路径的一部分
     * @return 该商户全部未删除来源网址
     */
    @PostMapping("/source-urls/search")
    public CommonResult<List<RiskDTOs.RiskRecordResponse>> sourceUrls(@PathVariable("merchantId") String merchantId) {
        return success(riskApplicationService.listMerchantSourceUrls(merchantId));
    }

    /**
     * 提交签名路径指定商户的来源网址。
     *
     * @param merchantId 已认证商户号，作为 HMAC 签名路径的一部分
     * @param request    来源网址和提交说明
     * @return 新增待审核记录
     */
    @PostMapping("/source-urls")
    public CommonResult<List<RiskDTOs.RiskRecordResponse>> submitSourceUrls(
            @PathVariable("merchantId") String merchantId,
            @Valid @RequestBody SourceUrlSubmitRequest request) {
        RiskDTOs.MerchantSourceUrlSubmissionRequest submission = new RiskDTOs.MerchantSourceUrlSubmissionRequest();
        submission.setSourceUrls(request.getSourceUrls());
        submission.setRemark(request.getRemark());
        return success(riskApplicationService.submitMerchantSourceUrls(merchantId, submission));
    }

    /**
     * 查询签名路径指定商户的 IP 白名单。
     *
     * @param merchantId 已认证商户号，作为 HMAC 签名路径的一部分
     * @return 该商户全部未删除 IP 白名单
     */
    @PostMapping("/ip-whitelists/search")
    public CommonResult<List<MerchantIpWhitelistResponse>> ipWhitelists(@PathVariable("merchantId") String merchantId) {
        return success(ipWhitelistService.listMerchantWhitelists(merchantId));
    }

    /**
     * 提交签名路径指定商户的 IP 白名单。
     *
     * @param merchantId 已认证商户号，作为 HMAC 签名路径的一部分
     * @param request    IP 地址和提交说明
     * @return 新增待审核记录
     */
    @PostMapping("/ip-whitelists")
    public CommonResult<List<MerchantIpWhitelistResponse>> submitIpWhitelists(
            @PathVariable("merchantId") String merchantId,
            @Valid @RequestBody IpWhitelistSubmitRequest request) {
        MerchantIpWhitelistSubmissionRequest submission = new MerchantIpWhitelistSubmissionRequest();
        submission.setIpValues(request.getIpValues());
        submission.setRemark(request.getRemark());
        return success(ipWhitelistService.submitMerchantWhitelists(merchantId, submission));
    }
}
