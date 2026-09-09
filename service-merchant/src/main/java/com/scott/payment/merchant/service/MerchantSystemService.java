package com.scott.payment.merchant.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountBaseSaveRequest;
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

import java.util.List;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSystemService
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : 商户系统服务契约，位于 商户后台服务，声明该业务能力的输入、返回结果和异常边界，由实现类保持一致。
 * @status : create
 */
public interface MerchantSystemService {

    /**
     * 查询部门；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 商户后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    List<DeptDTO> listDepts();

    /**
     * 查询部门；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 商户后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<DeptDTO> pageDepts(DeptQueryRequest request);

    /**
     * 构建部门树，按层级关系组装树形业务视图。
     * @return 当前商户可见的部门树
     */
    List<DeptDTO> deptTree();

    /**
     * 创建部门，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 商户后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    DeptDTO createDept(DeptSaveRequest request);

    /**
     * 更新部门，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 商户后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    DeptDTO updateDept(Long id, DeptSaveRequest request);

    /**
     * 删除或停用部门，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 商户后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteDept(Long id);

    /**
     * 查询岗位；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 商户后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    List<PostDTO> listPosts();

    /**
     * 查询岗位；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 商户后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<PostDTO> pagePosts(PostQueryRequest request);

    /**
     * 创建岗位，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 商户后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    PostDTO createPost(PostSaveRequest request);

    /**
     * 更新岗位，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 商户后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    PostDTO updatePost(Long id, PostSaveRequest request);

    /**
     * 删除或停用岗位，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 商户后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deletePost(Long id);

    /**
     * 查询账号；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 商户后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    List<AccountDTO> listAccounts();

    /**
     * 查询账号；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 商户后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<AccountDTO> pageAccounts(AccountQueryRequest request);

    /**
     * 创建账号，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 商户后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    AccountDTO createAccount(AccountSaveRequest request);

    /**
     * 更新账号，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 商户后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    AccountDTO updateAccount(Long id, AccountSaveRequest request);

    /**
     * 更新账号基础信息，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 商户后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    AccountDTO updateAccountBase(Long id, AccountBaseSaveRequest request);

    /**
     * 删除或停用账号，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 商户后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteAccount(Long id);

    /**
     * 更新账号密码，保持业务状态、配置项或展示字段与请求意图一致。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    void resetAccountPassword(Long id, AccountResetPasswordRequest request);

    /**
     * 更新账号状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 商户后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     */
    void updateAccountStatus(Long id, Integer status);

    /**
     * 更新账号角色，保持业务状态、配置项或展示字段与请求意图一致。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    void assignAccountRoles(Long id, IdsRequest request);

    /**
     * 更新账号部门，保持业务状态、配置项或展示字段与请求意图一致。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    void assignAccountDepts(Long id, IdsRequest request);

    /**
     * 更新账号岗位，保持业务状态、配置项或展示字段与请求意图一致。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    void assignAccountPosts(Long id, IdsRequest request);

    /**
     * 将指定账号设置为强制使用 MFA，并返回最新绑定状态。
     * <p>
     * 校验失败时按 商户后台服务 统一异常语义中断流程，不返回部分校验结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 更新后的账号 MFA 状态
     */
    AccountMfaStatusResponse requireAccountMfa(Long id, AccountMfaActionRequest request);

    /**
     * 更新账号 MFA，保持业务状态、配置项或展示字段与请求意图一致。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 重置后的账号 MFA 状态
     */
    AccountMfaStatusResponse resetAccountMfa(Long id, AccountMfaActionRequest request);

    /**
     * 为账号 MFA 设置受控豁免，并保留操作原因和审计上下文。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 豁免后的账号 MFA 状态
     */
    AccountMfaStatusResponse exemptAccountMfa(Long id, AccountMfaExemptRequest request);

    /**
     * 停用账号 MFA，并使现有绑定不再参与登录校验。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 停用后的账号 MFA 状态
     */
    AccountMfaStatusResponse disableAccountMfa(Long id, AccountMfaActionRequest request);

    /**
     * 解除账号 MFA 锁定状态，恢复符合条件的后续登录操作。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 解锁后的账号 MFA 状态
     */
    AccountMfaStatusResponse unlockAccountMfa(Long id, AccountMfaActionRequest request);

    /**
     * 重新发送账号 MFA 绑定邮件，并保留当前操作人审计信息。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 重新发送后的账号 MFA 状态
     */
    AccountMfaStatusResponse resendAccountMfaBindMail(Long id, AccountMfaActionRequest request);

    /**
     * 查询角色；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 商户后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    List<RoleDTO> listRoles();

    /**
     * 查询角色；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 商户后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<RoleDTO> pageRoles(RoleQueryRequest request);

    /**
     * 查询角色；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 商户后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    RoleDTO getRole(Long id);

    /**
     * 创建角色，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 商户后台服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    RoleDTO createRole(RoleSaveRequest request);

    /**
     * 更新角色，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 商户后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    RoleDTO updateRole(Long id, RoleSaveRequest request);

    /**
     * 删除或停用角色，调用方需保证权限和状态允许该操作。
     * <p>
     * 删除或停用必须通过 商户后台服务 既有权限和状态校验，并沿用软删除约定。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteRole(Long id);

    /**
     * 更新角色状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 状态或配置变更必须通过 商户后台服务 既有权限、版本和状态流转校验。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     */
    void updateRoleStatus(Long id, Integer status);

    /**
     * 构建角色授权树空白模板，供新增角色时展示全部可授权节点。
     * @return 未勾选授权项的完整角色授权树
     */
    RoleGrantTreeDTO roleGrantTreeTemplate();

    /**
     * 构建角色授权树，按层级关系组装树形业务视图。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 带当前角色授权勾选状态的授权树
     */
    RoleGrantTreeDTO roleGrantTree(Long id);

    /**
     * 更新角色授权树，保持业务状态、配置项或展示字段与请求意图一致。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    void grantRoleTree(Long id, RoleGrantTreeSaveRequest request);

    /**
     * 查询指定角色已授权的菜单集合。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 已授权菜单主键及菜单明细
     */
    RoleMenuAuthDTO roleMenus(Long id);

    /**
     * 更新角色菜单，保持业务状态、配置项或展示字段与请求意图一致。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    void grantRoleMenus(Long id, IdsRequest request);

    /**
     * 查询指定角色已授权的权限集合。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 已授权权限主键及权限明细
     */
    RolePermissionAuthDTO rolePermissions(Long id);

    /**
     * 更新角色权限，保持业务状态、配置项或展示字段与请求意图一致。
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    void grantRolePermissions(Long id, IdsRequest request);

    /**
     * 查询当前商户授权范围内可分配的权限集合。
     * @return 可分配权限列表
     */
    List<PermissionDTO> grantedPermissions();
}
