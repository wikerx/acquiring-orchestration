package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminMenuApplicationService;
import com.scott.payment.admin.dto.SysMenuCreateRequest;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMenuController
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台菜单管理控制器
 * @status : create
 *
 * <p>菜单树查询、菜单维护和状态切换均通过
 * {@link AdminMenuApplicationService} 编排，Controller 仅负责 HTTP 入口职责。</p>
 */
@RestController
@RequestMapping("/admin/system/menus")
public class AdminMenuController {

    private final AdminMenuApplicationService adminMenuApplicationService;

    /**
     * 创建菜单管理控制器。
     *
     * @param adminMenuApplicationService 菜单应用服务
     */
    public AdminMenuController(AdminMenuApplicationService adminMenuApplicationService) {
        this.adminMenuApplicationService = adminMenuApplicationService;
    }

    /**
     * 查询后台菜单树。
     *
     * @param request 查询条件
     * @return 菜单树列表
     */
    @PostMapping("/tree")
    @RequiresPermission("system:menu:list")
    @OperationLog(moduleName = "菜单管理", businessType = OperationTypeConstants.QUERY, operation = "查询后台菜单树")
    public CommonResult<List<SysMenuDTO>> treeMenus(@RequestBody(required = false) SysMenuQueryRequest request) {
        return success(adminMenuApplicationService.treeMenus(request));
    }

    /**
     * 新增后台菜单。
     *
     * @param request 新增请求
     * @return 新增后的菜单
     */
    @PostMapping("/create")
    @RequiresPermission("system:menu:add")
    @OperationLog(moduleName = "菜单管理", businessType = OperationTypeConstants.CREATE, operation = "新增后台菜单")
    public CommonResult<SysMenuDTO> createMenu(@Valid @RequestBody SysMenuCreateRequest request) {
        return success(adminMenuApplicationService.createMenu(request));
    }

    /**
     * 编辑后台菜单。
     *
     * @param request 更新请求
     * @return 更新后的菜单
     */
    @PostMapping("/update")
    @RequiresPermission("system:menu:edit")
    @OperationLog(moduleName = "菜单管理", businessType = OperationTypeConstants.UPDATE, operation = "编辑后台菜单")
    public CommonResult<SysMenuDTO> updateMenu(@Valid @RequestBody SysMenuUpdateRequest request) {
        return success(adminMenuApplicationService.updateMenu(request));
    }

    /**
     * 更新后台菜单状态。
     *
     * @param request 状态更新请求
     * @return 空响应
     */
    @PostMapping("/status")
    @RequiresPermission("system:menu:edit")
    @OperationLog(moduleName = "菜单管理", businessType = OperationTypeConstants.UPDATE, operation = "更新后台菜单状态")
    public CommonResult<Void> updateStatus(@Valid @RequestBody SysMenuStatusRequest request) {
        adminMenuApplicationService.updateStatus(request);
        return success();
    }

}
