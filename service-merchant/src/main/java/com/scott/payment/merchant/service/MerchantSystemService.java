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
 * @description : MerchantSystemService 服务契约，用于声明业务能力、调用边界和返回结果约束，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public interface MerchantSystemService {

    /**
     * 执行 list Depts 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    List<DeptDTO> listDepts();

    /**
     * 执行 page Depts 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PageResult<DeptDTO> pageDepts(DeptQueryRequest request);

    /**
     * 执行 dept Tree 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    List<DeptDTO> deptTree();

    /**
     * 执行 create Dept 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    DeptDTO createDept(DeptSaveRequest request);

    /**
     * 执行 update Dept 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    DeptDTO updateDept(Long id, DeptSaveRequest request);

    /**
     * 执行 delete Dept 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    void deleteDept(Long id);

    /**
     * 执行 list Posts 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    List<PostDTO> listPosts();

    /**
     * 执行 page Posts 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PageResult<PostDTO> pagePosts(PostQueryRequest request);

    /**
     * 执行 create Post 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PostDTO createPost(PostSaveRequest request);

    /**
     * 执行 update Post 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PostDTO updatePost(Long id, PostSaveRequest request);

    /**
     * 执行 delete Post 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    void deletePost(Long id);

    /**
     * 执行 list Accounts 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    List<AccountDTO> listAccounts();

    /**
     * 执行 page Accounts 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PageResult<AccountDTO> pageAccounts(AccountQueryRequest request);

    /**
     * 执行 create Account 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    AccountDTO createAccount(AccountSaveRequest request);

    /**
     * 执行 update Account 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    AccountDTO updateAccount(Long id, AccountSaveRequest request);

    /**
     * 执行 update Account Base 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    AccountDTO updateAccountBase(Long id, AccountBaseSaveRequest request);

    /**
     * 执行 delete Account 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    void deleteAccount(Long id);

    /**
     * 执行 reset Account Password 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     */
    void resetAccountPassword(Long id, AccountResetPasswordRequest request);

    /**
     * 执行 update Account Status 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     */
    void updateAccountStatus(Long id, Integer status);

    /**
     * 执行 assign Account Roles 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     */
    void assignAccountRoles(Long id, IdsRequest request);

    /**
     * 执行 assign Account Depts 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     */
    void assignAccountDepts(Long id, IdsRequest request);

    /**
     * 执行 assign Account Posts 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     */
    void assignAccountPosts(Long id, IdsRequest request);

    /**
     * 执行 require Account Mfa 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    AccountMfaStatusResponse requireAccountMfa(Long id, AccountMfaActionRequest request);

    /**
     * 执行 reset Account Mfa 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    AccountMfaStatusResponse resetAccountMfa(Long id, AccountMfaActionRequest request);

    /**
     * 执行 exempt Account Mfa 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    AccountMfaStatusResponse exemptAccountMfa(Long id, AccountMfaExemptRequest request);

    /**
     * 执行 disable Account Mfa 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    AccountMfaStatusResponse disableAccountMfa(Long id, AccountMfaActionRequest request);

    /**
     * 执行 unlock Account Mfa 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    AccountMfaStatusResponse unlockAccountMfa(Long id, AccountMfaActionRequest request);

    /**
     * 执行 resend Account Mfa Bind Mail 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    AccountMfaStatusResponse resendAccountMfaBindMail(Long id, AccountMfaActionRequest request);

    /**
     * 执行 list Roles 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    List<RoleDTO> listRoles();

    /**
     * 执行 page Roles 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    PageResult<RoleDTO> pageRoles(RoleQueryRequest request);

    /**
     * 执行 get Role 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    RoleDTO getRole(Long id);

    /**
     * 执行 create Role 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    RoleDTO createRole(RoleSaveRequest request);

    /**
     * 执行 update Role 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    RoleDTO updateRole(Long id, RoleSaveRequest request);

    /**
     * 执行 delete Role 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    void deleteRole(Long id);

    /**
     * 执行 update Role Status 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     */
    void updateRoleStatus(Long id, Integer status);

    /**
     * 执行 role Grant Tree Template 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    RoleGrantTreeDTO roleGrantTreeTemplate();

    /**
     * 执行 role Grant Tree 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    RoleGrantTreeDTO roleGrantTree(Long id);

    /**
     * 执行 grant Role Tree 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     */
    void grantRoleTree(Long id, RoleGrantTreeSaveRequest request);

    /**
     * 执行 role Menus 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    RoleMenuAuthDTO roleMenus(Long id);

    /**
     * 执行 grant Role Menus 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     */
    void grantRoleMenus(Long id, IdsRequest request);

    /**
     * 执行 role Permissions 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    RolePermissionAuthDTO rolePermissions(Long id);

    /**
     * 执行 grant Role Permissions 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     */
    void grantRolePermissions(Long id, IdsRequest request);

    /**
     * 执行 granted Permissions 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户后台服务层；输入来源、输出结构和异常语义由 MerchantSystemService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    List<PermissionDTO> grantedPermissions();
}
