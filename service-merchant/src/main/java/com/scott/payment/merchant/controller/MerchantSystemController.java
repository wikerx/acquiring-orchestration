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
 * @description : MerchantSystemController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class MerchantSystemController {

    /**
     * merchant System Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
    /**
     * 完成 dept Tree 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<List<DeptDTO>> deptTree() {
        return success(merchantSystemService.deptTree());
    }

    @GetMapping("/depts")
    @RequiresPermission("merchant:system:dept:list")
    /**
     * 完成 depts 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<List<DeptDTO>> depts() {
        return success(merchantSystemService.listDepts());
    }

    @GetMapping("/depts/page")
    @RequiresPermission("merchant:system:dept:list")
    /**
     * 完成 page Depts 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<PageResult<DeptDTO>> pageDepts(@ModelAttribute DeptQueryRequest request) {
        return success(merchantSystemService.pageDepts(request));
    }

    @PostMapping("/depts")
    @RequiresPermission("merchant:system:dept:add")
    @OperationLog(moduleName = "商户部门管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户部门")
    /**
     * 完成 create Dept 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<DeptDTO> createDept(@RequestBody DeptSaveRequest request) {
        return success(merchantSystemService.createDept(request));
    }

    @PutMapping("/depts/{id}")
    @RequiresPermission("merchant:system:dept:edit")
    @OperationLog(moduleName = "商户部门管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户部门")
    /**
     * 写入或更新 update Dept 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<DeptDTO> updateDept(@PathVariable("id") Long id, @RequestBody DeptSaveRequest request) {
        return success(merchantSystemService.updateDept(id, request));
    }

    @DeleteMapping("/depts/{id}")
    @RequiresPermission("merchant:system:dept:delete")
    @OperationLog(moduleName = "商户部门管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户部门")
    /**
     * 完成 delete Dept 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> deleteDept(@PathVariable("id") Long id) {
        merchantSystemService.deleteDept(id);
        return success();
    }

    @GetMapping("/posts")
    @RequiresPermission("merchant:system:post:list")
    /**
     * 完成 posts 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<List<PostDTO>> posts() {
        return success(merchantSystemService.listPosts());
    }

    @GetMapping("/posts/page")
    @RequiresPermission("merchant:system:post:list")
    /**
     * 完成 page Posts 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<PageResult<PostDTO>> pagePosts(@ModelAttribute PostQueryRequest request) {
        return success(merchantSystemService.pagePosts(request));
    }

    @PostMapping("/posts")
    @RequiresPermission("merchant:system:post:add")
    @OperationLog(moduleName = "商户岗位管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户岗位")
    /**
     * 完成 create Post 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<PostDTO> createPost(@RequestBody PostSaveRequest request) {
        return success(merchantSystemService.createPost(request));
    }

    @PutMapping("/posts/{id}")
    @RequiresPermission("merchant:system:post:edit")
    @OperationLog(moduleName = "商户岗位管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户岗位")
    /**
     * 写入或更新 update Post 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<PostDTO> updatePost(@PathVariable("id") Long id, @RequestBody PostSaveRequest request) {
        return success(merchantSystemService.updatePost(id, request));
    }

    @DeleteMapping("/posts/{id}")
    @RequiresPermission("merchant:system:post:delete")
    @OperationLog(moduleName = "商户岗位管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户岗位")
    /**
     * 完成 delete Post 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> deletePost(@PathVariable("id") Long id) {
        merchantSystemService.deletePost(id);
        return success();
    }

    @GetMapping("/accounts")
    @RequiresPermission("merchant:system:account:list")
    /**
     * 完成 accounts 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<List<AccountDTO>> accounts() {
        return success(merchantSystemService.listAccounts());
    }

    @GetMapping("/accounts/page")
    @RequiresPermission("merchant:system:account:list")
    /**
     * 完成 page Accounts 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<PageResult<AccountDTO>> pageAccounts(@ModelAttribute AccountQueryRequest request) {
        return success(merchantSystemService.pageAccounts(request));
    }

    @PostMapping("/accounts")
    @RequiresPermission("merchant:system:account:add")
    @OperationLog(moduleName = "商户员工管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户员工")
    /**
     * 完成 create Account 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<AccountDTO> createAccount(@RequestBody AccountSaveRequest request) {
        return success(merchantSystemService.createAccount(request));
    }

    @PutMapping("/accounts/{id}")
    @RequiresPermission("merchant:system:account:edit")
    @OperationLog(moduleName = "商户员工管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户员工")
    /**
     * 写入或更新 update Account 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<AccountDTO> updateAccount(@PathVariable("id") Long id, @RequestBody AccountBaseSaveRequest request) {
        return success(merchantSystemService.updateAccountBase(id, request));
    }

    @DeleteMapping("/accounts/{id}")
    @RequiresPermission("merchant:system:account:delete")
    @OperationLog(moduleName = "商户员工管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户员工")
    /**
     * 完成 delete Account 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 写入或更新 update Account Status 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> updateAccountStatus(@PathVariable("id") Long id, @RequestBody StatusRequest request) {
        merchantSystemService.updateAccountStatus(id, request.getStatus());
        return success();
    }

    @PostMapping("/accounts/{id}/roles")
    @RequiresPermission("merchant:system:account:assignRole")
    /**
     * 完成 assign Account Roles 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> assignAccountRoles(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.assignAccountRoles(id, request);
        return success();
    }

    @PostMapping("/accounts/{id}/depts")
    @RequiresPermission("merchant:system:account:edit")
    /**
     * 完成 assign Account Depts 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> assignAccountDepts(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.assignAccountDepts(id, request);
        return success();
    }

    @PostMapping("/accounts/{id}/posts")
    @RequiresPermission("merchant:system:account:edit")
    /**
     * 完成 assign Account Posts 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 完成 roles 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<List<RoleDTO>> roles() {
        return success(merchantSystemService.listRoles());
    }

    @GetMapping("/roles/page")
    @RequiresPermission("merchant:system:role:list")
    /**
     * 完成 page Roles 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<PageResult<RoleDTO>> pageRoles(@ModelAttribute RoleQueryRequest request) {
        return success(merchantSystemService.pageRoles(request));
    }

    @GetMapping("/roles/{id}")
    @RequiresPermission("merchant:system:role:detail")
    /**
     * 完成 role Detail 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<RoleDTO> roleDetail(@PathVariable("id") Long id) {
        return success(merchantSystemService.getRole(id));
    }

    @PostMapping("/roles")
    @RequiresPermission("merchant:system:role:add")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户角色")
    /**
     * 完成 create Role 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<RoleDTO> createRole(@RequestBody RoleSaveRequest request) {
        return success(merchantSystemService.createRole(request));
    }

    @PutMapping("/roles/{id}")
    @RequiresPermission("merchant:system:role:edit")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户角色")
    /**
     * 写入或更新 update Role 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<RoleDTO> updateRole(@PathVariable("id") Long id, @RequestBody RoleSaveRequest request) {
        return success(merchantSystemService.updateRole(id, request));
    }

    @DeleteMapping("/roles/{id}")
    @RequiresPermission("merchant:system:role:delete")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户角色")
    /**
     * 完成 delete Role 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> deleteRole(@PathVariable("id") Long id) {
        merchantSystemService.deleteRole(id);
        return success();
    }

    @PutMapping("/roles/{id}/status")
    @RequiresPermission("merchant:system:role:status")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户角色状态")
    /**
     * 写入或更新 update Role Status 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> updateRoleStatus(@PathVariable("id") Long id, @RequestBody StatusRequest request) {
        merchantSystemService.updateRoleStatus(id, request.getStatus());
        return success();
    }

    @GetMapping("/roles/grant-tree-template")
    @RequiresPermission("merchant:system:role:grantMenu")
    /**
     * 完成 role Grant Tree Template 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<RoleGrantTreeDTO> roleGrantTreeTemplate() {
        return success(merchantSystemService.roleGrantTreeTemplate());
    }

    @GetMapping("/roles/{id}/grant-tree")
    @RequiresPermission("merchant:system:role:grantMenu")
    /**
     * 完成 role Grant Tree 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<RoleGrantTreeDTO> roleGrantTree(@PathVariable("id") Long id) {
        return success(merchantSystemService.roleGrantTree(id));
    }

    @PostMapping("/roles/{id}/grant-tree")
    @RequiresPermission("merchant:system:role:grantMenu")
    @OperationLog(moduleName = "商户角色管理", businessType = OperationTypeConstants.UPDATE, operation = "授权商户角色")
    /**
     * 完成 grant Role Tree 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> grantRoleTree(@PathVariable("id") Long id, @RequestBody RoleGrantTreeSaveRequest request) {
        merchantSystemService.grantRoleTree(id, request);
        return success();
    }

    @GetMapping("/roles/{id}/menus")
    @RequiresPermission("merchant:system:role:grantMenu")
    /**
     * 完成 role Menus 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<RoleMenuAuthDTO> roleMenus(@PathVariable("id") Long id) {
        return success(merchantSystemService.roleMenus(id));
    }

    @PostMapping("/roles/{id}/menus")
    @RequiresPermission("merchant:system:role:grantMenu")
    /**
     * 完成 grant Role Menus 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> grantRoleMenus(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.grantRoleMenus(id, request);
        return success();
    }

    @GetMapping("/roles/{id}/permissions")
    @RequiresPermission("merchant:system:role:grantPermission")
    /**
     * 完成 role Permissions 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<RolePermissionAuthDTO> rolePermissions(@PathVariable("id") Long id) {
        return success(merchantSystemService.rolePermissions(id));
    }

    @PostMapping("/roles/{id}/permissions")
    @RequiresPermission("merchant:system:role:grantPermission")
    /**
     * 完成 grant Role Permissions 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> grantRolePermissions(@PathVariable("id") Long id, @RequestBody IdsRequest request) {
        merchantSystemService.grantRolePermissions(id, request);
        return success();
    }

    @GetMapping("/permissions")
    @RequiresPermission("merchant:system:role:grantPermission")
    /**
     * 完成 granted Permissions 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<List<PermissionDTO>> grantedPermissions() {
        return success(merchantSystemService.grantedPermissions());
    }
}
