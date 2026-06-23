package com.scott.payment.admin.api.merchant;

import com.scott.payment.admin.application.system.AdminMenuApplicationService;
import com.scott.payment.admin.dto.SysMenuCreateRequest;
import com.scott.payment.admin.dto.SysMenuDeleteRequest;
import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysMenuQueryRequest;
import com.scott.payment.admin.dto.SysMenuStatusRequest;
import com.scott.payment.admin.dto.SysMenuUpdateRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 商户系统菜单管理接口。
 *
 * <p>固定维护 MERCHANT 应用下的菜单、按钮和权限资源，避免与管理后台菜单混用。</p>
 */
@RestController
@RequestMapping("/admin/merchant/menus")
public class AdminMerchantMenuController {

    private final AdminMenuApplicationService adminMenuApplicationService;

    /**
     * 创建商户系统菜单管理接口。
     *
     * @param adminMenuApplicationService 菜单应用服务
     */
    public AdminMerchantMenuController(AdminMenuApplicationService adminMenuApplicationService) {
        this.adminMenuApplicationService = adminMenuApplicationService;
    }

    /**
     * 查询商户系统菜单树。
     *
     * @param request 查询条件
     * @return 菜单树列表
     */
    @PostMapping("/tree")
    @RequiresPermission("merchant:menu-manage:list")
    @OperationLog(moduleName = "商户系统菜单管理", businessType = OperationTypeConstants.QUERY, operation = "查询商户系统菜单树")
    public CommonResult<List<SysMenuDTO>> treeMenus(@RequestBody(required = false) SysMenuQueryRequest request) {
        return success(adminMenuApplicationService.treeMerchantMenus(request));
    }

    /**
     * 新增商户系统菜单。
     *
     * @param request 新增请求
     * @return 新增后的菜单
     */
    @PostMapping("/create")
    @RequiresPermission("merchant:menu-manage:add")
    @OperationLog(moduleName = "商户系统菜单管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户系统菜单")
    public CommonResult<SysMenuDTO> createMenu(@Valid @RequestBody SysMenuCreateRequest request) {
        return success(adminMenuApplicationService.createMerchantMenu(request));
    }

    /**
     * 编辑商户系统菜单。
     *
     * @param request 更新请求
     * @return 更新后的菜单
     */
    @PostMapping("/update")
    @RequiresPermission("merchant:menu-manage:edit")
    @OperationLog(moduleName = "商户系统菜单管理", businessType = OperationTypeConstants.UPDATE, operation = "编辑商户系统菜单")
    public CommonResult<SysMenuDTO> updateMenu(@Valid @RequestBody SysMenuUpdateRequest request) {
        return success(adminMenuApplicationService.updateMerchantMenu(request));
    }

    /**
     * 更新商户系统菜单状态。
     *
     * @param request 状态更新请求
     * @return 空响应
     */
    @PostMapping("/status")
    @RequiresPermission("merchant:menu-manage:edit")
    @OperationLog(moduleName = "商户系统菜单管理", businessType = OperationTypeConstants.UPDATE, operation = "更新商户系统菜单状态")
    public CommonResult<Void> updateStatus(@Valid @RequestBody SysMenuStatusRequest request) {
        adminMenuApplicationService.updateMerchantMenuStatus(request);
        return success();
    }

    /**
     * 删除商户系统菜单。
     *
     * @param request 删除请求
     * @return 空响应
     */
    @PostMapping("/delete")
    @RequiresPermission("merchant:menu-manage:remove")
    @OperationLog(moduleName = "商户系统菜单管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户系统菜单")
    public CommonResult<Void> deleteMenu(@Valid @RequestBody SysMenuDeleteRequest request) {
        adminMenuApplicationService.deleteMerchantMenu(request);
        return success();
    }
}
