package com.scott.payment.admin.api;

import com.scott.payment.admin.dto.SysMenuCreateRequest;
import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysMenuQueryRequest;
import com.scott.payment.admin.dto.SysMenuStatusRequest;
import com.scott.payment.admin.dto.SysMenuUpdateRequest;
import com.scott.payment.admin.service.AdminMenuService;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMenuController
 * @date : 2026-06-07 00:00
 * @description : 管理后台菜单内部接口
 * @status : create
 */
@RestController
@RequestMapping("/admin/system/menus")
public class AdminMenuController {

    private final AdminMenuService menuService;

    public AdminMenuController(AdminMenuService menuService) {
        this.menuService = menuService;
    }

    @PostMapping("/tree")
    @RequiresPermission("system:menu:list")
    @OperationLog(moduleName = "菜单管理", businessType = OperationTypeConstants.QUERY, operation = "查询后台菜单树")
    public CommonResult<List<SysMenuDTO>> treeMenus(@RequestBody(required = false) SysMenuQueryRequest request) {
        return success(menuService.treeMenus(request));
    }

    @PostMapping("/create")
    @RequiresPermission("system:menu:add")
    @OperationLog(moduleName = "菜单管理", businessType = OperationTypeConstants.CREATE, operation = "新增后台菜单")
    public CommonResult<SysMenuDTO> createMenu(@Valid @RequestBody SysMenuCreateRequest request) {
        return success(menuService.createMenu(request));
    }

    @PostMapping("/update")
    @RequiresPermission("system:menu:edit")
    @OperationLog(moduleName = "菜单管理", businessType = OperationTypeConstants.UPDATE, operation = "编辑后台菜单")
    public CommonResult<SysMenuDTO> updateMenu(@Valid @RequestBody SysMenuUpdateRequest request) {
        return success(menuService.updateMenu(request));
    }

    @PostMapping("/status")
    @RequiresPermission("system:menu:edit")
    @OperationLog(moduleName = "菜单管理", businessType = OperationTypeConstants.UPDATE, operation = "更新后台菜单状态")
    public CommonResult<Void> updateStatus(@Valid @RequestBody SysMenuStatusRequest request) {
        menuService.updateStatus(request);
        return success();
    }
}
