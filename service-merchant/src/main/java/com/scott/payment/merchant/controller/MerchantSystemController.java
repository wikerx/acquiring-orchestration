package com.scott.payment.merchant.controller;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountBaseSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.IdsRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PermissionDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleGrantTreeDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleGrantTreeSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleMenuAuthDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RolePermissionAuthDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.StatusRequest;
import com.scott.payment.merchant.service.MerchantSystemService;
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
 * 商户系统基础管理接口。
 *
 * <p>提供商户内部部门、岗位、员工账号、角色和授权维护能力。</p>
 */
@RestController
@RequestMapping("/merchant/system")
public class MerchantSystemController {

    private final MerchantSystemService merchantSystemService;

    /**
     * 创建商户系统基础管理接口。
     *
     * @param merchantSystemService 商户系统基础管理服务
     */
    public MerchantSystemController(MerchantSystemService merchantSystemService) {
        this.merchantSystemService = merchantSystemService;
    }

    @GetMapping("/depts/tree")
    @RequiresPermission("merchant:system:dept:list")
    public CommonResult<List<DeptDTO>> deptTree() {
        return success(merchantSystemService.deptTree());
    }

    @GetMapping("/depts")
    @RequiresPermission("merchant:system:dept:list")
    public CommonResult<List<DeptDTO>> depts() {
        return success(merchantSystemService.listDepts());
    }

    @PostMapping("/depts")
    @RequiresPermission("merchant:system:dept:add")
    @OperationLog(moduleName = "商户部门管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户部门")
    public CommonResult<DeptDTO> createDept(@RequestBody DeptSaveRequest request) {
        return success(merchantSystemService.createDept(request));
    }

    @PutMapping("/depts/{id}")
    @RequiresPermission("merchant:system:dept:edit")
    @OperationLog(moduleName = "商户部门管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户部门")
    public CommonResult<DeptDTO> updateDept(@PathVariable("id") Long id, @RequestBody DeptSaveRequest request) {
        return success(merchantSystemService.updateDept(id, request));
    }

    @DeleteMapping("/depts/{id}")
    @RequiresPermission("merchant:system:dept:delete")
    @OperationLog(moduleName = "商户部门管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户部门")
    public CommonResult<Void> deleteDept(@PathVariable("id") Long id) {
        merchantSystemService.deleteDept(id);
        return success();
    }

    @GetMapping("/posts")
    @RequiresPermission("merchant:system:post:list")
    public CommonResult<List<PostDTO>> posts() {
        return success(merchantSystemService.listPosts());
    }

    @PostMapping("/posts")
    @RequiresPermission("merchant:system:post:add")
    @OperationLog(moduleName = "商户岗位管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户岗位")
    public CommonResult<PostDTO> createPost(@RequestBody PostSaveRequest request) {
        return success(merchantSystemService.createPost(request));
    }

    @PutMapping("/posts/{id}")
    @RequiresPermission("merchant:system:post:edit")
    @OperationLog(moduleName = "商户岗位管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户岗位")
    public CommonResult<PostDTO> updatePost(@PathVariable("id") Long id, @RequestBody PostSaveRequest request) {
        return success(merchantSystemService.updatePost(id, request));
    }

    @DeleteMapping("/posts/{id}")
    @RequiresPermission("merchant:system:post:delete")
    @OperationLog(moduleName = "商户岗位管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户岗位")
    public CommonResult<Void> deletePost(@PathVariable("id") Long id) {
        merchantSystemService.deletePost(id);
        return success();
    }

    @GetMapping("/accounts")
    @RequiresPermission("merchant:system:account:list")
    public CommonResult<List<AccountDTO>> accounts() {
        return success(merchantSystemService.listAccounts());
    }

    @PostMapping("/accounts")
    @RequiresPermission("merchant:system:account:add")
    @OperationLog(moduleName = "商户员工管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户员工")
    public CommonResult<AccountDTO> createAccount(@RequestBody AccountSaveRequest request) {
        return success(merchantSystemService.createAccount(request));
    }

    @PutMapping("/accounts/{id}")
    @RequiresPermission("merchant:system:account:edit")
    @OperationLog(moduleName = "商户员工管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户员工")
    public CommonResult<AccountDTO> updateAccount(@PathVariable("id") Long id, @RequestBody AccountBaseSaveRequest request) {
        return success(merchantSystemService.updateAccountBase(id, request));
    }

    @DeleteMapping("/accounts/{id}")
    @RequiresPermission("merchant:system:account:delete")
    @OperationLog(moduleName = "商户员工管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户员工")
    public CommonResult<Void> deleteAccount(@PathVariable("id") Long id) {
        merchantSystemService.deleteAccount(id);
        return success();
    }

