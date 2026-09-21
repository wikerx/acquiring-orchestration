package com.scott.payment.merchant.api.profile;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.component.web.operation.constant.OperatorTypeConstants;
import com.scott.payment.merchant.application.profile.MerchantProfileApplicationService;
import com.scott.payment.merchant.dto.profile.MerchantProfileChangeDTOs;
import com.scott.payment.merchant.dto.profile.MerchantProfileResponse;
import com.scott.payment.merchant.dto.profile.MerchantProfileUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileController
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 商户门户主体资料接口，只从认证上下文定位商户并委托应用服务查询或更新同一份商户事实数据
 * @status : create
 */
@RestController
@RequestMapping("/merchant/info")
public class MerchantProfileController {

    /** 商户主体资料应用服务。 */
    private final MerchantProfileApplicationService applicationService;

    /**
     * 创建商户主体资料接口。
     *
     * @param applicationService 商户主体资料应用服务
     */
    public MerchantProfileController(MerchantProfileApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询当前认证商户主体资料。
     *
     * @return 当前商户主体资料
     */
    @GetMapping
    @RequiresPermission("merchant:info:view")
    public CommonResult<MerchantProfileResponse> getProfile() {
        return success(applicationService.getProfile(currentMerchantId()));
    }

    /**
     * 更新当前认证商户允许自助维护的主体资料。
     *
     * <p>请求含联系人和详细地址，因此操作日志不记录请求或响应正文，只保留操作人、模块、
     * 结果和链路标识。</p>
     *
     * @param request 商户允许维护的字段
     * @return 更新后的当前商户主体资料
     */
    @PutMapping
    @RequiresPermission("merchant:info:edit")
    @OperationLog(
            moduleName = "商户主体资料",
            businessType = OperationTypeConstants.UPDATE,
            operation = "修改商户主体资料",
            operatorType = OperatorTypeConstants.MERCHANT_USER,
            recordRequest = false,
            recordResponse = false
    )
    public CommonResult<MerchantProfileResponse> updateProfile(
            @Valid @RequestBody MerchantProfileUpdateRequest request) {
        return success(applicationService.updateProfile(currentMerchantId(), request));
    }

    /** 查询商户资料维护工作区，包含正式资料、活动申请、历史和合规资料。 */
    @GetMapping("/workspace")
    @RequiresPermission("merchant:info:view")
    public CommonResult<MerchantProfileChangeDTOs.Workspace> getWorkspace() {
        return success(applicationService.getWorkspace(currentMerchantId()));
    }

    /** 保存需审核字段的资料变更草稿，禁止直接覆盖正式主档。 */
    @PostMapping("/change-requests/draft")
    @RequiresPermission("merchant:info:edit")
    @OperationLog(
            moduleName = "商户资料维护",
            businessType = OperationTypeConstants.UPDATE,
            operation = "保存商户资料变更草稿",
            operatorType = OperatorTypeConstants.MERCHANT_USER,
            recordRequest = false,
            recordResponse = false
    )
    public CommonResult<MerchantProfileChangeDTOs.ChangeRequest> saveChangeDraft(
            @Valid @RequestBody MerchantProfileChangeDTOs.ProfileSnapshot request) {
        return success(applicationService.saveDraft(currentMerchantId(), request));
    }

    /** 查询当前商户自己的指定资料变更申请。 */
    @GetMapping("/change-requests/{requestNo}")
    @RequiresPermission("merchant:info:view")
    public CommonResult<MerchantProfileChangeDTOs.ChangeRequest> getChangeRequest(
            @PathVariable("requestNo") String requestNo) {
        return success(applicationService.getChangeRequest(currentMerchantId(), requestNo));
    }

    /** 提交草稿或补件申请进入管理端审核。 */
    @PostMapping("/change-requests/{requestNo}/submit")
    @RequiresPermission("merchant:info:edit")
    @OperationLog(
            moduleName = "商户资料维护",
            businessType = OperationTypeConstants.UPDATE,
            operation = "提交商户资料变更审核",
            operatorType = OperatorTypeConstants.MERCHANT_USER,
            recordRequest = false,
            recordResponse = false
    )
    public CommonResult<MerchantProfileChangeDTOs.ChangeRequest> submitChangeRequest(
            @PathVariable("requestNo") String requestNo,
            @RequestBody(required = false) MerchantProfileChangeDTOs.SubmitRequest request) {
        return success(applicationService.submitChangeRequest(
                currentMerchantId(), requestNo, request == null ? null : request.getComment()));
    }

    /** 撤回尚未完成审核的资料变更申请并释放活动申请占位。 */
    @PostMapping("/change-requests/{requestNo}/withdraw")
    @RequiresPermission("merchant:info:edit")
    @OperationLog(
            moduleName = "商户资料维护",
            businessType = OperationTypeConstants.UPDATE,
            operation = "撤回商户资料变更申请",
            operatorType = OperatorTypeConstants.MERCHANT_USER,
            recordRequest = false,
            recordResponse = false
    )
    public CommonResult<MerchantProfileChangeDTOs.ChangeRequest> withdrawChangeRequest(
            @PathVariable("requestNo") String requestNo) {
        return success(applicationService.withdrawChangeRequest(currentMerchantId(), requestNo));
    }

    /**
     * 从内部认证上下文读取当前商户号，禁止通过请求参数扩大租户范围。
     *
     * @return 已认证商户号
     */
    private String currentMerchantId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return account.getMerchantId().trim();
    }
}
