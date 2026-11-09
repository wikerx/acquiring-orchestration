package com.scott.payment.merchant.api.access;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.merchant.application.access.MerchantAccessOverviewApplicationService;
import com.scott.payment.merchant.dto.access.MerchantAccessOverviewDTOs.AccessOverview;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 商户支付接入概览接口，只读取当前认证商户的非敏感配置状态。
 */
@RestController
@RequestMapping("/merchant/access-config/overview")
public class MerchantAccessOverviewController {

    private final MerchantAccessOverviewApplicationService applicationService;

    /**
     * 创建商户支付接入概览接口。
     *
     * @param applicationService 接入概览应用服务
     */
    public MerchantAccessOverviewController(MerchantAccessOverviewApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询当前认证商户的支付接入可用状态。
     *
     * @return 凭证、IP 白名单和来源网址检查结果
     */
    @GetMapping
    @RequiresPermission("merchant:access-config:overview:view")
    public CommonResult<AccessOverview> overview() {
        return success(applicationService.queryOverview(currentMerchantId()));
    }

    /**
     * 从可信认证上下文读取商户号，禁止页面指定其他商户。
     */
    private String currentMerchantId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return account.getMerchantId().trim();
    }
}
