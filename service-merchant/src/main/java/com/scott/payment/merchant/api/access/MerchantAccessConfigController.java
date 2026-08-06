package com.scott.payment.merchant.api.access;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.component.web.operation.constant.OperatorTypeConstants;
import com.scott.payment.merchant.application.access.MerchantAccessConfigApplicationService;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.IpWhitelistItem;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.IpWhitelistSubmitRequest;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlItem;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlSubmitRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAccessConfigController
 * @date : 2026-08-06 00:00
 * @description : 商户来源网址和 IP 白名单门户接口，从认证上下文限定商户范围，不接受客户端商户号。
 * @status : create
 */
@RestController
@RequestMapping("/merchant/access-config")
public class MerchantAccessConfigController {

    private final MerchantAccessConfigApplicationService applicationService;

    /**
     * 创建商户访问配置接口。
     *
     * @param applicationService 访问配置应用服务
     */
    public MerchantAccessConfigController(MerchantAccessConfigApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询当前认证商户的来源网址。
     *
     * @return 商户全部来源网址记录
     */
    @GetMapping("/source-urls")
    @RequiresPermission("merchant:access-config:view")
    public CommonResult<List<SourceUrlItem>> sourceUrls() {
        return success(applicationService.listSourceUrls(currentMerchantId()));
    }

    /**
     * 提交当前认证商户的来源网址，客户端不能指定商户号。
     *
     * @param request 来源网址和提交说明
     * @return 新增待审核记录
     */
    @PostMapping("/source-urls")
    @RequiresPermission("merchant:access-config:submit")
    @OperationLog(moduleName = "商户访问配置", businessType = OperationTypeConstants.CREATE,
            operation = "提交商户来源网址", operatorType = OperatorTypeConstants.MERCHANT_USER)
    public CommonResult<List<SourceUrlItem>> submitSourceUrls(@RequestBody SourceUrlSubmitRequest request) {
        return success(applicationService.submitSourceUrls(currentMerchantId(), request));
    }

    /**
     * 查询当前认证商户的 IP 白名单。
     *
     * @return 商户全部 IP 白名单记录
     */
    @GetMapping("/ip-whitelists")
    @RequiresPermission("merchant:access-config:view")
    public CommonResult<List<IpWhitelistItem>> ipWhitelists() {
        return success(applicationService.listIpWhitelists(currentMerchantId()));
    }

    /**
     * 提交当前认证商户的 IP 白名单，客户端不能指定商户号。
     *
     * @param request IP 地址和提交说明
     * @return 新增待审核记录
     */
    @PostMapping("/ip-whitelists")
    @RequiresPermission("merchant:access-config:submit")
    @OperationLog(moduleName = "商户访问配置", businessType = OperationTypeConstants.CREATE,
            operation = "提交商户IP白名单", operatorType = OperatorTypeConstants.MERCHANT_USER)
    public CommonResult<List<IpWhitelistItem>> submitIpWhitelists(@RequestBody IpWhitelistSubmitRequest request) {
        return success(applicationService.submitIpWhitelists(currentMerchantId(), request));
    }

    private String currentMerchantId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return account.getMerchantId().trim();
    }
}
