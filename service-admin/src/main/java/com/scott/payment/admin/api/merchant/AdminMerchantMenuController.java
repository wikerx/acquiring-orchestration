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

@RestController
@RequestMapping("/admin/merchant/menus")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuController
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : AdminMerchantMenuController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminMerchantMenuController {

    /**
     * admin Menu Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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