    @PutMapping("/accounts/{id}/status")
    @RequiresPermission("merchant:system:account:status")
    public CommonResult<Void> updateAccountStatus(@PathVariable("id") Long id, @RequestBody StatusRequest request) {
        merchantSystemService.updateAccountStatus(id, request.getStatus());
        return success();
    }

    @PostMapping("/accounts/{id}/roles")
    @RequiresPermission("merchant:system:account:assignRole")
    public CommonResult<Void> assignAccountRoles(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.assignAccountRoles(id, request);
        return success();
    }

    @PostMapping("/accounts/{id}/depts")
    @RequiresPermission("merchant:system:account:edit")
    public CommonResult<Void> assignAccountDepts(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.assignAccountDepts(id, request);
        return success();
    }

    @PostMapping("/accounts/{id}/posts")
    @RequiresPermission("merchant:system:account:edit")
    public CommonResult<Void> assignAccountPosts(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.assignAccountPosts(id, request);
        return success();
    }

    @GetMapping("/roles")
    @RequiresPermission("merchant:system:role:list")
    public CommonResult<List<RoleDTO>> roles() {
        return success(merchantSystemService.listRoles());
    }

    @GetMapping("/roles/{id}")
    @RequiresPermission("merchant:system:role:detail")
    public CommonResult<RoleDTO> roleDetail(@PathVariable("id") Long id) {
        return success(merchantSystemService.getRole(id));
    }

    @PostMapping("/roles")
    @RequiresPermission("merchant:system:role:add")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户角色")
    public CommonResult<RoleDTO> createRole(@RequestBody RoleSaveRequest request) {
        return success(merchantSystemService.createRole(request));
    }

    @PutMapping("/roles/{id}")
    @RequiresPermission("merchant:system:role:edit")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户角色")
    public CommonResult<RoleDTO> updateRole(@PathVariable("id") Long id, @RequestBody RoleSaveRequest request) {
        return success(merchantSystemService.updateRole(id, request));
    }

    @DeleteMapping("/roles/{id}")
    @RequiresPermission("merchant:system:role:delete")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户角色")
    public CommonResult<Void> deleteRole(@PathVariable("id") Long id) {
        merchantSystemService.deleteRole(id);
        return success();
    }

    @PutMapping("/roles/{id}/status")
    @RequiresPermission("merchant:system:role:status")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户角色状态")
    public CommonResult<Void> updateRoleStatus(@PathVariable("id") Long id, @RequestBody StatusRequest request) {
        merchantSystemService.updateRoleStatus(id, request.getStatus());
        return success();
    }

    @GetMapping("/roles/grant-tree-template")
    @RequiresPermission("merchant:system:role:grantMenu")
    public CommonResult<RoleGrantTreeDTO> roleGrantTreeTemplate() {
        return success(merchantSystemService.roleGrantTreeTemplate());
    }

    @GetMapping("/roles/{id}/grant-tree")
    @RequiresPermission("merchant:system:role:grantMenu")
    public CommonResult<RoleGrantTreeDTO> roleGrantTree(@PathVariable("id") Long id) {
        return success(merchantSystemService.roleGrantTree(id));
    }

    @PostMapping("/roles/{id}/grant-tree")
    @RequiresPermission("merchant:system:role:grantMenu")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.UPDATE, operation = "授权商户角色")
    public CommonResult<Void> grantRoleTree(@PathVariable("id") Long id, @RequestBody RoleGrantTreeSaveRequest request) {
        merchantSystemService.grantRoleTree(id, request);
        return success();
    }

    @GetMapping("/roles/{id}/menus")
    @RequiresPermission("merchant:system:role:grantMenu")
    public CommonResult<RoleMenuAuthDTO> roleMenus(@PathVariable("id") Long id) {
        return success(merchantSystemService.roleMenus(id));
    }

    @PostMapping("/roles/{id}/menus")
    @RequiresPermission("merchant:system:role:grantMenu")
    public CommonResult<Void> grantRoleMenus(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.grantRoleMenus(id, request);
        return success();
    }

    @GetMapping("/roles/{id}/permissions")
    @RequiresPermission("merchant:system:role:grantPermission")
    public CommonResult<RolePermissionAuthDTO> rolePermissions(@PathVariable("id") Long id) {
        return success(merchantSystemService.rolePermissions(id));
    }

    @PostMapping("/roles/{id}/permissions")
    @RequiresPermission("merchant:system:role:grantPermission")
    public CommonResult<Void> grantRolePermissions(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.grantRolePermissions(id, request);
        return success();
    }

    @GetMapping("/permissions")
    @RequiresPermission("merchant:system:role:grantPermission")
    public CommonResult<List<PermissionDTO>> grantedPermissions() {
        return success(merchantSystemService.grantedPermissions());
    }
}
