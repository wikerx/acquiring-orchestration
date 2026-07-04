package com.scott.payment.merchant.controller;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountBaseSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.IdsRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PermissionDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleGrantTreeDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleGrantTreeSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleMenuAuthDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RolePermissionAuthDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.StatusRequest;
import com.scott.payment.merchant.service.MerchantSystemService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
 * @classname : MerchantSystemController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Merchant System 管理接口，位于 service-merchant 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/merchant/system")
public class MerchantSystemController {

    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final MerchantSystemService merchantSystemService;

    /**
     * 创建商户系统基础管理接口。
     *
     * @param merchantSystemService 商户系统基础管理服务
     */
    public MerchantSystemController(MerchantSystemService merchantSystemService) {
        this.merchantSystemService = merchantSystemService;
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/depts/tree")
    @RequiresPermission("merchant:system:dept:list")
    public CommonResult<List<DeptDTO>> deptTree() {
        return success(merchantSystemService.deptTree());
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/depts")
    @RequiresPermission("merchant:system:dept:list")
    public CommonResult<List<DeptDTO>> depts() {
        return success(merchantSystemService.listDepts());
    }

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/depts/page")
    @RequiresPermission("merchant:system:dept:list")
    public CommonResult<PageResult<DeptDTO>> pageDepts(@ModelAttribute DeptQueryRequest request) {
        return success(merchantSystemService.pageDepts(request));
    }

    /**
     * 创建或保存商户管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/depts")
    @RequiresPermission("merchant:system:dept:add")
    @OperationLog(moduleName = "商户部门管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户部门")
    public CommonResult<DeptDTO> createDept(@RequestBody DeptSaveRequest request) {
        return success(merchantSystemService.createDept(request));
    }

    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/depts/{id}")
    @RequiresPermission("merchant:system:dept:edit")
    @OperationLog(moduleName = "商户部门管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户部门")
    public CommonResult<DeptDTO> updateDept(@PathVariable("id") Long id, @RequestBody DeptSaveRequest request) {
        return success(merchantSystemService.updateDept(id, request));
    }

    /**
     * 删除商户管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @DeleteMapping("/depts/{id}")
    @RequiresPermission("merchant:system:dept:delete")
    @OperationLog(moduleName = "商户部门管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户部门")
    public CommonResult<Void> deleteDept(@PathVariable("id") Long id) {
        merchantSystemService.deleteDept(id);
        return success();
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/posts")
    @RequiresPermission("merchant:system:post:list")
    public CommonResult<List<PostDTO>> posts() {
        return success(merchantSystemService.listPosts());
    }

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/posts/page")
    @RequiresPermission("merchant:system:post:list")
    public CommonResult<PageResult<PostDTO>> pagePosts(@ModelAttribute PostQueryRequest request) {
        return success(merchantSystemService.pagePosts(request));
    }

    /**
     * 创建或保存商户管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/posts")
    @RequiresPermission("merchant:system:post:add")
    @OperationLog(moduleName = "商户岗位管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户岗位")
    public CommonResult<PostDTO> createPost(@RequestBody PostSaveRequest request) {
        return success(merchantSystemService.createPost(request));
    }

    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/posts/{id}")
    @RequiresPermission("merchant:system:post:edit")
    @OperationLog(moduleName = "商户岗位管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户岗位")
    public CommonResult<PostDTO> updatePost(@PathVariable("id") Long id, @RequestBody PostSaveRequest request) {
        return success(merchantSystemService.updatePost(id, request));
    }

    /**
     * 删除商户管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @DeleteMapping("/posts/{id}")
    @RequiresPermission("merchant:system:post:delete")
    @OperationLog(moduleName = "商户岗位管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户岗位")
    public CommonResult<Void> deletePost(@PathVariable("id") Long id) {
        merchantSystemService.deletePost(id);
        return success();
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/accounts")
    @RequiresPermission("merchant:system:account:list")
    public CommonResult<List<AccountDTO>> accounts() {
        return success(merchantSystemService.listAccounts());
    }

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/accounts/page")
    @RequiresPermission("merchant:system:account:list")
    public CommonResult<PageResult<AccountDTO>> pageAccounts(@ModelAttribute AccountQueryRequest request) {
        return success(merchantSystemService.pageAccounts(request));
    }

    /**
     * 创建或保存商户管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/accounts")
    @RequiresPermission("merchant:system:account:add")
    @OperationLog(moduleName = "商户员工管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户员工")
    public CommonResult<AccountDTO> createAccount(@RequestBody AccountSaveRequest request) {
        return success(merchantSystemService.createAccount(request));
    }

    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/accounts/{id}")
    @RequiresPermission("merchant:system:account:edit")
    @OperationLog(moduleName = "商户员工管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户员工")
    public CommonResult<AccountDTO> updateAccount(@PathVariable("id") Long id, @RequestBody AccountBaseSaveRequest request) {
        return success(merchantSystemService.updateAccountBase(id, request));
    }

    /**
     * 删除商户管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @DeleteMapping("/accounts/{id}")
    @RequiresPermission("merchant:system:account:delete")
    @OperationLog(moduleName = "商户员工管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户员工")
    public CommonResult<Void> deleteAccount(@PathVariable("id") Long id) {
        merchantSystemService.deleteAccount(id);
        return success();
    }

    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/accounts/{id}/status")
    @RequiresPermission("merchant:system:account:status")
    public CommonResult<Void> updateAccountStatus(@PathVariable("id") Long id, @RequestBody StatusRequest request) {
        merchantSystemService.updateAccountStatus(id, request.getStatus());
        return success();
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/accounts/{id}/roles")
    @RequiresPermission("merchant:system:account:assignRole")
    public CommonResult<Void> assignAccountRoles(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.assignAccountRoles(id, request);
        return success();
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/accounts/{id}/depts")
    @RequiresPermission("merchant:system:account:edit")
    public CommonResult<Void> assignAccountDepts(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.assignAccountDepts(id, request);
        return success();
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/accounts/{id}/posts")
    @RequiresPermission("merchant:system:account:edit")
    public CommonResult<Void> assignAccountPosts(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.assignAccountPosts(id, request);
        return success();
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/roles")
    @RequiresPermission("merchant:system:role:list")
    public CommonResult<List<RoleDTO>> roles() {
        return success(merchantSystemService.listRoles());
    }

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/roles/page")
    @RequiresPermission("merchant:system:role:list")
    public CommonResult<PageResult<RoleDTO>> pageRoles(@ModelAttribute RoleQueryRequest request) {
        return success(merchantSystemService.pageRoles(request));
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/roles/{id}")
    @RequiresPermission("merchant:system:role:detail")
    public CommonResult<RoleDTO> roleDetail(@PathVariable("id") Long id) {
        return success(merchantSystemService.getRole(id));
    }

    /**
     * 创建或保存商户管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/roles")
    @RequiresPermission("merchant:system:role:add")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户角色")
    public CommonResult<RoleDTO> createRole(@RequestBody RoleSaveRequest request) {
        return success(merchantSystemService.createRole(request));
    }

    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/roles/{id}")
    @RequiresPermission("merchant:system:role:edit")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户角色")
    public CommonResult<RoleDTO> updateRole(@PathVariable("id") Long id, @RequestBody RoleSaveRequest request) {
        return success(merchantSystemService.updateRole(id, request));
    }

    /**
     * 删除商户管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @DeleteMapping("/roles/{id}")
    @RequiresPermission("merchant:system:role:delete")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户角色")
    public CommonResult<Void> deleteRole(@PathVariable("id") Long id) {
        merchantSystemService.deleteRole(id);
        return success();
    }

    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/roles/{id}/status")
    @RequiresPermission("merchant:system:role:status")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户角色状态")
    public CommonResult<Void> updateRoleStatus(@PathVariable("id") Long id, @RequestBody StatusRequest request) {
        merchantSystemService.updateRoleStatus(id, request.getStatus());
        return success();
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/roles/grant-tree-template")
    @RequiresPermission("merchant:system:role:grantMenu")
    public CommonResult<RoleGrantTreeDTO> roleGrantTreeTemplate() {
        return success(merchantSystemService.roleGrantTreeTemplate());
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/roles/{id}/grant-tree")
    @RequiresPermission("merchant:system:role:grantMenu")
    public CommonResult<RoleGrantTreeDTO> roleGrantTree(@PathVariable("id") Long id) {
        return success(merchantSystemService.roleGrantTree(id));
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/roles/{id}/grant-tree")
    @RequiresPermission("merchant:system:role:grantMenu")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.UPDATE, operation = "授权商户角色")
    public CommonResult<Void> grantRoleTree(@PathVariable("id") Long id, @RequestBody RoleGrantTreeSaveRequest request) {
        merchantSystemService.grantRoleTree(id, request);
        return success();
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/roles/{id}/menus")
    @RequiresPermission("merchant:system:role:grantMenu")
    public CommonResult<RoleMenuAuthDTO> roleMenus(@PathVariable("id") Long id) {
        return success(merchantSystemService.roleMenus(id));
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/roles/{id}/menus")
    @RequiresPermission("merchant:system:role:grantMenu")
    public CommonResult<Void> grantRoleMenus(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.grantRoleMenus(id, request);
        return success();
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/roles/{id}/permissions")
    @RequiresPermission("merchant:system:role:grantPermission")
    public CommonResult<RolePermissionAuthDTO> rolePermissions(@PathVariable("id") Long id) {
        return success(merchantSystemService.rolePermissions(id));
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/roles/{id}/permissions")
    @RequiresPermission("merchant:system:role:grantPermission")
    public CommonResult<Void> grantRolePermissions(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.grantRolePermissions(id, request);
        return success();
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/permissions")
    @RequiresPermission("merchant:system:role:grantPermission")
    public CommonResult<List<PermissionDTO>> grantedPermissions() {
        return success(merchantSystemService.grantedPermissions());
    }
}
