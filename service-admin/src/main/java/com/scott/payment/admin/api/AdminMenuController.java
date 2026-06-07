package com.scott.payment.admin.api;

import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysMenuQueryRequest;
import com.scott.payment.admin.service.AdminMenuService;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
        return CommonResult.success(menuService.treeMenus(request));
    }
}
