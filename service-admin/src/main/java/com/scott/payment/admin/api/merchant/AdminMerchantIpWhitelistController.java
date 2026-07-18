package com.scott.payment.admin.api.merchant;

import com.scott.payment.admin.application.merchant.AdminMerchantIpWhitelistApplicationService;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistConfigRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistCreateRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistQuery;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistStatusRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistUpdateRequest;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantIpWhitelistController
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 商户 IP 白名单管理接口，位于 service-admin 接口层，负责后台精确 IP 白名单的权限校验、参数接收和 HTTP 映射。
 * @status : create
 */
@RestController
@RequestMapping("/admin/merchant/ip-whitelist")
public class AdminMerchantIpWhitelistController {

    private final AdminMerchantIpWhitelistApplicationService whitelistApplicationService;

    /**
     * 创建商户 IP 白名单控制器。
     *
     * @param whitelistApplicationService 白名单应用服务
     */
    public AdminMerchantIpWhitelistController(AdminMerchantIpWhitelistApplicationService whitelistApplicationService) {
        this.whitelistApplicationService = whitelistApplicationService;
    }

    /**
     * 分页查询商户 IP 白名单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("merchant:ip-whitelist:list")
    public CommonResult<PageResult<MerchantIpWhitelistResponse>> pageWhitelists(@RequestBody(required = false) MerchantIpWhitelistQuery query) {
        return success(whitelistApplicationService.pageWhitelists(query));
    }

    /**
     * 按当前查询条件导出商户 IP 白名单。
     *
     * @param query 查询条件
     * @param response HTTP 响应
     */
    @PostMapping("/export")
    @RequiresPermission("merchant:ip-whitelist:export")
    @OperationLog(moduleName = "商户IP白名单管理", businessType = OperationTypeConstants.EXPORT, operation = "导出商户IP白名单")
    public void exportWhitelists(@RequestBody(required = false) MerchantIpWhitelistQuery query,
                                 HttpServletResponse response) {
        whitelistApplicationService.exportWhitelists(query, currentOperatorName(), response);
    }

    /**
     * 查询白名单详情。
     *
     * @param id 白名单记录 ID
     * @return 白名单详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("merchant:ip-whitelist:detail")
    public CommonResult<MerchantIpWhitelistResponse> getWhitelist(@PathVariable("id") Long id) {
        return success(whitelistApplicationService.getWhitelist(id));
    }

    /**
     * 新增商户 IP 白名单。
     *
     * @param request 新增请求
     * @return 新增后的记录集合
     */
    @PostMapping
    @RequiresPermission("merchant:ip-whitelist:add")
    @OperationLog(moduleName = "商户IP白名单管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户IP白名单")
    public CommonResult<List<MerchantIpWhitelistResponse>> createWhitelists(@Valid @RequestBody MerchantIpWhitelistCreateRequest request) {
        return success(whitelistApplicationService.createWhitelists(request));
    }

    /**
     * 修改单条商户 IP 白名单。
     *
     * @param id      白名单记录 ID
     * @param request 更新请求
     * @return 更新后的记录
     */
    @PutMapping("/{id}")
    @RequiresPermission("merchant:ip-whitelist:edit")
    @OperationLog(moduleName = "商户IP白名单管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户IP白名单")
    public CommonResult<MerchantIpWhitelistResponse> updateWhitelist(@PathVariable("id") Long id,
                                                                     @Valid @RequestBody MerchantIpWhitelistUpdateRequest request) {
        return success(whitelistApplicationService.updateWhitelist(id, request));
    }

    /**
     * 更新 IP 白名单状态。
     *
     * @param id      白名单记录 ID
     * @param request 状态请求
     * @return 更新后的记录
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("merchant:ip-whitelist:status")
    @OperationLog(moduleName = "商户IP白名单管理", businessType = OperationTypeConstants.UPDATE, operation = "切换商户IP白名单状态")
    public CommonResult<MerchantIpWhitelistResponse> updateWhitelistStatus(@PathVariable("id") Long id,
                                                                           @Valid @RequestBody MerchantIpWhitelistStatusRequest request) {
        return success(whitelistApplicationService.updateWhitelistStatus(id, request.getStatus()));
    }

    /**
     * 更新商户维度 IP 白名单校验开关。
     *
     * @param request 开关请求
     * @return 当前配置视图
     */
    @PutMapping("/config")
    @RequiresPermission("merchant:ip-whitelist:config")
    @OperationLog(moduleName = "商户IP白名单管理", businessType = OperationTypeConstants.UPDATE, operation = "切换商户IP白名单校验")
    public CommonResult<MerchantIpWhitelistResponse> updateConfig(@Valid @RequestBody MerchantIpWhitelistConfigRequest request) {
        return success(whitelistApplicationService.updateConfig(request));
    }

    /**
     * 删除单条 IP 白名单记录。
     *
     * @param id 白名单记录 ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("merchant:ip-whitelist:remove")
    @OperationLog(moduleName = "商户IP白名单管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户IP白名单")
    public CommonResult<Void> deleteWhitelist(@PathVariable("id") Long id) {
        whitelistApplicationService.deleteWhitelist(id);
        return success();
    }

    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (account.getRealName() != null && !account.getRealName().isBlank()) {
            return account.getRealName();
        }
        return account.getLoginAccount();
    }
}
