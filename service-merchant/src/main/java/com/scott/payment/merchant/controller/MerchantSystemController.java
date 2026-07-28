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

@RestController
@RequestMapping("/merchant/system")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSystemController
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : Merchant System Controller 控制器，位于 商户后台服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
public class MerchantSystemController {

    /**
     * merchant System Service 依赖，用于 Merchant System Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
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

    @GetMapping("/depts/page")
    @RequiresPermission("merchant:system:dept:list")
    public CommonResult<PageResult<DeptDTO>> pageDepts(@ModelAttribute DeptQueryRequest request) {
        return success(merchantSystemService.pageDepts(request));
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

    @GetMapping("/posts/page")
    @RequiresPermission("merchant:system:post:list")
    public CommonResult<PageResult<PostDTO>> pagePosts(@ModelAttribute PostQueryRequest request) {
        return success(merchantSystemService.pagePosts(request));
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

    @GetMapping("/accounts/page")
    @RequiresPermission("merchant:system:account:list")
    public CommonResult<PageResult<AccountDTO>> pageAccounts(@ModelAttribute AccountQueryRequest request) {
        return success(merchantSystemService.pageAccounts(request));
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

    @GetMapping("/roles")
    @RequiresPermission("merchant:system:role:list")
    public CommonResult<List<RoleDTO>> roles() {
        return success(merchantSystemService.listRoles());
    }

    @GetMapping("/roles/page")
    @RequiresPermission("merchant:system:role:list")
    public CommonResult<PageResult<RoleDTO>> pageRoles(@ModelAttribute RoleQueryRequest request) {
        return success(merchantSystemService.pageRoles(request));
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
