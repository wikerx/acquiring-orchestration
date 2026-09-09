package com.scott.payment.merchant.controller;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountBaseSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountMfaActionRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountMfaExemptRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountMfaStatusResponse;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountResetPasswordRequest;
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
import jakarta.validation.Valid;
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
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : 商户系统 HTTP 控制器，位于 商户后台服务，只承接参数、鉴权注解和统一响应，业务编排委托应用服务。
 * @status : create
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

    /**
     * 查询商户组织部门树，供账号数据范围配置使用。
     *
     * @return 部门树
     */
    @GetMapping("/depts/tree")
    @RequiresPermission("merchant:system:dept:list")
    public CommonResult<List<DeptDTO>> deptTree() {
        return success(merchantSystemService.deptTree());
    }

    /**
     * 查询商户全部有效部门。
     *
     * @return 部门列表
     */
    @GetMapping("/depts")
    @RequiresPermission("merchant:system:dept:list")
    public CommonResult<List<DeptDTO>> depts() {
        return success(merchantSystemService.listDepts());
    }

    /**
     * 分页查询商户部门。
     *
     * @param request 部门筛选和分页条件
     * @return 部门分页结果
     */
    @GetMapping("/depts/page")
    @RequiresPermission("merchant:system:dept:list")
    public CommonResult<PageResult<DeptDTO>> pageDepts(@ModelAttribute DeptQueryRequest request) {
        return success(merchantSystemService.pageDepts(request));
    }

    /**
     * 新增商户部门并记录管理操作日志。
     *
     * @param request 部门保存请求
     * @return 新建部门
     */
    @PostMapping("/depts")
    @RequiresPermission("merchant:system:dept:add")
    @OperationLog(moduleName = "商户部门管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户部门")
    public CommonResult<DeptDTO> createDept(@RequestBody DeptSaveRequest request) {
        return success(merchantSystemService.createDept(request));
    }

    /**
     * 更新商户部门基本信息和层级关系。
     *
     * @param id      部门主键
     * @param request 部门保存请求
     * @return 更新后的部门
     */
    @PutMapping("/depts/{id}")
    @RequiresPermission("merchant:system:dept:edit")
    @OperationLog(moduleName = "商户部门管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户部门")
    public CommonResult<DeptDTO> updateDept(@PathVariable("id") Long id, @RequestBody DeptSaveRequest request) {
        return success(merchantSystemService.updateDept(id, request));
    }

    /**
     * 删除商户部门；存在关联账号或子部门时由服务层拒绝。
     *
     * @param id 部门主键
     * @return 空结果
     */
    @DeleteMapping("/depts/{id}")
    @RequiresPermission("merchant:system:dept:delete")
    @OperationLog(moduleName = "商户部门管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户部门")
    public CommonResult<Void> deleteDept(@PathVariable("id") Long id) {
        merchantSystemService.deleteDept(id);
        return success();
    }

    /**
     * 查询商户全部有效岗位。
     *
     * @return 岗位列表
     */
    @GetMapping("/posts")
    @RequiresPermission("merchant:system:post:list")
    public CommonResult<List<PostDTO>> posts() {
        return success(merchantSystemService.listPosts());
    }

    /**
     * 分页查询商户岗位。
     *
     * @param request 岗位筛选和分页条件
     * @return 岗位分页结果
     */
    @GetMapping("/posts/page")
    @RequiresPermission("merchant:system:post:list")
    public CommonResult<PageResult<PostDTO>> pagePosts(@ModelAttribute PostQueryRequest request) {
        return success(merchantSystemService.pagePosts(request));
    }

    /**
     * 新增商户岗位并记录管理操作日志。
     *
     * @param request 岗位保存请求
     * @return 新建岗位
     */
    @PostMapping("/posts")
    @RequiresPermission("merchant:system:post:add")
    @OperationLog(moduleName = "商户岗位管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户岗位")
    public CommonResult<PostDTO> createPost(@RequestBody PostSaveRequest request) {
        return success(merchantSystemService.createPost(request));
    }

    /**
     * 更新商户岗位信息。
     *
     * @param id      岗位主键
     * @param request 岗位保存请求
     * @return 更新后的岗位
     */
    @PutMapping("/posts/{id}")
    @RequiresPermission("merchant:system:post:edit")
    @OperationLog(moduleName = "商户岗位管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户岗位")
    public CommonResult<PostDTO> updatePost(@PathVariable("id") Long id, @RequestBody PostSaveRequest request) {
        return success(merchantSystemService.updatePost(id, request));
    }

    /**
     * 删除商户岗位；账号关联约束由服务层校验。
     *
     * @param id 岗位主键
     * @return 空结果
     */
    @DeleteMapping("/posts/{id}")
    @RequiresPermission("merchant:system:post:delete")
    @OperationLog(moduleName = "商户岗位管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户岗位")
    public CommonResult<Void> deletePost(@PathVariable("id") Long id) {
        merchantSystemService.deletePost(id);
        return success();
    }

    /**
     * 查询商户全部有效员工账号，不返回密码、盐或 MFA 密钥。
     *
     * @return 员工账号列表
     */
    @GetMapping("/accounts")
    @RequiresPermission("merchant:system:account:list")
    public CommonResult<List<AccountDTO>> accounts() {
        return success(merchantSystemService.listAccounts());
    }

    /**
     * 分页查询商户员工账号。
     *
     * @param request 账号筛选和分页条件
     * @return 不含认证密钥的账号分页结果
     */
    @GetMapping("/accounts/page")
    @RequiresPermission("merchant:system:account:list")
    public CommonResult<PageResult<AccountDTO>> pageAccounts(@ModelAttribute AccountQueryRequest request) {
        return success(merchantSystemService.pageAccounts(request));
    }

    /**
     * 创建商户员工账号，密码哈希和默认授权由服务层处理。
     *
     * @param request 账号保存请求
     * @return 新建账号的非敏感信息
     */
    @PostMapping("/accounts")
    @RequiresPermission("merchant:system:account:add")
    @OperationLog(moduleName = "商户员工管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户员工")
    public CommonResult<AccountDTO> createAccount(@RequestBody AccountSaveRequest request) {
        return success(merchantSystemService.createAccount(request));
    }

    /**
     * 更新商户员工基础资料，不通过该入口修改密码和 MFA 材料。
     *
     * @param id      账号主键
     * @param request 基础资料保存请求
     * @return 更新后的非敏感账号信息
     */
    @PutMapping("/accounts/{id}")
    @RequiresPermission("merchant:system:account:edit")
    @OperationLog(moduleName = "商户员工管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户员工")
    public CommonResult<AccountDTO> updateAccount(@PathVariable("id") Long id, @RequestBody AccountBaseSaveRequest request) {
        return success(merchantSystemService.updateAccountBase(id, request));
    }

    /**
     * 逻辑删除商户员工账号并终止其后续登录资格。
     *
     * @param id 账号主键
     * @return 空结果
     */
    @DeleteMapping("/accounts/{id}")
    @RequiresPermission("merchant:system:account:delete")
    @OperationLog(moduleName = "商户员工管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户员工")
    public CommonResult<Void> deleteAccount(@PathVariable("id") Long id) {
        merchantSystemService.deleteAccount(id);
        return success();
    }

    /**
     * 重置商户员工登录密码，并强制注销该账号已有会话。
     *
     * @param id      员工账号ID
     * @param request 重置密码请求
     * @return 空结果
     */
    @PostMapping("/accounts/{id}/reset-password")
    @RequiresPermission("merchant:system:account:resetPassword")
    @OperationLog(moduleName = "商户员工管理", businessType = OperationTypeConstants.UPDATE, operation = "重置商户员工密码")
    public CommonResult<Void> resetAccountPassword(@PathVariable("id") Long id,
                                                   @Valid @RequestBody AccountResetPasswordRequest request) {
        merchantSystemService.resetAccountPassword(id, request);
        return success();
    }

    /**
     * 切换商户员工账号状态。
     *
     * @param id      账号主键
     * @param request 目标状态
     * @return 空结果
     */
    @PutMapping("/accounts/{id}/status")
    @RequiresPermission("merchant:system:account:status")
    public CommonResult<Void> updateAccountStatus(@PathVariable("id") Long id, @RequestBody StatusRequest request) {
        merchantSystemService.updateAccountStatus(id, request.getStatus());
        return success();
    }

    /**
     * 全量替换员工账号的角色授权。
     *
     * @param id      账号主键
     * @param request 角色主键集合
     * @return 空结果
     */
    @PostMapping("/accounts/{id}/roles")
    @RequiresPermission("merchant:system:account:assignRole")
    public CommonResult<Void> assignAccountRoles(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.assignAccountRoles(id, request);
        return success();
    }

    /**
     * 全量替换员工账号的部门数据范围。
     *
     * @param id      账号主键
     * @param request 部门主键集合
     * @return 空结果
     */
    @PostMapping("/accounts/{id}/depts")
    @RequiresPermission("merchant:system:account:edit")
    public CommonResult<Void> assignAccountDepts(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.assignAccountDepts(id, request);
        return success();
    }

    /**
     * 全量替换员工账号的岗位关系。
     *
     * @param id      账号主键
     * @param request 岗位主键集合
     * @return 空结果
     */
    @PostMapping("/accounts/{id}/posts")
    @RequiresPermission("merchant:system:account:edit")
    public CommonResult<Void> assignAccountPosts(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.assignAccountPosts(id, request);
        return success();
    }

    /**
     * 强制启用商户员工 MFA。
     *
     * @param id      账号ID
     * @param request 操作请求
     * @return MFA 状态
     */
    @PostMapping("/accounts/{id}/mfa/require")
    @RequiresPermission("merchant:system:account:mfa:require")
    @OperationLog(moduleName = "商户员工 MFA", businessType = OperationTypeConstants.UPDATE, operation = "强制启用商户员工 MFA")
    public CommonResult<AccountMfaStatusResponse> requireAccountMfa(@PathVariable("id") Long id,
                                                                    @Valid @RequestBody AccountMfaActionRequest request) {
        return success(merchantSystemService.requireAccountMfa(id, request));
    }

    /**
     * 重置商户员工 MFA。
     *
     * @param id      账号ID
     * @param request 操作请求
     * @return MFA 状态
     */
    @PostMapping("/accounts/{id}/mfa/reset")
    @RequiresPermission("merchant:system:account:mfa:reset")
    @OperationLog(moduleName = "商户员工 MFA", businessType = OperationTypeConstants.UPDATE, operation = "重置商户员工 MFA")
    public CommonResult<AccountMfaStatusResponse> resetAccountMfa(@PathVariable("id") Long id,
                                                                  @Valid @RequestBody AccountMfaActionRequest request) {
        return success(merchantSystemService.resetAccountMfa(id, request));
    }

    /**
     * 豁免商户员工 MFA。
     *
     * @param id      账号ID
     * @param request 豁免请求
     * @return MFA 状态
     */
    @PostMapping("/accounts/{id}/mfa/exempt")
    @RequiresPermission("merchant:system:account:mfa:exempt")
    @OperationLog(moduleName = "商户员工 MFA", businessType = OperationTypeConstants.UPDATE, operation = "豁免商户员工 MFA")
    public CommonResult<AccountMfaStatusResponse> exemptAccountMfa(@PathVariable("id") Long id,
                                                                   @Valid @RequestBody AccountMfaExemptRequest request) {
        return success(merchantSystemService.exemptAccountMfa(id, request));
    }

    /**
     * 停用商户员工 MFA。
     *
     * @param id      账号ID
     * @param request 操作请求
     * @return MFA 状态
     */
    @PostMapping("/accounts/{id}/mfa/disable")
    @RequiresPermission("merchant:system:account:mfa:disable")
    @OperationLog(moduleName = "商户员工 MFA", businessType = OperationTypeConstants.UPDATE, operation = "停用商户员工 MFA")
    public CommonResult<AccountMfaStatusResponse> disableAccountMfa(@PathVariable("id") Long id,
                                                                    @Valid @RequestBody AccountMfaActionRequest request) {
        return success(merchantSystemService.disableAccountMfa(id, request));
    }

    /**
     * 解锁商户员工 MFA。
     *
     * @param id      账号ID
     * @param request 操作请求
     * @return MFA 状态
     */
    @PostMapping("/accounts/{id}/mfa/unlock")
    @RequiresPermission("merchant:system:account:mfa:unlock")
    @OperationLog(moduleName = "商户员工 MFA", businessType = OperationTypeConstants.UPDATE, operation = "解锁商户员工 MFA")
    public CommonResult<AccountMfaStatusResponse> unlockAccountMfa(@PathVariable("id") Long id,
                                                                   @Valid @RequestBody AccountMfaActionRequest request) {
        return success(merchantSystemService.unlockAccountMfa(id, request));
    }

    /**
     * 重发商户员工 MFA 绑定邮件。
     *
     * @param id      账号ID
     * @param request 操作请求
     * @return MFA 状态
     */
    @PostMapping("/accounts/{id}/mfa/resend-bind-mail")
    @RequiresPermission("merchant:system:account:mfa:resend")
    @OperationLog(moduleName = "商户员工 MFA", businessType = OperationTypeConstants.UPDATE, operation = "重发商户员工 MFA 绑定邮件")
    public CommonResult<AccountMfaStatusResponse> resendAccountMfaBindMail(@PathVariable("id") Long id,
                                                                          @Valid @RequestBody AccountMfaActionRequest request) {
        return success(merchantSystemService.resendAccountMfaBindMail(id, request));
    }

    /**
     * 查询商户全部有效角色。
     *
     * @return 角色列表
     */
    @GetMapping("/roles")
    @RequiresPermission("merchant:system:role:list")
    public CommonResult<List<RoleDTO>> roles() {
        return success(merchantSystemService.listRoles());
    }

    /**
     * 分页查询商户角色。
     *
     * @param request 角色筛选和分页条件
     * @return 角色分页结果
     */
    @GetMapping("/roles/page")
    @RequiresPermission("merchant:system:role:list")
    public CommonResult<PageResult<RoleDTO>> pageRoles(@ModelAttribute RoleQueryRequest request) {
        return success(merchantSystemService.pageRoles(request));
    }

    /**
     * 查询商户角色详情。
     *
     * @param id 角色主键
     * @return 角色详情
     */
    @GetMapping("/roles/{id}")
    @RequiresPermission("merchant:system:role:detail")
    public CommonResult<RoleDTO> roleDetail(@PathVariable("id") Long id) {
        return success(merchantSystemService.getRole(id));
    }

    /**
     * 创建商户角色。
     *
     * @param request 角色保存请求
     * @return 新建角色
     */
    @PostMapping("/roles")
    @RequiresPermission("merchant:system:role:add")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户角色")
    public CommonResult<RoleDTO> createRole(@RequestBody RoleSaveRequest request) {
        return success(merchantSystemService.createRole(request));
    }

    /**
     * 更新商户角色基础信息，不隐式改变菜单或权限授权。
     *
     * @param id      角色主键
     * @param request 角色保存请求
     * @return 更新后的角色
     */
    @PutMapping("/roles/{id}")
    @RequiresPermission("merchant:system:role:edit")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户角色")
    public CommonResult<RoleDTO> updateRole(@PathVariable("id") Long id, @RequestBody RoleSaveRequest request) {
        return success(merchantSystemService.updateRole(id, request));
    }

    /**
     * 删除商户角色；账号仍关联该角色时由服务层拒绝。
     *
     * @param id 角色主键
     * @return 空结果
     */
    @DeleteMapping("/roles/{id}")
    @RequiresPermission("merchant:system:role:delete")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户角色")
    public CommonResult<Void> deleteRole(@PathVariable("id") Long id) {
        merchantSystemService.deleteRole(id);
        return success();
    }

    /**
     * 切换商户角色状态。
     *
     * @param id      角色主键
     * @param request 目标状态
     * @return 空结果
     */
    @PutMapping("/roles/{id}/status")
    @RequiresPermission("merchant:system:role:status")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户角色状态")
    public CommonResult<Void> updateRoleStatus(@PathVariable("id") Long id, @RequestBody StatusRequest request) {
        merchantSystemService.updateRoleStatus(id, request.getStatus());
        return success();
    }

    /**
     * 查询当前商户可授权的完整菜单、按钮和权限树。
     *
     * @return 授权树模板
     */
    @GetMapping("/roles/grant-tree-template")
    @RequiresPermission("merchant:system:role:grantMenu")
    public CommonResult<RoleGrantTreeDTO> roleGrantTreeTemplate() {
        return success(merchantSystemService.roleGrantTreeTemplate());
    }

    /**
     * 查询角色授权树及当前勾选状态。
     *
     * @param id 角色主键
     * @return 角色授权树
     */
    @GetMapping("/roles/{id}/grant-tree")
    @RequiresPermission("merchant:system:role:grantMenu")
    public CommonResult<RoleGrantTreeDTO> roleGrantTree(@PathVariable("id") Long id) {
        return success(merchantSystemService.roleGrantTree(id));
    }

    /**
     * 全量保存角色的菜单、按钮和权限树授权。
     *
     * @param id      角色主键
     * @param request 授权树选择结果
     * @return 空结果
     */
    @PostMapping("/roles/{id}/grant-tree")
    @RequiresPermission("merchant:system:role:grantMenu")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.UPDATE, operation = "授权商户角色")
    public CommonResult<Void> grantRoleTree(@PathVariable("id") Long id, @RequestBody RoleGrantTreeSaveRequest request) {
        merchantSystemService.grantRoleTree(id, request);
        return success();
    }

    /**
     * 查询角色可选菜单和已授权菜单。
     *
     * @param id 角色主键
     * @return 菜单授权信息
     */
    @GetMapping("/roles/{id}/menus")
    @RequiresPermission("merchant:system:role:grantMenu")
    public CommonResult<RoleMenuAuthDTO> roleMenus(@PathVariable("id") Long id) {
        return success(merchantSystemService.roleMenus(id));
    }

    /**
     * 全量替换角色菜单授权。
     *
     * @param id      角色主键
     * @param request 菜单主键集合
     * @return 空结果
     */
    @PostMapping("/roles/{id}/menus")
    @RequiresPermission("merchant:system:role:grantMenu")
    public CommonResult<Void> grantRoleMenus(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.grantRoleMenus(id, request);
        return success();
    }

    /**
     * 查询角色可选权限和已授权权限。
     *
     * @param id 角色主键
     * @return 权限授权信息
     */
    @GetMapping("/roles/{id}/permissions")
    @RequiresPermission("merchant:system:role:grantPermission")
    public CommonResult<RolePermissionAuthDTO> rolePermissions(@PathVariable("id") Long id) {
        return success(merchantSystemService.rolePermissions(id));
    }

    /**
     * 全量替换角色权限授权。
     *
     * @param id      角色主键
     * @param request 权限主键集合
     * @return 空结果
     */
    @PostMapping("/roles/{id}/permissions")
    @RequiresPermission("merchant:system:role:grantPermission")
    public CommonResult<Void> grantRolePermissions(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.grantRolePermissions(id, request);
        return success();
    }

    /**
     * 查询当前商户已获平台授予、可继续分配给角色的权限。
     *
     * @return 可分配权限列表
     */
    @GetMapping("/permissions")
    @RequiresPermission("merchant:system:role:grantPermission")
    public CommonResult<List<PermissionDTO>> grantedPermissions() {
        return success(merchantSystemService.grantedPermissions());
    }
}
