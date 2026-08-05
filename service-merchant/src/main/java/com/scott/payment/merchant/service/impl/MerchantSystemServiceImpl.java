package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.dto.AuthMenuDTO;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaLogDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaTokenDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import com.scott.payment.component.db.auth.entity.SysMerchantAccountDeptDO;
import com.scott.payment.component.db.auth.entity.SysMerchantAccountPostDO;
import com.scott.payment.component.db.auth.entity.SysMerchantDeptDO;
import com.scott.payment.component.db.auth.entity.SysMerchantMenuGrantDO;
import com.scott.payment.component.db.auth.entity.SysMerchantPermissionGrantDO;
import com.scott.payment.component.db.auth.entity.SysMerchantPostDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserRoleDO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.entity.SysRoleMenuDO;
import com.scott.payment.component.db.auth.entity.SysRolePermissionDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaLogMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaTokenMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantAccountDeptMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantAccountPostMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantDeptMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantMenuGrantMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantPermissionGrantMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantPostMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysRolePermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.db.auth.support.MfaSecretCrypto;
import com.scott.payment.component.db.auth.support.TotpUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountBaseSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountMfaActionRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountMfaExemptRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountMfaStatusResponse;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountResetPasswordRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AuthGrantNodeDTO;
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
import com.scott.payment.merchant.service.MerchantConfigService;
import com.scott.payment.merchant.service.MerchantSystemService;
import com.scott.payment.merchant.service.MerchantTemplateEmailService;
import com.scott.payment.merchant.service.MerchantTemplateEmailService.MerchantEmailSendCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSystemServiceImpl
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : Merchant System Service Impl 服务实现，位于 商户后台服务，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。
 * @status : create
 */
public class MerchantSystemServiceImpl implements MerchantSystemService {

    /**
     * ROOT PARENT ID，用于定位 Merchant System Service Impl 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；不允许为空；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final long ROOT_PARENT_ID = 0L;
    /**
     * ROLE TYPE CUSTOM，用于区分 Merchant System Service Impl 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String ROLE_TYPE_CUSTOM = "CUSTOM";
    /**
     * ROLE TYPE SYSTEM，用于区分 Merchant System Service Impl 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String ROLE_TYPE_SYSTEM = "SYSTEM";
    /**
     * DATA SCOPE ALL，用于保存 Merchant System Service Impl 中与 datascopeall 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DATA_SCOPE_ALL = "ALL";
    /**
     * DATA SCOPE SELF，用于保存 Merchant System Service Impl 中与 datascopeself 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DATA_SCOPE_SELF = "SELF";
    /**
     * DATA SCOPE CUSTOM，用于保存 Merchant System Service Impl 中与 datascopecustom 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DATA_SCOPE_CUSTOM = "CUSTOM";
    /**
     * ACCOUNT LOGIN SEPARATOR，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String ACCOUNT_LOGIN_SEPARATOR = "_";
    /**
     * MFA 登录票据类型。
     */
    private static final String MFA_TOKEN_TYPE_LOGIN = "LOGIN_MFA";
    /**
     * MFA 操作结果：成功。
     */
    private static final String MFA_RESULT_SUCCESS = "SUCCESS";
    /**
     * MFA 操作结果：失败。
     */
    private static final String MFA_RESULT_FAILED = "FAILED";
    /**
     * 商户 MFA 邮件场景编码。
     */
    private static final String MERCHANT_MFA_SCENE = "MERCHANT_MFA";
    /**
     * 商户 MFA 绑定邮件模板。
     */
    private static final String TEMPLATE_MFA_BIND_NOTICE = "MERCHANT_MFA_BIND_NOTICE";
    /**
     * 商户 MFA 启用邮件模板。
     */
    private static final String TEMPLATE_MFA_ENABLED_NOTICE = "MERCHANT_MFA_ENABLED_NOTICE";
    /**
     * 商户 MFA 重置邮件模板。
     */
    private static final String TEMPLATE_MFA_RESET_NOTICE = "MERCHANT_MFA_RESET_NOTICE";
    /**
     * 商户 MFA 停用邮件模板。
     */
    private static final String TEMPLATE_MFA_DISABLED_NOTICE = "MERCHANT_MFA_DISABLED_NOTICE";
    /**
     * 商户 MFA 豁免邮件模板。
     */
    private static final String TEMPLATE_MFA_EXEMPT_NOTICE = "MERCHANT_MFA_EXEMPT_NOTICE";
    /**
     * 商户系统账号开户通知模板编码。
     */
    private static final String TEMPLATE_ACCOUNT_CREATED_NOTICE = "MERCHANT_ACCOUNT_CREATED";
    /**
     * 账号开户通知邮件场景编码。
     */
    private static final String ACCOUNT_CREATED_SCENE = "ACCOUNT_CREATED";
    /** 商户管理员重置员工密码后的通知模板。 */
    private static final String TEMPLATE_PASSWORD_CHANGED_NOTICE = "MERCHANT_PASSWORD_CHANGED_BY_ADMIN";
    /** 密码被管理员修改通知场景。 */
    private static final String PASSWORD_CHANGED_SCENE = "PASSWORD_CHANGED";
    /** 邮件操作时间格式。 */
    private static final DateTimeFormatter EMAIL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /**
     * 参数管理中维护的商户系统前端地址。
     */
    private static final String MERCHANT_FRONTEND_BASE_URL_KEY = "platform.merchant.frontend-base-url";

    /**
     * base Merchant Info Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final BaseMerchantInfoMapper baseMerchantInfoMapper;
    /**
     * sys App Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysAppMapper sysAppMapper;
    /**
     * sys User Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysUserMapper sysUserMapper;
    /**
     * sys Account Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysAccountMapper sysAccountMapper;
    /**
     * 商户员工 MFA 配置 Mapper。
     */
    private final SysAccountMfaMapper sysAccountMfaMapper;
    /**
     * 商户员工 MFA 登录票据 Mapper。
     */
    private final SysAccountMfaTokenMapper sysAccountMfaTokenMapper;
    /**
     * 商户员工 MFA 审计日志 Mapper。
     */
    private final SysAccountMfaLogMapper sysAccountMfaLogMapper;
    /**
     * 登录会话 Mapper，用于安全策略变更后强制会话失效。
     */
    private final SysLoginSessionMapper sysLoginSessionMapper;
    /**
     * sys Role Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysRoleMapper sysRoleMapper;
    /**
     * sys Role Menu Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysRoleMenuMapper sysRoleMenuMapper;
    /**
     * sys Role Permission Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysRolePermissionMapper sysRolePermissionMapper;
    /**
     * sys Menu Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMenuMapper sysMenuMapper;
    /**
     * sys Permission Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysPermissionMapper sysPermissionMapper;
    /**
     * sys Merchant Dept Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantDeptMapper sysMerchantDeptMapper;
    /**
     * sys Merchant Post Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantPostMapper sysMerchantPostMapper;
    /**
     * sys Merchant Account Dept Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantAccountDeptMapper sysMerchantAccountDeptMapper;
    /**
     * sys Merchant Account Post Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantAccountPostMapper sysMerchantAccountPostMapper;
    /**
     * sys Merchant Menu Grant Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantMenuGrantMapper sysMerchantMenuGrantMapper;
    /**
     * sys Merchant Permission Grant Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantPermissionGrantMapper sysMerchantPermissionGrantMapper;
    /**
     * sys Merchant User Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantUserMapper sysMerchantUserMapper;
    /**
     * sys Merchant User Role Mapper 依赖，用于 Merchant System Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantUserRoleMapper sysMerchantUserRoleMapper;
    /**
     * 商户模板邮件服务。
     */
    private final MerchantTemplateEmailService merchantTemplateEmailService;
    /**
     * 商户系统只读参数服务，用于读取参数管理中的平台访问地址。
     */
    private final MerchantConfigService merchantConfigService;

    public MerchantSystemServiceImpl(BaseMerchantInfoMapper baseMerchantInfoMapper,
                                     SysAppMapper sysAppMapper,
                                     SysUserMapper sysUserMapper,
                                     SysAccountMapper sysAccountMapper,
                                     SysAccountMfaMapper sysAccountMfaMapper,
                                     SysAccountMfaTokenMapper sysAccountMfaTokenMapper,
                                     SysAccountMfaLogMapper sysAccountMfaLogMapper,
                                     SysLoginSessionMapper sysLoginSessionMapper,
                                     SysRoleMapper sysRoleMapper,
                                     SysRoleMenuMapper sysRoleMenuMapper,
                                     SysRolePermissionMapper sysRolePermissionMapper,
                                     SysMenuMapper sysMenuMapper,
                                     SysPermissionMapper sysPermissionMapper,
                                     SysMerchantDeptMapper sysMerchantDeptMapper,
                                     SysMerchantPostMapper sysMerchantPostMapper,
                                     SysMerchantAccountDeptMapper sysMerchantAccountDeptMapper,
                                     SysMerchantAccountPostMapper sysMerchantAccountPostMapper,
                                     SysMerchantMenuGrantMapper sysMerchantMenuGrantMapper,
                                     SysMerchantPermissionGrantMapper sysMerchantPermissionGrantMapper,
                                     SysMerchantUserMapper sysMerchantUserMapper,
                                     SysMerchantUserRoleMapper sysMerchantUserRoleMapper,
                                     MerchantTemplateEmailService merchantTemplateEmailService,
                                     MerchantConfigService merchantConfigService) {
        this.baseMerchantInfoMapper = baseMerchantInfoMapper;
        this.sysAppMapper = sysAppMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysAccountMapper = sysAccountMapper;
        this.sysAccountMfaMapper = sysAccountMfaMapper;
        this.sysAccountMfaTokenMapper = sysAccountMfaTokenMapper;
        this.sysAccountMfaLogMapper = sysAccountMfaLogMapper;
        this.sysLoginSessionMapper = sysLoginSessionMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysMerchantDeptMapper = sysMerchantDeptMapper;
        this.sysMerchantPostMapper = sysMerchantPostMapper;
        this.sysMerchantAccountDeptMapper = sysMerchantAccountDeptMapper;
        this.sysMerchantAccountPostMapper = sysMerchantAccountPostMapper;
        this.sysMerchantMenuGrantMapper = sysMerchantMenuGrantMapper;
        this.sysMerchantPermissionGrantMapper = sysMerchantPermissionGrantMapper;
        this.sysMerchantUserMapper = sysMerchantUserMapper;
        this.sysMerchantUserRoleMapper = sysMerchantUserRoleMapper;
        this.merchantTemplateEmailService = merchantTemplateEmailService;
        this.merchantConfigService = merchantConfigService;
    }

    /**
     * 查询当前商户全部未删除部门。
     *
     * @return 按排序号和主键升序的部门列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<DeptDTO> listDepts() {
        String merchantId = currentMerchantId();
        return sysMerchantDeptMapper.selectList(Wrappers.<SysMerchantDeptDO>lambdaQuery()
                        .eq(SysMerchantDeptDO::getMerchantId, merchantId)
                        .eq(SysMerchantDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysMerchantDeptDO::getSortNo, SysMerchantDeptDO::getId))
                .stream().map(this::toDeptDTO).toList();
    }

    /**
     * 分页查询当前商户部门，支持状态及名称/编码关键字筛选。
     *
     * @param request 部门查询条件
     * @return 租户隔离的部门分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<DeptDTO> pageDepts(DeptQueryRequest request) {
        DeptQueryRequest query = request == null ? new DeptQueryRequest() : request;
        String keyword = normalize(query.getKeyword());
        Page<SysMerchantDeptDO> page = sysMerchantDeptMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<SysMerchantDeptDO>lambdaQuery()
                        .eq(SysMerchantDeptDO::getMerchantId, currentMerchantId())
                        .eq(SysMerchantDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                        .eq(query.getStatus() != null, SysMerchantDeptDO::getStatus, query.getStatus())
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(SysMerchantDeptDO::getDeptName, keyword)
                                .or()
                                .like(SysMerchantDeptDO::getDeptCode, keyword))
                        .orderByAsc(SysMerchantDeptDO::getSortNo, SysMerchantDeptDO::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toDeptDTO).toList());
    }

    /**
     * 组装当前商户的部门层级树。
     *
     * @return 部门树
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<DeptDTO> deptTree() {
        return buildDeptTree(listDepts());
    }

    /**
     * 创建当前商户部门。
     * <p>
     * 写入前校验父部门归属和部门编码唯一性，审计人取当前账号。
     * </p>
     *
     * @param request 部门保存请求
     * @return 新建部门
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public DeptDTO createDept(DeptSaveRequest request) {
        String merchantId = currentMerchantId();
        validateDeptParent(merchantId, request.getParentId(), null);
        assertDeptCodeAvailable(merchantId, request.getDeptCode(), null);
        SysMerchantDeptDO dept = new SysMerchantDeptDO();
        applyDept(dept, request);
        LocalDateTime now = LocalDateTime.now();
        dept.setMerchantId(merchantId);
        dept.setCreatedAt(now);
        dept.setUpdatedAt(now);
        dept.setCreatedBy(currentAccountId());
        dept.setUpdatedBy(currentAccountId());
        dept.setDeleted(AuthConstants.NOT_DELETED);
        sysMerchantDeptMapper.insert(dept);
        return toDeptDTO(dept);
    }

    /**
     * 更新当前商户部门，禁止选择自身或其他商户部门作为父节点。
     *
     * @param id      部门主键
     * @param request 部门保存请求
     * @return 更新后的部门
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public DeptDTO updateDept(Long id, DeptSaveRequest request) {
        String merchantId = currentMerchantId();
        SysMerchantDeptDO dept = getDept(merchantId, id);
        validateDeptParent(merchantId, request.getParentId(), id);
        assertDeptCodeAvailable(merchantId, request.getDeptCode(), id);
        applyDept(dept, request);
        dept.setUpdatedAt(LocalDateTime.now());
        dept.setUpdatedBy(currentAccountId());
        sysMerchantDeptMapper.updateById(dept);
        return toDeptDTO(dept);
    }

    /**
     * 删除或停用部门，调用方需保证权限和状态允许该操作。
     * <p>
     * 前置条件：调用方已确认 商户后台服务 中目标记录存在、权限满足且状态允许删除或停用。
     * 该方法通常执行软删除、停用或批量标记；幂等结果以记录状态或受影响行数为准。
     * 异常边界：记录不存在、状态禁止删除或数据库更新失败会阻断后续流程。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteDept(Long id) {
        String merchantId = currentMerchantId();
        SysMerchantDeptDO dept = getDept(merchantId, id);
        Long childCount = sysMerchantDeptMapper.selectCount(Wrappers.<SysMerchantDeptDO>lambdaQuery()
                .eq(SysMerchantDeptDO::getMerchantId, merchantId)
                .eq(SysMerchantDeptDO::getParentId, id)
                .eq(SysMerchantDeptDO::getDeleted, AuthConstants.NOT_DELETED));
        if (childCount != null && childCount > 0) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "dept has children");
        }
        Long bindCount = sysMerchantAccountDeptMapper.selectCount(Wrappers.<SysMerchantAccountDeptDO>lambdaQuery()
                .eq(SysMerchantAccountDeptDO::getMerchantId, merchantId)
                .eq(SysMerchantAccountDeptDO::getDeptId, id));
        if (bindCount != null && bindCount > 0) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "dept is assigned to accounts");
        }
        dept.setDeleted(dept.getId());
        dept.setStatus(AuthConstants.DISABLED);
        dept.setUpdatedAt(LocalDateTime.now());
        sysMerchantDeptMapper.updateById(dept);
    }

    /**
     * 查询当前商户全部未删除岗位。
     *
     * @return 按排序号和主键升序的岗位列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<PostDTO> listPosts() {
        String merchantId = currentMerchantId();
        return sysMerchantPostMapper.selectList(Wrappers.<SysMerchantPostDO>lambdaQuery()
                        .eq(SysMerchantPostDO::getMerchantId, merchantId)
                        .eq(SysMerchantPostDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysMerchantPostDO::getSortNo, SysMerchantPostDO::getId))
                .stream().map(this::toPostDTO).toList();
    }

    /**
     * 分页查询当前商户岗位，支持状态及名称/编码关键字筛选。
     *
     * @param request 岗位查询条件
     * @return 租户隔离的岗位分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<PostDTO> pagePosts(PostQueryRequest request) {
        PostQueryRequest query = request == null ? new PostQueryRequest() : request;
        String keyword = normalize(query.getKeyword());
        Page<SysMerchantPostDO> page = sysMerchantPostMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<SysMerchantPostDO>lambdaQuery()
                        .eq(SysMerchantPostDO::getMerchantId, currentMerchantId())
                        .eq(SysMerchantPostDO::getDeleted, AuthConstants.NOT_DELETED)
                        .eq(query.getStatus() != null, SysMerchantPostDO::getStatus, query.getStatus())
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(SysMerchantPostDO::getPostName, keyword)
                                .or()
                                .like(SysMerchantPostDO::getPostCode, keyword))
                        .orderByAsc(SysMerchantPostDO::getSortNo, SysMerchantPostDO::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toPostDTO).toList());
    }

    /**
     * 创建当前商户岗位并校验岗位编码唯一性。
     *
     * @param request 岗位保存请求
     * @return 新建岗位
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public PostDTO createPost(PostSaveRequest request) {
        String merchantId = currentMerchantId();
        assertPostCodeAvailable(merchantId, request.getPostCode(), null);
        SysMerchantPostDO post = new SysMerchantPostDO();
        applyPost(post, request);
        LocalDateTime now = LocalDateTime.now();
        post.setMerchantId(merchantId);
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        post.setCreatedBy(currentAccountId());
        post.setUpdatedBy(currentAccountId());
        post.setDeleted(AuthConstants.NOT_DELETED);
        sysMerchantPostMapper.insert(post);
        return toPostDTO(post);
    }

    /**
     * 更新当前商户岗位并保持岗位编码唯一。
     *
     * @param id      岗位主键
     * @param request 岗位保存请求
     * @return 更新后的岗位
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public PostDTO updatePost(Long id, PostSaveRequest request) {
        String merchantId = currentMerchantId();
        SysMerchantPostDO post = getPost(merchantId, id);
        assertPostCodeAvailable(merchantId, request.getPostCode(), id);
        applyPost(post, request);
        post.setUpdatedAt(LocalDateTime.now());
        post.setUpdatedBy(currentAccountId());
        sysMerchantPostMapper.updateById(post);
        return toPostDTO(post);
    }

    /**
     * 逻辑删除当前商户岗位。
     *
     * @param id 岗位主键
     * @throws ServiceException 岗位仍分配给员工账号时抛出
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long id) {
        String merchantId = currentMerchantId();
        SysMerchantPostDO post = getPost(merchantId, id);
        Long bindCount = sysMerchantAccountPostMapper.selectCount(Wrappers.<SysMerchantAccountPostDO>lambdaQuery()
                .eq(SysMerchantAccountPostDO::getMerchantId, merchantId)
                .eq(SysMerchantAccountPostDO::getPostId, id));
        if (bindCount != null && bindCount > 0) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "post is assigned to accounts");
        }
        post.setDeleted(post.getId());
        post.setStatus(AuthConstants.DISABLED);
        post.setUpdatedAt(LocalDateTime.now());
        sysMerchantPostMapper.updateById(post);
    }

    /**
     * 查询当前商户全部有效员工账号。
     *
     * @return 不包含密码哈希、盐和 MFA 密钥的账号列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<AccountDTO> listAccounts() {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        List<SysMerchantUserDO> users = sysMerchantUserMapper.selectList(Wrappers.<SysMerchantUserDO>lambdaQuery()
                .eq(SysMerchantUserDO::getMerchantId, merchantId)
                .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED)
                .orderByAsc(SysMerchantUserDO::getId));
        return users.stream().map(user -> toAccountDTO(app.getId(), user)).toList();
    }

    /**
     * 分页查询当前商户员工账号。
     * <p>
     * 角色筛选先验证角色属于当前商户；关键字可匹配登录账号、姓名和基础账号信息。
     * </p>
     *
     * @param request 账号查询条件
     * @return 租户隔离的账号分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<AccountDTO> pageAccounts(AccountQueryRequest request) {
        AccountQueryRequest query = request == null ? new AccountQueryRequest() : request;
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        Set<Long> merchantUserIds = merchantUserIdsByRole(app.getId(), query.getRoleId());
        if (query.getRoleId() != null && merchantUserIds.isEmpty()) {
            return PageResult.of(0, query.safePageNo(), query.safePageSize(), Collections.emptyList());
        }
        String keyword = normalize(query.getKeyword());
        Set<Long> accountIdsByKeyword = accountIdsByKeyword(app.getId(), merchantId, keyword);
        Page<SysMerchantUserDO> page = sysMerchantUserMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<SysMerchantUserDO>lambdaQuery()
                        .eq(SysMerchantUserDO::getMerchantId, merchantId)
                        .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED)
                        .eq(query.getStatus() != null, SysMerchantUserDO::getStatus, query.getStatus())
                        .in(query.getRoleId() != null, SysMerchantUserDO::getId, merchantUserIds)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(SysMerchantUserDO::getLoginAccount, keyword)
                                .or()
                                .like(SysMerchantUserDO::getRealName, keyword)
                                .or(!accountIdsByKeyword.isEmpty())
                                .in(!accountIdsByKeyword.isEmpty(), SysMerchantUserDO::getAccountId, accountIdsByKeyword))
                        .orderByDesc(SysMerchantUserDO::getCreatedAt)
                        .orderByAsc(SysMerchantUserDO::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(user -> toAccountDTO(app.getId(), user)).toList());
    }

    /**
     * 创建商户员工的用户、登录账号、默认 MFA 和组织授权关系。
     * <p>
     * 密码只以随机盐哈希落库；创建通知在事务提交后发送，避免回滚记录对应无效账号。
     * </p>
     *
     * @param request 账号及初始授权保存请求
     * @return 新建账号的非敏感信息
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountDTO createAccount(AccountSaveRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        assertAccountAvailable(merchantId, request.getLoginAccount(), null);
        LocalDateTime now = LocalDateTime.now();
        SysUserDO user = new SysUserDO();
        user.setUserType(AuthConstants.USER_TYPE_MERCHANT);
        user.setRealName(required(request.getRealName(), "realName"));
        user.setMobile(normalize(request.getMobile()));
        user.setEmail(required(request.getEmail(), "email"));
        user.setStatus(validStatus(request.getStatus()));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setCreatedBy(currentAccountId());
        user.setUpdatedBy(currentAccountId());
        user.setDeleted(AuthConstants.NOT_DELETED);
        sysUserMapper.insert(user);
        String salt = PasswordHashUtils.generateSalt();
        SysAccountDO account = new SysAccountDO();
        account.setAppId(app.getId());
        account.setUserId(user.getId());
        account.setMerchantId(merchantId);
        account.setLoginAccount(toAccountLoginName(merchantId, request.getLoginAccount()));
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(required(request.getPassword(), "password"), salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        account.setMobile(user.getMobile());
        account.setEmail(user.getEmail());
        account.setMfaEnabled(AuthConstants.DISABLED);
        account.setPasswordExpired(AuthConstants.DISABLED);
        account.setPasswordUpdatedAt(now);
        account.setFailedLoginCount(0);
        account.setLocked(AuthConstants.DISABLED);
        account.setStatus(user.getStatus());
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setCreatedBy(currentAccountId());
        account.setUpdatedBy(currentAccountId());
        account.setDeleted(AuthConstants.NOT_DELETED);
        sysAccountMapper.insert(account);
        createDefaultRequiredMfa(app, account, now);
        SysMerchantUserDO merchantUser = createMerchantUser(merchantId, request.getLoginAccount(), user, account, now);
        replaceAccountRoles(app.getId(), merchantId, merchantUser, request.getRoleIds());
        replaceAccountDepts(merchantId, account.getId(), request.getDeptIds());
        replaceAccountPosts(merchantId, account.getId(), request.getPostIds());
        sendAccountCreatedNoticeAfterCommit(app, account, request.getPassword());
        return toAccountDTO(app.getId(), account);
    }

    /**
     * 更新员工基础资料及角色、部门和岗位关系。
     *
     * @param id      账号主键
     * @param request 完整账号保存请求
     * @return 更新后的非敏感账号信息
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountDTO updateAccount(Long id, AccountSaveRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        SysMerchantUserDO merchantUser = getMerchantUser(merchantId, account.getId());
        updateAccountBaseFields(app.getId(), merchantId, account, request);
        replaceAccountRoles(app.getId(), merchantId, merchantUser, request.getRoleIds());
        replaceAccountDepts(merchantId, account.getId(), request.getDeptIds());
        replaceAccountPosts(merchantId, account.getId(), request.getPostIds());
        return toAccountDTO(app.getId(), account);
    }

    /**
     * 更新员工基础资料，不改变角色、部门、岗位、密码或 MFA 材料。
     *
     * @param id      账号主键
     * @param request 基础资料保存请求
     * @return 更新后的非敏感账号信息
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountDTO updateAccountBase(Long id, AccountBaseSaveRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        SysMerchantUserDO merchantUser = getMerchantUser(merchantId, account.getId());
        updateAccountBaseFields(app.getId(), merchantId, account, request);
        merchantUser.setLoginAccount(required(request.getLoginAccount(), "loginAccount"));
        merchantUser.setRealName(required(request.getRealName(), "realName"));
        merchantUser.setStatus(validStatus(request.getStatus()));
        merchantUser.setUpdatedAt(LocalDateTime.now());
        merchantUser.setUpdatedBy(currentAccountId());
        sysMerchantUserMapper.updateById(merchantUser);
        return toAccountDTO(app.getId(), account);
    }

    /**
     * 逻辑删除并停用员工基础账号和商户成员关系。
     *
     * @param id 账号主键
     * @throws ServiceException 尝试删除当前登录账号时抛出
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long id) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        if (Objects.equals(id, currentAccountId())) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "can not delete current account");
        }
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        account.setDeleted(account.getId());
        account.setStatus(AuthConstants.DISABLED);
        account.setUpdatedAt(LocalDateTime.now());
        sysAccountMapper.updateById(account);
        SysMerchantUserDO merchantUser = getMerchantUser(merchantId, account.getId());
        merchantUser.setDeleted(merchantUser.getId());
        merchantUser.setStatus(AuthConstants.DISABLED);
        merchantUser.setUpdatedAt(LocalDateTime.now());
        merchantUser.setUpdatedBy(currentAccountId());
        sysMerchantUserMapper.updateById(merchantUser);
    }

    /**
     * 重置商户员工密码，并注销该员工当前有效会话。
     * <p>
     * 新密码使用新盐重新哈希；不允许通过管理入口重置当前登录账号，以免误锁定当前会话。
     * </p>
     *
     * @param id      员工账号主键
     * @param request 新密码请求
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void resetAccountPassword(Long id, AccountResetPasswordRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        if (Objects.equals(id, currentAccountId())) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "不能重置当前登录账号密码，请在个人中心修改密码");
        }
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        LocalDateTime now = LocalDateTime.now();
        String temporaryPassword = required(request.getPassword(), "password");
        String salt = PasswordHashUtils.generateSalt();
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(temporaryPassword, salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        account.setPasswordExpired(AuthConstants.DISABLED);
        account.setPasswordUpdatedAt(now);
        account.setFailedLoginCount(0);
        account.setLocked(AuthConstants.DISABLED);
        account.setLockedAt(null);
        account.setLockedReason(null);
        account.setUpdatedAt(now);
        account.setUpdatedBy(currentAccountId());
        sysAccountMapper.updateById(account);
        logoutSessions(app.getId(), account.getId(), now);
        sendPasswordChangedNoticeAfterCommit(app, account, temporaryPassword, now);
    }

    /**
     * 同步切换基础账号、商户成员和 MFA 的启停状态。
     *
     * @param id     账号主键
     * @param status 目标启停状态
     * @throws ServiceException 尝试停用当前登录账号时抛出
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void updateAccountStatus(Long id, Integer status) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        if (Objects.equals(id, currentAccountId()) && validStatus(status) == AuthConstants.DISABLED) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "can not disable current account");
        }
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        account.setStatus(validStatus(status));
        account.setUpdatedAt(LocalDateTime.now());
        sysAccountMapper.updateById(account);
        syncMfaStatusForAccountStatus(account);
        SysMerchantUserDO merchantUser = getMerchantUser(merchantId, account.getId());
        merchantUser.setStatus(validStatus(status));
        merchantUser.setUpdatedAt(LocalDateTime.now());
        merchantUser.setUpdatedBy(currentAccountId());
        sysMerchantUserMapper.updateById(merchantUser);
    }

    /**
     * 全量替换员工角色，并校验角色均属于当前商户。
     *
     * @param id      账号主键
     * @param request 角色主键集合
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void assignAccountRoles(Long id, IdsRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        SysMerchantUserDO merchantUser = getMerchantUser(merchantId, account.getId());
        replaceAccountRoles(app.getId(), merchantId, merchantUser, request.getIds());
    }

    /**
     * 全量替换员工部门数据范围，并校验部门归属当前商户。
     *
     * @param id      账号主键
     * @param request 部门主键集合
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void assignAccountDepts(Long id, IdsRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getAccount(app.getId(), merchantId, id);
        replaceAccountDepts(merchantId, id, request.getIds());
    }

    /**
     * 全量替换员工岗位关系，并校验岗位归属当前商户。
     *
     * @param id      账号主键
     * @param request 岗位主键集合
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void assignAccountPosts(Long id, IdsRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getAccount(app.getId(), merchantId, id);
        replaceAccountPosts(merchantId, id, request.getIds());
    }

    /**
     * 强制启用商户员工 MFA。
     *
     * @param id      员工账号ID
     * @param request 操作请求
     * @return MFA 状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountMfaStatusResponse requireAccountMfa(Long id, AccountMfaActionRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_REQUIRED);
        mfa.setMfaStatus(account.getStatus() != null && account.getStatus() == AuthConstants.DISABLED
                ? AuthConstants.MFA_STATUS_DISABLED
                : AuthConstants.MFA_STATUS_PENDING_BIND);
        mfa.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        mfa.setSecretCipher(null);
        mfa.setPendingSecretCipher(MfaSecretCrypto.encrypt(TotpUtils.generateBase32Secret()));
        mfa.setIssuer("Acquiring Merchant");
        mfa.setAccountLabel(mfaAccountLabel(account));
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setLastSuccessTimeStep(null);
        mfa.setExemptReason(null);
        mfa.setExemptUntil(null);
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
        syncAccountMfaLegacyFields(account, true);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        logoutSessions(app.getId(), account.getId(), now);
        recordMfaLog(app, account, mfa, "REQUIRE", MFA_RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_ENABLED_NOTICE, request.getReason(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_BIND_NOTICE, request.getReason(), null);
        return toMfaStatusResponse(account, mfa);
    }

    /**
     * 重置商户员工 MFA。
     *
     * @param id      员工账号ID
     * @param request 操作请求
     * @return MFA 状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountMfaStatusResponse resetAccountMfa(Long id, AccountMfaActionRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        assertNotSelf(account.getId(), "不能重置当前登录账号自己的 MFA");
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_REQUIRED);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_RESET_REQUIRED);
        mfa.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        mfa.setSecretCipher(null);
        mfa.setPendingSecretCipher(MfaSecretCrypto.encrypt(TotpUtils.generateBase32Secret()));
        mfa.setIssuer("Acquiring Merchant");
        mfa.setAccountLabel(mfaAccountLabel(account));
        mfa.setResetTime(now);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setLastSuccessTimeStep(null);
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
        syncAccountMfaLegacyFields(account, true);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        logoutSessions(app.getId(), account.getId(), now);
        recordMfaLog(app, account, mfa, "RESET", MFA_RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_RESET_NOTICE, request.getReason(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_BIND_NOTICE, request.getReason(), null);
        return toMfaStatusResponse(account, mfa);
    }

    /**
     * 豁免商户员工 MFA。
     *
     * @param id      员工账号ID
     * @param request 豁免请求
     * @return MFA 状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountMfaStatusResponse exemptAccountMfa(Long id, AccountMfaExemptRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        assertNotSelf(account.getId(), "不能豁免当前登录账号自己的 MFA");
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_EXEMPT);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_EXEMPT);
        mfa.setSecretCipher(null);
        mfa.setPendingSecretCipher(null);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setLastSuccessTimeStep(null);
        mfa.setExemptReason(normalize(request.getReason()));
        mfa.setExemptUntil(request.getExemptUntil());
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
        syncAccountMfaLegacyFields(account, false);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        logoutSessions(app.getId(), account.getId(), now);
        recordMfaLog(app, account, mfa, "EXEMPT", MFA_RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_EXEMPT_NOTICE, request.getReason(), request.getExemptUntil());
        return toMfaStatusResponse(account, mfa);
    }

    /**
     * 停用商户员工 MFA。
     *
     * @param id      员工账号ID
     * @param request 操作请求
     * @return MFA 状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountMfaStatusResponse disableAccountMfa(Long id, AccountMfaActionRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        assertNotSelf(account.getId(), "不能停用当前登录账号自己的 MFA");
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_OPTIONAL);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_NOT_ENABLED);
        mfa.setSecretCipher(null);
        mfa.setPendingSecretCipher(null);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setLastSuccessTimeStep(null);
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
        syncAccountMfaLegacyFields(account, false);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        logoutSessions(app.getId(), account.getId(), now);
        recordMfaLog(app, account, mfa, "DISABLE", MFA_RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_DISABLED_NOTICE, request.getReason(), null);
        return toMfaStatusResponse(account, mfa);
    }

    /**
     * 解锁商户员工 MFA。
     *
     * @param id      员工账号ID
     * @param request 操作请求
     * @return MFA 状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountMfaStatusResponse unlockAccountMfa(Long id, AccountMfaActionRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        if (AuthConstants.MFA_STATUS_LOCKED.equals(mfa.getMfaStatus())) {
            mfa.setMfaStatus(StringUtils.hasText(mfa.getSecretCipher())
                    ? AuthConstants.MFA_STATUS_ENABLED
                    : AuthConstants.MFA_STATUS_PENDING_BIND);
        }
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
        recordMfaLog(app, account, mfa, "UNLOCK", MFA_RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), null);
        return toMfaStatusResponse(account, mfa);
    }

    /**
     * 重发商户员工 MFA 绑定邮件。
     *
     * @param id      员工账号ID
     * @param request 操作请求
     * @return MFA 状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountMfaStatusResponse resendAccountMfaBindMail(Long id, AccountMfaActionRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        if (!AuthConstants.MFA_STATUS_PENDING_BIND.equals(mfa.getMfaStatus())
                && !AuthConstants.MFA_STATUS_RESET_REQUIRED.equals(mfa.getMfaStatus())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "MFA 绑定邮件只能对待绑定或需重绑用户重发");
        }
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        if (!StringUtils.hasText(mfa.getPendingSecretCipher())) {
            mfa.setPendingSecretCipher(MfaSecretCrypto.encrypt(TotpUtils.generateBase32Secret()));
        }
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        recordMfaLog(app, account, mfa, "RESEND_BIND_MAIL", MFA_RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_BIND_NOTICE, request.getReason(), null);
        return toMfaStatusResponse(account, mfa);
    }

    /**
     * 查询当前商户全部未删除角色。
     *
     * @return 按排序号和主键升序的角色列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<RoleDTO> listRoles() {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        return sysRoleMapper.selectList(Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getAppId, app.getId())
                        .eq(SysRoleDO::getMerchantId, merchantId)
                        .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysRoleDO::getSortNo, SysRoleDO::getId))
                .stream().map(this::toRoleDTO).toList();
    }

    /**
     * 分页查询当前商户角色，支持名称、编码、状态和创建时间筛选。
     *
     * @param request 角色查询条件
     * @return 租户隔离的角色分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<RoleDTO> pageRoles(RoleQueryRequest request) {
        RoleQueryRequest query = request == null ? new RoleQueryRequest() : request;
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        LocalDateTime startTime = parseQueryDateTime(query.getCreatedStartTime(), false);
        LocalDateTime endTime = parseQueryDateTime(query.getCreatedEndTime(), true);
        Page<SysRoleDO> page = sysRoleMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getAppId, app.getId())
                        .eq(SysRoleDO::getMerchantId, merchantId)
                        .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                        .like(StringUtils.hasText(query.getRoleName()), SysRoleDO::getRoleName, normalize(query.getRoleName()))
                        .like(StringUtils.hasText(query.getRoleCode()), SysRoleDO::getRoleCode, normalize(query.getRoleCode()))
                        .eq(query.getStatus() != null, SysRoleDO::getStatus, query.getStatus())
                        .ge(startTime != null, SysRoleDO::getCreatedAt, startTime)
                        .le(endTime != null, SysRoleDO::getCreatedAt, endTime)
                        .orderByAsc(SysRoleDO::getSortNo, SysRoleDO::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toRoleDTO).toList());
    }

    /**
     * 查询当前商户的角色详情。
     *
     * @param id 角色主键
     * @return 角色详情
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public RoleDTO getRole(Long id) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        return toRoleDTO(getRole(app.getId(), merchantId, id));
    }

    /**
     * 创建当前商户自定义角色及初始菜单、权限授权。
     *
     * @param request 角色及授权保存请求
     * @return 新建角色
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public RoleDTO createRole(RoleSaveRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        assertRoleCodeAvailable(app.getId(), merchantId, request.getRoleCode(), null);
        LocalDateTime now = LocalDateTime.now();
        SysRoleDO role = new SysRoleDO();
        role.setAppId(app.getId());
        role.setMerchantId(merchantId);
        role.setRoleCode(required(request.getRoleCode(), "roleCode"));
        role.setRoleName(required(request.getRoleName(), "roleName"));
        role.setRoleType(ROLE_TYPE_CUSTOM);
        role.setDataScope(resolveDataScope(request.getDataScope()));
        role.setDescription(normalize(request.getDescription()));
        role.setStatus(validStatus(request.getStatus()));
        role.setSortNo(request.getSortNo() == null ? 100 : request.getSortNo());
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setCreatedBy(currentAccountId());
        role.setUpdatedBy(currentAccountId());
        role.setDeleted(AuthConstants.NOT_DELETED);
        sysRoleMapper.insert(role);
        replaceRoleGrants(app.getId(), merchantId, role.getId(), request.getMenuIds(), request.getPermissionIds());
        return toRoleDTO(role);
    }

    /**
     * 更新角色和授权关系；系统角色编码不可修改。
     *
     * @param id      角色主键
     * @param request 角色及授权保存请求
     * @return 更新后的角色
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public RoleDTO updateRole(Long id, RoleSaveRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysRoleDO role = getRole(app.getId(), merchantId, id);
        if (isSystemRole(role)) {
            if (StringUtils.hasText(request.getRoleCode()) && !Objects.equals(role.getRoleCode(), request.getRoleCode().trim())) {
                throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "system role code can not be changed");
            }
        } else {
            assertRoleCodeAvailable(app.getId(), merchantId, request.getRoleCode(), id);
            role.setRoleCode(required(request.getRoleCode(), "roleCode"));
        }
        role.setRoleName(required(request.getRoleName(), "roleName"));
        role.setDataScope(resolveDataScope(request.getDataScope()));
        role.setDescription(normalize(request.getDescription()));
        role.setStatus(validStatus(request.getStatus()));
        role.setSortNo(request.getSortNo() == null ? 100 : request.getSortNo());
        role.setUpdatedAt(LocalDateTime.now());
        role.setUpdatedBy(currentAccountId());
        sysRoleMapper.updateById(role);
        replaceRoleGrants(app.getId(), merchantId, id, request.getMenuIds(), request.getPermissionIds());
        return toRoleDTO(role);
    }

    /**
     * 逻辑删除当前商户自定义角色。
     *
     * @param id 角色主键
     * @throws ServiceException 系统角色或仍分配给员工的角色不可删除
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysRoleDO role = getRole(app.getId(), merchantId, id);
        if (isSystemRole(role)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "system role can not be deleted");
        }
        Long bindCount = sysMerchantUserRoleMapper.selectCount(Wrappers.<SysMerchantUserRoleDO>lambdaQuery()
                .eq(SysMerchantUserRoleDO::getAppId, app.getId())
                .eq(SysMerchantUserRoleDO::getRoleId, id)
                .eq(SysMerchantUserRoleDO::getDeleted, AuthConstants.NOT_DELETED));
        if (bindCount != null && bindCount > 0) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "role is assigned to accounts");
        }
        role.setDeleted(role.getId());
        role.setStatus(AuthConstants.DISABLED);
        role.setUpdatedAt(LocalDateTime.now());
        sysRoleMapper.updateById(role);
    }

    /**
     * 切换当前商户角色状态。
     *
     * @param id     角色主键
     * @param status 目标启停状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void updateRoleStatus(Long id, Integer status) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysRoleDO role = getRole(app.getId(), merchantId, id);
        role.setStatus(validStatus(status));
        role.setUpdatedAt(LocalDateTime.now());
        role.setUpdatedBy(currentAccountId());
        sysRoleMapper.updateById(role);
    }

    /**
     * 构造当前商户可分配的菜单、按钮和权限授权树模板。
     *
     * @return 不含角色勾选状态的授权树
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public RoleGrantTreeDTO roleGrantTreeTemplate() {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        RoleGrantTreeDTO dto = new RoleGrantTreeDTO();
        dto.setTree(buildGrantTree(loadGrantedMenuTree(app.getId(), merchantId), grantedPermissions()));
        return dto;
    }

    /**
     * 查询角色授权树及已勾选菜单、权限。
     *
     * @param id 角色主键
     * @return 受当前商户平台授权范围限制的角色授权树
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public RoleGrantTreeDTO roleGrantTree(Long id) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysRoleDO role = getRole(app.getId(), merchantId, id);
        RoleGrantTreeDTO dto = new RoleGrantTreeDTO();
        dto.setRoleId(id);
        dto.setRole(toRoleDTO(role));
        dto.setTree(buildGrantTree(loadGrantedMenuTree(app.getId(), merchantId), grantedPermissions()));
        dto.setCheckedMenuIds(sysRoleMenuMapper.selectList(Wrappers.<SysRoleMenuDO>lambdaQuery()
                        .eq(SysRoleMenuDO::getAppId, app.getId())
                        .eq(SysRoleMenuDO::getRoleId, id)
                        .eq(SysRoleMenuDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysRoleMenuDO::getMenuId).toList());
        dto.setCheckedPermissionIds(sysRolePermissionMapper.selectList(Wrappers.<SysRolePermissionDO>lambdaQuery()
                        .eq(SysRolePermissionDO::getAppId, app.getId())
                        .eq(SysRolePermissionDO::getRoleId, id)
                        .eq(SysRolePermissionDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysRolePermissionDO::getPermissionId).toList());
        return dto;
    }

    /**
     * 全量替换角色的菜单和权限授权。
     * <p>
     * 保存前校验所选标识均在平台授予当前商户的集合内，防止商户自助提权。
     * </p>
     *
     * @param id      角色主键
     * @param request 菜单和权限选择结果
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void grantRoleTree(Long id, RoleGrantTreeSaveRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getRole(app.getId(), merchantId, id);
        replaceRoleGrants(app.getId(), merchantId, id,
                request == null ? null : request.getMenuIds(),
                request == null ? null : request.getPermissionIds());
    }

    /**
     * 查询角色菜单授权和当前商户可选菜单树。
     *
     * @param id 角色主键
     * @return 菜单授权信息
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public RoleMenuAuthDTO roleMenus(Long id) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getRole(app.getId(), merchantId, id);
        RoleMenuAuthDTO dto = new RoleMenuAuthDTO();
        dto.setRoleId(id);
        dto.setMenus(loadGrantedMenuTree(app.getId(), merchantId));
        dto.setCheckedMenuIds(sysRoleMenuMapper.selectList(Wrappers.<SysRoleMenuDO>lambdaQuery()
                        .eq(SysRoleMenuDO::getAppId, app.getId())
                        .eq(SysRoleMenuDO::getRoleId, id)
                        .eq(SysRoleMenuDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysRoleMenuDO::getMenuId).toList());
        return dto;
    }

    /**
     * 全量替换角色菜单授权，并拒绝超出商户平台菜单授权的标识。
     *
     * @param id      角色主键
     * @param request 菜单主键集合
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void grantRoleMenus(Long id, IdsRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getRole(app.getId(), merchantId, id);
        Set<Long> ids = normalizeIds(request.getIds());
        if (!loadGrantedMenuIds(app.getId(), merchantId).containsAll(ids)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "menuIds exceed merchant grant");
        }
        softDeleteRoleMenus(app.getId(), id);
        LocalDateTime now = LocalDateTime.now();
        ids.forEach(menuId -> {
            SysRoleMenuDO relation = new SysRoleMenuDO();
            relation.setAppId(app.getId());
            relation.setRoleId(id);
            relation.setMenuId(menuId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            relation.setDeleted(AuthConstants.NOT_DELETED);
            sysRoleMenuMapper.insert(relation);
        });
    }

    /**
     * 整理角色权限，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public RolePermissionAuthDTO rolePermissions(Long id) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getRole(app.getId(), merchantId, id);
        RolePermissionAuthDTO dto = new RolePermissionAuthDTO();
        dto.setRoleId(id);
        dto.setPermissions(grantedPermissions());
        dto.setCheckedPermissionIds(sysRolePermissionMapper.selectList(Wrappers.<SysRolePermissionDO>lambdaQuery()
                        .eq(SysRolePermissionDO::getAppId, app.getId())
                        .eq(SysRolePermissionDO::getRoleId, id)
                        .eq(SysRolePermissionDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysRolePermissionDO::getPermissionId).toList());
        return dto;
    }

    /**
     * 全量替换角色权限授权，并拒绝超出商户平台权限授权的标识。
     *
     * @param id      角色主键
     * @param request 权限主键集合
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void grantRolePermissions(Long id, IdsRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getRole(app.getId(), merchantId, id);
        Set<Long> ids = normalizeIds(request.getIds());
        if (!loadGrantedPermissionIds(app.getId(), merchantId).containsAll(ids)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "permissionIds exceed merchant grant");
        }
        softDeleteRolePermissions(app.getId(), id);
        LocalDateTime now = LocalDateTime.now();
        ids.forEach(permissionId -> {
            SysRolePermissionDO relation = new SysRolePermissionDO();
            relation.setAppId(app.getId());
            relation.setRoleId(id);
            relation.setPermissionId(permissionId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            relation.setDeleted(AuthConstants.NOT_DELETED);
            sysRolePermissionMapper.insert(relation);
        });
    }

    /**
     * 查询平台已授予当前商户且仍启用的权限。
     *
     * @return 可继续分配给商户角色的权限列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<PermissionDTO> grantedPermissions() {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        Set<Long> ids = loadGrantedPermissionIds(app.getId(), merchantId);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return sysPermissionMapper.selectList(Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, app.getId())
                        .in(SysPermissionDO::getId, ids)
                        .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysPermissionDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysPermissionDO::getMenuId, SysPermissionDO::getId))
                .stream().map(this::toPermissionDTO).toList();
    }

    /**
     * 查询当前商户指定角色关联的商户成员主键。
     * <p>
     * 先验证角色属于当前商户，避免使用其他商户 roleId 侧信道筛选账号。
     * </p>
     *
     * @param appId  商户后台应用主键
     * @param roleId 角色主键
     * @return 关联商户成员主键集合；角色无效或未关联时返回空集
     */
    private Set<Long> merchantUserIdsByRole(Long appId, Long roleId) {
        if (roleId == null || roleId <= 0) {
            return Collections.emptySet();
        }
        String merchantId = currentMerchantId();
        Long roleCount = sysRoleMapper.selectCount(Wrappers.<SysRoleDO>lambdaQuery()
                .eq(SysRoleDO::getAppId, appId)
                .eq(SysRoleDO::getMerchantId, merchantId)
                .eq(SysRoleDO::getId, roleId)
                .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED));
        if (roleCount == null || roleCount == 0) {
            return Collections.emptySet();
        }
        return sysMerchantUserRoleMapper.selectList(Wrappers.<SysMerchantUserRoleDO>lambdaQuery()
                        .eq(SysMerchantUserRoleDO::getAppId, appId)
                        .eq(SysMerchantUserRoleDO::getRoleId, roleId)
                        .eq(SysMerchantUserRoleDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysMerchantUserRoleDO::getMerchantUserId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 整理账号ID按keyword，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param keyword 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Set<Long> accountIdsByKeyword(Long appId, String merchantId, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptySet();
        }
        return sysAccountMapper.selectList(Wrappers.<SysAccountDO>lambdaQuery()
                        .eq(SysAccountDO::getAppId, appId)
                        .eq(SysAccountDO::getMerchantId, merchantId)
                        .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED)
                        .and(wrapper -> wrapper
                                .like(SysAccountDO::getMobile, keyword)
                                .or()
                                .like(SysAccountDO::getEmail, keyword)))
                .stream().map(SysAccountDO::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 应用应用部门，把校验后的配置、金额、状态或字段值写入目标对象。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param dept dept 输入值，参与 部门 的查询、校验、转换、写入或日志摘要
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    private void applyDept(SysMerchantDeptDO dept, DeptSaveRequest request) {
        dept.setParentId(request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId());
        dept.setDeptCode(required(request.getDeptCode(), "deptCode"));
        dept.setDeptName(required(request.getDeptName(), "deptName"));
        dept.setLeaderAccountId(request.getLeaderAccountId());
        dept.setPhone(normalize(request.getPhone()));
        dept.setEmail(normalize(request.getEmail()));
        dept.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        dept.setStatus(validStatus(request.getStatus()));
        dept.setRemark(normalize(request.getRemark()));
    }

    /**
     * 应用应用岗位，把校验后的配置、金额、状态或字段值写入目标对象。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param post post 输入值，参与 岗位 的查询、校验、转换、写入或日志摘要
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    private void applyPost(SysMerchantPostDO post, PostSaveRequest request) {
        post.setPostCode(required(request.getPostCode(), "postCode"));
        post.setPostName(required(request.getPostName(), "postName"));
        post.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        post.setStatus(validStatus(request.getStatus()));
        post.setRemark(normalize(request.getRemark()));
    }

    /**
     * 更新账号basefields，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 商户后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    private void updateAccountBaseFields(Long appId, String merchantId, SysAccountDO account, AccountBaseSaveRequest request) {
        SysMerchantUserDO merchantUser = getMerchantUser(merchantId, account.getId());
        assertAccountAvailable(merchantId, request.getLoginAccount(), merchantUser.getId());
        SysUserDO user = sysUserMapper.selectById(account.getUserId());
        user.setRealName(required(request.getRealName(), "realName"));
        user.setMobile(normalize(request.getMobile()));
        user.setEmail(required(request.getEmail(), "email"));
        user.setStatus(validStatus(request.getStatus()));
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(currentAccountId());
        sysUserMapper.updateById(user);
        account.setLoginAccount(toAccountLoginName(merchantId, request.getLoginAccount()));
        account.setMobile(user.getMobile());
        account.setEmail(user.getEmail());
        account.setStatus(user.getStatus());
        account.setUpdatedAt(LocalDateTime.now());
        account.setUpdatedBy(currentAccountId());
        sysAccountMapper.updateById(account);
    }

    /**
     * 创建商户用户，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 商户后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param loginAccount login Account 输入值，参与 login账号 的查询、校验、转换、写入或日志摘要
     * @param user user 输入值，参与 用户 的查询、校验、转换、写入或日志摘要
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     * @return 写入、更新或删除后的处理结果
     */
    private SysMerchantUserDO createMerchantUser(String merchantId, String loginAccount, SysUserDO user, SysAccountDO account, LocalDateTime now) {
        SysMerchantUserDO merchantUser = new SysMerchantUserDO();
        merchantUser.setMerchantInfoId(currentMerchantInfoId(merchantId));
        merchantUser.setMerchantId(merchantId);
        merchantUser.setUserId(user.getId());
        merchantUser.setAccountId(account.getId());
        merchantUser.setLoginAccount(required(loginAccount, "loginAccount"));
        merchantUser.setRealName(user.getRealName());
        merchantUser.setStatus(account.getStatus());
        merchantUser.setCreatedAt(now);
        merchantUser.setUpdatedAt(now);
        merchantUser.setCreatedBy(currentAccountId());
        merchantUser.setUpdatedBy(currentAccountId());
        merchantUser.setDeleted(AuthConstants.NOT_DELETED);
        sysMerchantUserMapper.insert(merchantUser);
        return merchantUser;
    }

    /**
     * 为新增商户员工创建默认强制 MFA 配置。
     *
     * @param app     商户应用
     * @param account 登录账号
     * @param now     当前时间
     */
    private void createDefaultRequiredMfa(SysAppDO app, SysAccountDO account, LocalDateTime now) {
        SysAccountMfaDO mfa = new SysAccountMfaDO();
        mfa.setAppId(app.getId());
        mfa.setAccountId(account.getId());
        mfa.setUserId(account.getUserId());
        mfa.setMerchantId(account.getMerchantId());
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_REQUIRED);
        mfa.setMfaStatus(account.getStatus() != null && account.getStatus() == AuthConstants.DISABLED
                ? AuthConstants.MFA_STATUS_DISABLED
                : AuthConstants.MFA_STATUS_PENDING_BIND);
        mfa.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        mfa.setIssuer("Acquiring Merchant");
        mfa.setAccountLabel(mfaAccountLabel(account));
        mfa.setFailedVerifyCount(0);
        mfa.setCreatedAt(now);
        mfa.setUpdatedAt(now);
        mfa.setCreatedBy(currentAccountId());
        mfa.setUpdatedBy(currentAccountId());
        mfa.setDeleted(AuthConstants.NOT_DELETED);
        sysAccountMfaMapper.insert(mfa);
        syncAccountMfaLegacyFields(account, true);
    }

    /**
     * 同步账号表历史 MFA 字段，避免旧页面、报表或缓存仍读取 sys_account.mfa_enabled 时出现状态不一致。
     *
     * @param account 登录账号
     * @param enabled 是否启用 MFA
     */
    private void syncAccountMfaLegacyFields(SysAccountDO account, boolean enabled) {
        account.setMfaEnabled(enabled ? AuthConstants.ENABLED : AuthConstants.DISABLED);
        account.setMfaType(enabled ? AuthConstants.MFA_TYPE_TOTP : null);
        account.setTotpSecret(null);
        account.setUpdatedAt(LocalDateTime.now());
        account.setUpdatedBy(currentAccountId());
        sysAccountMapper.updateById(account);
    }

    /**
     * 账号停用时同步 MFA 状态，账号启用时保留原 MFA 策略等待管理员明确处理。
     *
     * @param account 登录账号
     */
    private void syncMfaStatusForAccountStatus(SysAccountDO account) {
        if (account.getStatus() == null || account.getStatus() != AuthConstants.DISABLED) {
            return;
        }
        SysAccountMfaDO mfa = loadMfa(account.getAppId(), account.getId());
        if (mfa == null) {
            return;
        }
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_DISABLED);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setUpdatedAt(LocalDateTime.now());
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
    }

    /**
     * 查询多因子认证，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param accountId account ID 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private SysAccountMfaDO loadMfa(Long appId, Long accountId) {
        return sysAccountMfaMapper.selectOne(Wrappers.<SysAccountMfaDO>lambdaQuery()
                .eq(SysAccountMfaDO::getAppId, appId)
                .eq(SysAccountMfaDO::getAccountId, accountId)
                .eq(SysAccountMfaDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
    }

    /**
     * 校验确保mfa输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param app app 输入值，参与 app 的查询、校验、转换、写入或日志摘要
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private SysAccountMfaDO ensureMfa(SysAppDO app, SysAccountDO account, LocalDateTime now) {
        SysAccountMfaDO mfa = loadMfa(app.getId(), account.getId());
        if (mfa != null) {
            return mfa;
        }
        SysAccountMfaDO created = new SysAccountMfaDO();
        created.setAppId(app.getId());
        created.setAccountId(account.getId());
        created.setUserId(account.getUserId());
        created.setMerchantId(account.getMerchantId());
        created.setMfaPolicy(AuthConstants.MFA_POLICY_OPTIONAL);
        created.setMfaStatus(AuthConstants.MFA_STATUS_NOT_ENABLED);
        created.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        created.setIssuer("Acquiring Merchant");
        created.setAccountLabel(mfaAccountLabel(account));
        created.setFailedVerifyCount(0);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        created.setCreatedBy(currentAccountId());
        created.setUpdatedBy(currentAccountId());
        created.setDeleted(AuthConstants.NOT_DELETED);
        sysAccountMfaMapper.insert(created);
        return created;
    }

    /**
     * 整理失效openmfatokens，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param accountId account ID 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     */
    private void expireOpenMfaTokens(Long appId, Long accountId, LocalDateTime now) {
        sysAccountMfaTokenMapper.update(
                Wrappers.<SysAccountMfaTokenDO>lambdaUpdate()
                        .set(SysAccountMfaTokenDO::getUsed, AuthConstants.ENABLED)
                        .set(SysAccountMfaTokenDO::getUsedAt, now)
                        .set(SysAccountMfaTokenDO::getUpdatedAt, now)
                        .eq(SysAccountMfaTokenDO::getAppId, appId)
                        .eq(SysAccountMfaTokenDO::getAccountId, accountId)
                        .eq(SysAccountMfaTokenDO::getTokenType, MFA_TOKEN_TYPE_LOGIN)
                        .eq(SysAccountMfaTokenDO::getUsed, AuthConstants.DISABLED)
                        .eq(SysAccountMfaTokenDO::getDeleted, AuthConstants.NOT_DELETED)
        );
    }

    /**
     * 记录会话，写入安全、审计或链路排障所需的脱敏上下文。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param accountId account ID 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     */
    private void logoutSessions(Long appId, Long accountId, LocalDateTime now) {
        sysLoginSessionMapper.update(
                Wrappers.<SysLoginSessionDO>lambdaUpdate()
                        .set(SysLoginSessionDO::getLogout, AuthConstants.ENABLED)
                        .set(SysLoginSessionDO::getLogoutAt, now)
                        .set(SysLoginSessionDO::getUpdatedAt, now)
                        .eq(SysLoginSessionDO::getAppId, appId)
                        .eq(SysLoginSessionDO::getAccountId, accountId)
                        .eq(SysLoginSessionDO::getLogout, AuthConstants.DISABLED)
        );
    }

/**
 * 记录mfa日志，写入安全、审计或链路排障所需的脱敏上下文。
 * <p>
 * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
 * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param app app 输入值，参与 app 的查询、校验、转换、写入或日志摘要
 * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
 * @param mfa mfa 输入值，参与 多因子认证 的查询、校验、转换、写入或日志摘要
 * @param actionType action Type 输入值，参与 actiontype 的查询、校验、转换、写入或日志摘要
 * @param result 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
 * @param reason reason 输入值，参与 reason 的查询、校验、转换、写入或日志摘要
 * @param beforePolicy before Policy 输入值，参与 beforepolicy 的查询、校验、转换、写入或日志摘要
 * @param beforeStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param operator operator 输入值，参与 operator 的查询、校验、转换、写入或日志摘要
 * @param userAgent user Agent 输入值，参与 用户agent 的查询、校验、转换、写入或日志摘要
 */
    private void recordMfaLog(SysAppDO app,
                              SysAccountDO account,
                              SysAccountMfaDO mfa,
                              String actionType,
                              String result,
                              String reason,
                              String beforePolicy,
                              String beforeStatus,
                              InternalAuthAccount operator,
                              String userAgent) {
        SysAccountMfaLogDO logRow = new SysAccountMfaLogDO();
        logRow.setAppId(app.getId());
        logRow.setAccountId(account.getId());
        logRow.setUserId(account.getUserId());
        logRow.setMerchantId(account.getMerchantId());
        logRow.setActionType(actionType);
        logRow.setResult(result);
        logRow.setReason(normalize(reason));
        logRow.setBeforePolicy(beforePolicy);
        logRow.setBeforeStatus(beforeStatus);
        logRow.setAfterPolicy(mfa.getMfaPolicy());
        logRow.setAfterStatus(mfa.getMfaStatus());
        logRow.setOperatorAccountId(operator == null ? null : operator.getAccountId());
        logRow.setOperatorLoginAccount(operator == null ? null : operator.getLoginAccount());
        logRow.setClientIp("-");
        logRow.setUserAgent(userAgent);
        logRow.setEventTime(LocalDateTime.now());
        logRow.setCreatedAt(LocalDateTime.now());
        sysAccountMfaLogMapper.insert(logRow);
    }

    /**
     * 发送mfanotice消息或请求，补齐目标地址、链路标识和业务载荷。
     * <p>
     * 前置条件：调用方已确定 商户后台服务 的目标地址、消息主题、业务编号和重试策略。
     * 该方法可能调用外部系统、内部服务或 MQ；traceId 必须沿调用链透传，重试应保留原业务标识。
     * 异常边界：网络异常、超时或投递失败需转换为当前模块可识别的失败结果并记录脱敏摘要。
     * </p>
     * @param app app 输入值，参与 app 的查询、校验、转换、写入或日志摘要
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @param templateCode template Code 输入值，参与 template编码 的查询、校验、转换、写入或日志摘要
     * @param reason reason 输入值，参与 reason 的查询、校验、转换、写入或日志摘要
     * @param exemptUntil exempt Until 输入值，参与 exemptuntil 的查询、校验、转换、写入或日志摘要
     */
    private void sendMfaNotice(SysAppDO app, SysAccountDO account, String templateCode, String reason, LocalDateTime exemptUntil) {
        if (!StringUtils.hasText(account.getEmail())) {
            return;
        }
        try {
            merchantTemplateEmailService.sendByTemplate(new MerchantEmailSendCommand(
                    app.getAppCode(),
                    account.getMerchantId(),
                    account.getMerchantId(),
                    merchantName(account.getMerchantId()),
                    templateCode,
                    MERCHANT_MFA_SCENE,
                    "zh-CN",
                    List.of(account.getEmail()),
                    mfaEmailVariables(account, reason, exemptUntil),
                    MERCHANT_MFA_SCENE,
                    String.valueOf(account.getId())
            ));
        } catch (RuntimeException exception) {
            log.warn("merchant mfa notice send failed, accountId: {}, templateCode: {}, exceptionType: {}",
                    account.getId(), templateCode, exception.getClass().getSimpleName());
            SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
            recordMfaLog(app, account, mfa, "SEND_NOTICE", MFA_RESULT_FAILED, exception.getMessage(),
                    mfa.getMfaPolicy(), mfa.getMfaStatus(), currentOperator(), null);
        }
    }

    /**
     * 事务提交后发送商户系统账号开户通知，避免账号创建失败时误发邮件。
     *
     * @param app             商户系统应用
     * @param account         登录账号
     * @param initialPassword 初始密码，仅作为敏感模板变量传入邮件服务
     */
    private void sendAccountCreatedNoticeAfterCommit(SysAppDO app, SysAccountDO account, String initialPassword) {
        Runnable task = () -> sendAccountCreatedNotice(app, account, initialPassword);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    /**
     * 发送商户系统账号开户通知。发送失败仅记录告警，不影响员工创建结果。
     *
     * @param app             商户系统应用
     * @param account         登录账号
     * @param initialPassword 初始密码，仅作为敏感模板变量传入邮件服务
     */
    private void sendAccountCreatedNotice(SysAppDO app, SysAccountDO account, String initialPassword) {
        if (!StringUtils.hasText(account.getEmail())) {
            return;
        }
        try {
            merchantTemplateEmailService.sendByTemplate(new MerchantEmailSendCommand(
                    app.getAppCode(),
                    account.getMerchantId(),
                    account.getMerchantId(),
                    merchantName(account.getMerchantId()),
                    TEMPLATE_ACCOUNT_CREATED_NOTICE,
                    ACCOUNT_CREATED_SCENE,
                    "zh-CN",
                    List.of(account.getEmail()),
                    accountCreatedEmailVariables(account, initialPassword),
                    ACCOUNT_CREATED_SCENE,
                    String.valueOf(account.getId())
            ));
        } catch (RuntimeException exception) {
            log.warn("merchant account created notice send failed, accountId: {}, exceptionType: {}",
                    account.getId(), exception.getClass().getSimpleName());
        }
    }

    /**
     * 构建商户系统开户通知模板变量。
     *
     * @param account         登录账号
     * @param initialPassword 初始密码，仅作为敏感模板变量传入邮件服务
     * @return 邮件模板变量
     */
    private Map<String, Object> accountCreatedEmailVariables(SysAccountDO account, String initialPassword) {
        Map<String, Object> variables = new LinkedHashMap<>();
        String merchantSystemBaseUrl = merchantSystemBaseUrl();
        variables.put("systemName", "商户系统");
        variables.put("userName", displayLoginAccount(account));
        variables.put("merchantId", account.getMerchantId());
        variables.put("merchantName", merchantName(account.getMerchantId()));
        variables.put("loginAccount", displayLoginAccount(account));
        variables.put("initialPassword", initialPassword);
        variables.put("loginUrl", merchantLoginUrl(merchantSystemBaseUrl));
        variables.put("merchantSystemBaseUrl", merchantSystemBaseUrl);
        variables.put("mfaGuide", "首次登录时请按页面提示完成多因素认证（MFA）绑定。绑定二维码和手动密钥只会在登录页身份校验通过后展示，邮件不会包含 MFA 密钥。");
        variables.put("verifyCodeGuide", "登录页会自动加载图形验证码，请输入图片中的验证码后继续登录。");
        return variables;
    }

    /** 事务提交后发送商户管理员重置员工密码通知。 */
    private void sendPasswordChangedNoticeAfterCommit(SysAppDO app,
                                                      SysAccountDO account,
                                                      String temporaryPassword,
                                                      LocalDateTime operationTime) {
        Runnable task = () -> sendPasswordChangedNotice(app, account, temporaryPassword, operationTime);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    /** 发送商户员工密码被管理员修改通知，发送失败不影响密码更新。 */
    private void sendPasswordChangedNotice(SysAppDO app,
                                           SysAccountDO account,
                                           String temporaryPassword,
                                           LocalDateTime operationTime) {
        if (!StringUtils.hasText(account.getEmail())) {
            return;
        }
        try {
            String baseUrl = merchantSystemBaseUrl();
            merchantTemplateEmailService.sendByTemplate(new MerchantEmailSendCommand(
                    app.getAppCode(),
                    account.getMerchantId(),
                    account.getMerchantId(),
                    merchantName(account.getMerchantId()),
                    TEMPLATE_PASSWORD_CHANGED_NOTICE,
                    PASSWORD_CHANGED_SCENE,
                    "zh-CN",
                    List.of(account.getEmail()),
                    passwordChangedEmailVariables(account, temporaryPassword, operationTime, baseUrl),
                    PASSWORD_CHANGED_SCENE,
                    String.valueOf(account.getId())
            ));
        } catch (RuntimeException exception) {
            log.warn("merchant password changed notice send failed, accountId: {}, exceptionType: {}",
                    account.getId(), exception.getClass().getSimpleName());
        }
    }

    /** 构建商户员工密码被管理员修改通知变量。 */
    private Map<String, Object> passwordChangedEmailVariables(SysAccountDO account,
                                                              String temporaryPassword,
                                                              LocalDateTime operationTime,
                                                              String baseUrl) {
        Map<String, Object> variables = new LinkedHashMap<>();
        InternalAuthAccount operator = currentOperator();
        variables.put("systemName", "Vexra Merchant");
        variables.put("userName", displayLoginAccount(account));
        variables.put("merchantId", account.getMerchantId());
        variables.put("merchantName", merchantName(account.getMerchantId()));
        variables.put("loginAccount", displayLoginAccount(account));
        variables.put("temporaryPassword", temporaryPassword);
        variables.put("operatorName", operator != null && StringUtils.hasText(operator.getRealName())
                ? operator.getRealName()
                : operator != null && StringUtils.hasText(operator.getLoginAccount())
                ? operator.getLoginAccount()
                : "Merchant Administrator");
        variables.put("operationTime", EMAIL_TIME_FORMATTER.format(operationTime));
        variables.put("loginUrl", merchantLoginUrl(baseUrl));
        return variables;
    }

    /**
     * 整理MFA邮件变量，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @param reason reason 输入值，参与 reason 的查询、校验、转换、写入或日志摘要
     * @param exemptUntil exempt Until 输入值，参与 exemptuntil 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, Object> mfaEmailVariables(SysAccountDO account, String reason, LocalDateTime exemptUntil) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("loginAccount", displayLoginAccount(account));
        variables.put("merchantId", account.getMerchantId());
        variables.put("merchantName", merchantName(account.getMerchantId()));
        variables.put("email", account.getEmail());
        variables.put("reason", StringUtils.hasText(reason) ? reason : "-");
        variables.put("exemptUntil", exemptUntil == null ? "长期有效" : exemptUntil.toString().replace("T", " "));
        String merchantSystemBaseUrl = merchantSystemBaseUrl();
        variables.put("merchantSystemBaseUrl", merchantSystemBaseUrl);
        variables.put("bindUrl", merchantLoginUrl(merchantSystemBaseUrl));
        return variables;
    }

    /**
     * 整理商户system基础url，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String merchantSystemBaseUrl() {
        return merchantConfigService.enabledConfigValue(MERCHANT_FRONTEND_BASE_URL_KEY)
                .map(this::trimTrailingSlash)
                .orElse("");
    }

    /**
     * 整理商户登录url，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param merchantSystemBaseUrl merchant System Base URL 输入值，参与 商户system基础url 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String merchantLoginUrl(String merchantSystemBaseUrl) {
        if (!StringUtils.hasText(merchantSystemBaseUrl)) {
            return "/login";
        }
        return trimTrailingSlash(merchantSystemBaseUrl) + "/login";
    }

    /**
     * 规范化trimtrailingslash，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 整理商户name，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String merchantName(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return "-";
        }
        BaseMerchantInfoDO merchant = baseMerchantInfoMapper.selectOne(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                .eq(BaseMerchantInfoDO::getDeleted, 0)
                .last("LIMIT 1"));
        if (merchant == null || !StringUtils.hasText(merchant.getMerchantName())) {
            return merchantId;
        }
        return merchant.getMerchantName();
    }

    /**
     * 构造mfa状态响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @param mfa mfa 输入值，参与 多因子认证 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private AccountMfaStatusResponse toMfaStatusResponse(SysAccountDO account, SysAccountMfaDO mfa) {
        AccountMfaStatusResponse response = new AccountMfaStatusResponse();
        response.setAccountId(account.getId());
        response.setLoginAccount(displayLoginAccount(account));
        response.setMfaPolicy(mfa.getMfaPolicy());
        response.setMfaStatus(mfa.getMfaStatus());
        response.setBindTime(mfa.getBindTime());
        response.setLastVerifyTime(mfa.getLastVerifyTime());
        response.setLockedUntil(mfa.getLockedUntil());
        response.setExemptUntil(mfa.getExemptUntil());
        return response;
    }

    /**
     * 构造mfa状态对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @param dto DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     */
    private void fillMfaStatus(SysAccountDO account, AccountDTO dto) {
        SysAccountMfaDO mfa = loadMfa(account.getAppId(), account.getId());
        dto.setMfaPolicy(mfa == null ? AuthConstants.MFA_POLICY_OPTIONAL : mfa.getMfaPolicy());
        dto.setMfaStatus(mfa == null ? AuthConstants.MFA_STATUS_NOT_ENABLED : mfa.getMfaStatus());
        dto.setMfaBindTime(mfa == null ? null : mfa.getBindTime());
        dto.setMfaLastVerifyTime(mfa == null ? null : mfa.getLastVerifyTime());
        dto.setMfaExemptUntil(mfa == null ? null : mfa.getExemptUntil());
        dto.setMfaLockedUntil(mfa == null ? null : mfa.getLockedUntil());
    }

    /**
     * 校验断言notself输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param targetAccountId target Account ID 输入值，参与 target账号ID 的查询、校验、转换、写入或日志摘要
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    private void assertNotSelf(Long targetAccountId, String message) {
        InternalAuthAccount operator = currentOperator();
        if (operator != null && Objects.equals(operator.getAccountId(), targetAccountId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
        }
    }

    /**
     * 整理当前操作人，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private InternalAuthAccount currentOperator() {
        return InternalAuthContextHolder.get();
    }

    /**
     * 整理MFA账号label，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String mfaAccountLabel(SysAccountDO account) {
        return StringUtils.hasText(account.getMerchantId())
                ? account.getMerchantId() + ":" + account.getLoginAccount()
                : account.getLoginAccount();
    }

    /**
     * 整理展示登录账号，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String displayLoginAccount(SysAccountDO account) {
        if (account == null || !StringUtils.hasText(account.getLoginAccount())) {
            return "-";
        }
        String suffix = ACCOUNT_LOGIN_SEPARATOR + account.getMerchantId();
        if (StringUtils.hasText(account.getMerchantId()) && account.getLoginAccount().endsWith(suffix)) {
            return account.getLoginAccount().substring(0, account.getLoginAccount().length() - suffix.length());
        }
        return account.getLoginAccount();
    }

    /**
     * 整理当前商户信息ID，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Long currentMerchantInfoId(String merchantId) {
        BaseMerchantInfoDO merchant = baseMerchantInfoMapper.selectOne(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                .eq(BaseMerchantInfoDO::getDeleted, 0)
                .last("LIMIT 1"));
        if (merchant == null) {
            throw new ServiceException(ApiResultEnum.MERCHANT_INVALID);
        }
        return merchant.getId();
    }

    /**
     * 更新账号角色，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param merchantUser merchant User 输入值，参与 商户用户 的查询、校验、转换、写入或日志摘要
     * @param roleIds role Ids 输入值，参与 角色ID 的查询、校验、转换、写入或日志摘要
     */
    private void replaceAccountRoles(Long appId, String merchantId, SysMerchantUserDO merchantUser, List<Long> roleIds) {
        Set<Long> ids = normalizeIds(roleIds);
        if (!ids.isEmpty()) {
            Set<Long> available = sysRoleMapper.selectList(Wrappers.<SysRoleDO>lambdaQuery()
                            .eq(SysRoleDO::getAppId, appId)
                            .eq(SysRoleDO::getMerchantId, merchantId)
                            .in(SysRoleDO::getId, ids)
                            .eq(SysRoleDO::getStatus, AuthConstants.ENABLED)
                            .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED))
                    .stream().map(SysRoleDO::getId).collect(Collectors.toSet());
            if (!available.containsAll(ids)) {
                throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "roleIds exceed merchant roles");
            }
        }
        sysMerchantUserRoleMapper.selectList(Wrappers.<SysMerchantUserRoleDO>lambdaQuery()
                .eq(SysMerchantUserRoleDO::getAppId, appId)
                .eq(SysMerchantUserRoleDO::getMerchantUserId, merchantUser.getId())
                .eq(SysMerchantUserRoleDO::getDeleted, AuthConstants.NOT_DELETED)).forEach(relation -> {
            relation.setDeleted(relation.getId());
            sysMerchantUserRoleMapper.updateById(relation);
        });
        LocalDateTime now = LocalDateTime.now();
        ids.forEach(roleId -> {
            SysMerchantUserRoleDO relation = new SysMerchantUserRoleDO();
            relation.setAppId(appId);
            relation.setMerchantInfoId(merchantUser.getMerchantInfoId());
            relation.setMerchantUserId(merchantUser.getId());
            relation.setRoleId(roleId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            relation.setDeleted(AuthConstants.NOT_DELETED);
            sysMerchantUserRoleMapper.insert(relation);
        });
    }

    /**
     * 更新账号部门，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param accountId account ID 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @param deptIds dept Ids 输入值，参与 部门ID 的查询、校验、转换、写入或日志摘要
     */
    private void replaceAccountDepts(String merchantId, Long accountId, List<Long> deptIds) {
        Set<Long> ids = normalizeIds(deptIds);
        if (!ids.isEmpty() && !listDepts().stream().map(DeptDTO::getDeptId).collect(Collectors.toSet()).containsAll(ids)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "deptIds exceed merchant depts");
        }
        sysMerchantAccountDeptMapper.delete(Wrappers.<SysMerchantAccountDeptDO>lambdaQuery()
                .eq(SysMerchantAccountDeptDO::getMerchantId, merchantId)
                .eq(SysMerchantAccountDeptDO::getAccountId, accountId));
        LocalDateTime now = LocalDateTime.now();
        ids.forEach(deptId -> {
            SysMerchantAccountDeptDO relation = new SysMerchantAccountDeptDO();
            relation.setMerchantId(merchantId);
            relation.setAccountId(accountId);
            relation.setDeptId(deptId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            sysMerchantAccountDeptMapper.insert(relation);
        });
    }

    /**
     * 更新账号岗位，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param accountId account ID 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @param postIds post Ids 输入值，参与 岗位ID 的查询、校验、转换、写入或日志摘要
     */
    private void replaceAccountPosts(String merchantId, Long accountId, List<Long> postIds) {
        Set<Long> ids = normalizeIds(postIds);
        if (!ids.isEmpty() && !listPosts().stream().map(PostDTO::getPostId).collect(Collectors.toSet()).containsAll(ids)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "postIds exceed merchant posts");
        }
        sysMerchantAccountPostMapper.delete(Wrappers.<SysMerchantAccountPostDO>lambdaQuery()
                .eq(SysMerchantAccountPostDO::getMerchantId, merchantId)
                .eq(SysMerchantAccountPostDO::getAccountId, accountId));
        LocalDateTime now = LocalDateTime.now();
        ids.forEach(postId -> {
            SysMerchantAccountPostDO relation = new SysMerchantAccountPostDO();
            relation.setMerchantId(merchantId);
            relation.setAccountId(accountId);
            relation.setPostId(postId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            sysMerchantAccountPostMapper.insert(relation);
        });
    }

    /**
     * 更新角色授权，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param roleId role ID 输入值，参与 角色ID 的查询、校验、转换、写入或日志摘要
     * @param menuIds menu Ids 输入值，参与 菜单ID 的查询、校验、转换、写入或日志摘要
     * @param permissionIds permission Ids 输入值，参与 权限ID 的查询、校验、转换、写入或日志摘要
     */
    private void replaceRoleGrants(Long appId, String merchantId, Long roleId, List<Long> menuIds, List<Long> permissionIds) {
        if (menuIds == null && permissionIds == null) {
            return;
        }
        Set<Long> safeMenuIds = normalizeIds(menuIds);
        Set<Long> safePermissionIds = normalizeIds(permissionIds);
        if (!currentAccountCanGrantRole()) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "role grant permission required");
        }
        if (!loadGrantedMenuIds(appId, merchantId).containsAll(safeMenuIds)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "menuIds exceed merchant grant");
        }
        if (!loadGrantedPermissionIds(appId, merchantId).containsAll(safePermissionIds)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "permissionIds exceed merchant grant");
        }
        softDeleteRoleMenus(appId, roleId);
        softDeleteRolePermissions(appId, roleId);
        LocalDateTime now = LocalDateTime.now();
        safeMenuIds.forEach(menuId -> {
            SysRoleMenuDO relation = new SysRoleMenuDO();
            relation.setAppId(appId);
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            relation.setDeleted(AuthConstants.NOT_DELETED);
            sysRoleMenuMapper.insert(relation);
        });
        safePermissionIds.forEach(permissionId -> {
            SysRolePermissionDO relation = new SysRolePermissionDO();
            relation.setAppId(appId);
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            relation.setDeleted(AuthConstants.NOT_DELETED);
            sysRolePermissionMapper.insert(relation);
        });
    }

    /**
     * 查询已授权菜单 ID，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private Set<Long> loadGrantedMenuIds(Long appId, String merchantId) {
        return sysMerchantMenuGrantMapper.selectList(Wrappers.<SysMerchantMenuGrantDO>lambdaQuery()
                        .eq(SysMerchantMenuGrantDO::getAppId, appId)
                        .eq(SysMerchantMenuGrantDO::getMerchantId, merchantId)
                        .eq(SysMerchantMenuGrantDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysMerchantMenuGrantDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysMerchantMenuGrantDO::getMenuId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 查询已授权权限 ID，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private Set<Long> loadGrantedPermissionIds(Long appId, String merchantId) {
        return sysMerchantPermissionGrantMapper.selectList(Wrappers.<SysMerchantPermissionGrantDO>lambdaQuery()
                        .eq(SysMerchantPermissionGrantDO::getAppId, appId)
                        .eq(SysMerchantPermissionGrantDO::getMerchantId, merchantId)
                        .eq(SysMerchantPermissionGrantDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysMerchantPermissionGrantDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysMerchantPermissionGrantDO::getPermissionId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 查询已授权菜单树，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<AuthMenuDTO> loadGrantedMenuTree(Long appId, String merchantId) {
        Set<Long> ids = loadGrantedMenuIds(appId, merchantId);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<AuthMenuDTO> nodes = sysMenuMapper.selectList(Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getAppId, appId)
                        .in(SysMenuDO::getId, ids)
                        .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysMenuDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysMenuDO::getSortNo, SysMenuDO::getId))
                .stream().map(this::toAuthMenuDTO).toList();
        return buildMenuTree(nodes);
    }

    /**
     * 更新softdelete角色菜单，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param roleId role ID 输入值，参与 角色ID 的查询、校验、转换、写入或日志摘要
     */
    private void softDeleteRoleMenus(Long appId, Long roleId) {
        sysRoleMenuMapper.selectList(Wrappers.<SysRoleMenuDO>lambdaQuery()
                .eq(SysRoleMenuDO::getAppId, appId)
                .eq(SysRoleMenuDO::getRoleId, roleId)
                .eq(SysRoleMenuDO::getDeleted, AuthConstants.NOT_DELETED)).forEach(relation -> {
            relation.setDeleted(relation.getId());
            sysRoleMenuMapper.updateById(relation);
        });
    }

    /**
     * 更新softdelete角色权限，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param roleId role ID 输入值，参与 角色ID 的查询、校验、转换、写入或日志摘要
     */
    private void softDeleteRolePermissions(Long appId, Long roleId) {
        sysRolePermissionMapper.selectList(Wrappers.<SysRolePermissionDO>lambdaQuery()
                .eq(SysRolePermissionDO::getAppId, appId)
                .eq(SysRolePermissionDO::getRoleId, roleId)
                .eq(SysRolePermissionDO::getDeleted, AuthConstants.NOT_DELETED)).forEach(relation -> {
            relation.setDeleted(relation.getId());
            sysRolePermissionMapper.updateById(relation);
        });
    }

    /**
     * 查询部门，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private SysMerchantDeptDO getDept(String merchantId, Long id) {
        SysMerchantDeptDO dept = sysMerchantDeptMapper.selectOne(Wrappers.<SysMerchantDeptDO>lambdaQuery()
                .eq(SysMerchantDeptDO::getMerchantId, merchantId)
                .eq(SysMerchantDeptDO::getId, id)
                .eq(SysMerchantDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (dept == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "dept not found");
        }
        return dept;
    }

    /**
     * 查询岗位，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private SysMerchantPostDO getPost(String merchantId, Long id) {
        SysMerchantPostDO post = sysMerchantPostMapper.selectOne(Wrappers.<SysMerchantPostDO>lambdaQuery()
                .eq(SysMerchantPostDO::getMerchantId, merchantId)
                .eq(SysMerchantPostDO::getId, id)
                .eq(SysMerchantPostDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (post == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "post not found");
        }
        return post;
    }

    /**
     * 查询账号，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private SysAccountDO getAccount(Long appId, String merchantId, Long id) {
        SysAccountDO account = sysAccountMapper.selectOne(Wrappers.<SysAccountDO>lambdaQuery()
                .eq(SysAccountDO::getAppId, appId)
                .eq(SysAccountDO::getMerchantId, merchantId)
                .eq(SysAccountDO::getId, id)
                .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (account == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "account not found");
        }
        return account;
    }

    /**
     * 查询商户用户，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param accountId account ID 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private SysMerchantUserDO getMerchantUser(String merchantId, Long accountId) {
        SysMerchantUserDO merchantUser = sysMerchantUserMapper.selectOne(Wrappers.<SysMerchantUserDO>lambdaQuery()
                .eq(SysMerchantUserDO::getMerchantId, merchantId)
                .eq(SysMerchantUserDO::getAccountId, accountId)
                .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (merchantUser == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "merchant user not found");
        }
        return merchantUser;
    }

    /**
     * 查询角色，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 商户后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private SysRoleDO getRole(Long appId, String merchantId, Long id) {
        SysRoleDO role = sysRoleMapper.selectOne(Wrappers.<SysRoleDO>lambdaQuery()
                .eq(SysRoleDO::getAppId, appId)
                .eq(SysRoleDO::getMerchantId, merchantId)
                .eq(SysRoleDO::getId, id)
                .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (role == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "role not found");
        }
        return role;
    }

    /**
     * 校验部门parent输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 商户后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param parentId parent ID 输入值，参与 parentID 的查询、校验、转换、写入或日志摘要
     * @param currentId current ID 输入值，参与 currentID 的查询、校验、转换、写入或日志摘要
     */
    private void validateDeptParent(String merchantId, Long parentId, Long currentId) {
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return;
        }
        if (Objects.equals(parentId, currentId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "parentId is invalid");
        }
        getDept(merchantId, parentId);
    }

    /**
     * 校验断言部门编码available输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param currentId current ID 输入值，参与 currentID 的查询、校验、转换、写入或日志摘要
     */
    private void assertDeptCodeAvailable(String merchantId, String code, Long currentId) {
        Long count = sysMerchantDeptMapper.selectCount(Wrappers.<SysMerchantDeptDO>lambdaQuery()
                .eq(SysMerchantDeptDO::getMerchantId, merchantId)
                .eq(SysMerchantDeptDO::getDeptCode, required(code, "deptCode"))
                .ne(currentId != null, SysMerchantDeptDO::getId, currentId)
                .eq(SysMerchantDeptDO::getDeleted, AuthConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "deptCode already exists");
        }
    }

    /**
     * 校验断言岗位编码available输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param currentId current ID 输入值，参与 currentID 的查询、校验、转换、写入或日志摘要
     */
    private void assertPostCodeAvailable(String merchantId, String code, Long currentId) {
        Long count = sysMerchantPostMapper.selectCount(Wrappers.<SysMerchantPostDO>lambdaQuery()
                .eq(SysMerchantPostDO::getMerchantId, merchantId)
                .eq(SysMerchantPostDO::getPostCode, required(code, "postCode"))
                .ne(currentId != null, SysMerchantPostDO::getId, currentId)
                .eq(SysMerchantPostDO::getDeleted, AuthConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "postCode already exists");
        }
    }

    /**
     * 校验断言账号available输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param loginAccount login Account 输入值，参与 login账号 的查询、校验、转换、写入或日志摘要
     * @param currentMerchantUserId current Merchant User ID 输入值，参与 current商户用户ID 的查询、校验、转换、写入或日志摘要
     */
    private void assertAccountAvailable(String merchantId, String loginAccount, Long currentMerchantUserId) {
        Long count = sysMerchantUserMapper.selectCount(Wrappers.<SysMerchantUserDO>lambdaQuery()
                .eq(SysMerchantUserDO::getMerchantId, merchantId)
                .eq(SysMerchantUserDO::getLoginAccount, required(loginAccount, "loginAccount"))
                .ne(currentMerchantUserId != null, SysMerchantUserDO::getId, currentMerchantUserId)
                .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "loginAccount already exists");
        }
    }

    /**
     * 校验断言角色编码available输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param roleCode role Code 输入值，参与 角色编码 的查询、校验、转换、写入或日志摘要
     * @param currentId current ID 输入值，参与 currentID 的查询、校验、转换、写入或日志摘要
     */
    private void assertRoleCodeAvailable(Long appId, String merchantId, String roleCode, Long currentId) {
        Long count = sysRoleMapper.selectCount(Wrappers.<SysRoleDO>lambdaQuery()
                .eq(SysRoleDO::getAppId, appId)
                .eq(SysRoleDO::getMerchantId, merchantId)
                .eq(SysRoleDO::getRoleCode, required(roleCode, "roleCode"))
                .ne(currentId != null, SysRoleDO::getId, currentId)
                .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "roleCode already exists");
        }
    }

    /**
     * 构造部门dto对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param dept dept 输入值，参与 部门 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private DeptDTO toDeptDTO(SysMerchantDeptDO dept) {
        DeptDTO dto = new DeptDTO();
        dto.setDeptId(dept.getId());
        dto.setParentId(dept.getParentId());
        dto.setDeptCode(dept.getDeptCode());
        dto.setDeptName(dept.getDeptName());
        dto.setLeaderAccountId(dept.getLeaderAccountId());
        dto.setPhone(dept.getPhone());
        dto.setEmail(dept.getEmail());
        dto.setSortNo(dept.getSortNo());
        dto.setStatus(dept.getStatus());
        dto.setRemark(dept.getRemark());
        dto.setCreatedAt(dept.getCreatedAt());
        dto.setUpdatedAt(dept.getUpdatedAt());
        return dto;
    }

    /**
     * 构造岗位dto对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param post post 输入值，参与 岗位 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private PostDTO toPostDTO(SysMerchantPostDO post) {
        PostDTO dto = new PostDTO();
        dto.setPostId(post.getId());
        dto.setPostCode(post.getPostCode());
        dto.setPostName(post.getPostName());
        dto.setSortNo(post.getSortNo());
        dto.setStatus(post.getStatus());
        dto.setRemark(post.getRemark());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setUpdatedAt(post.getUpdatedAt());
        return dto;
    }

    /**
     * 构造账号dto对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private AccountDTO toAccountDTO(Long appId, SysAccountDO account) {
        return toAccountDTO(appId, getMerchantUser(account.getMerchantId(), account.getId()));
    }

    /**
     * 构造账号dto对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantUser merchant User 输入值，参与 商户用户 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private AccountDTO toAccountDTO(Long appId, SysMerchantUserDO merchantUser) {
        SysAccountDO account = sysAccountMapper.selectById(merchantUser.getAccountId());
        AccountDTO dto = new AccountDTO();
        SysUserDO user = sysUserMapper.selectById(account.getUserId());
        dto.setAccountId(account.getId());
        dto.setUserId(account.getUserId());
        dto.setMerchantUserId(merchantUser.getId());
        dto.setLoginAccount(merchantUser.getLoginAccount());
        dto.setRealName(StringUtils.hasText(merchantUser.getRealName()) ? merchantUser.getRealName() : user == null ? null : user.getRealName());
        dto.setMobile(account.getMobile());
        dto.setEmail(account.getEmail());
        dto.setStatus(merchantUser.getStatus());
        dto.setLocked(account.getLocked());
        dto.setLastLoginAt(account.getLastLoginAt());
        dto.setCreatedAt(merchantUser.getCreatedAt());
        dto.setCurrentAccount(Objects.equals(account.getId(), currentAccountId()));
        List<Long> roleIds = sysMerchantUserRoleMapper.selectList(Wrappers.<SysMerchantUserRoleDO>lambdaQuery()
                        .eq(SysMerchantUserRoleDO::getAppId, appId)
                        .eq(SysMerchantUserRoleDO::getMerchantUserId, merchantUser.getId())
                        .eq(SysMerchantUserRoleDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysMerchantUserRoleDO::getRoleId).toList();
        dto.setRoleIds(roleIds);
        if (!roleIds.isEmpty()) {
            dto.setRoleNames(sysRoleMapper.selectList(Wrappers.<SysRoleDO>lambdaQuery().in(SysRoleDO::getId, roleIds))
                    .stream().map(SysRoleDO::getRoleName).toList());
        }
        dto.setDeptIds(sysMerchantAccountDeptMapper.selectList(Wrappers.<SysMerchantAccountDeptDO>lambdaQuery()
                        .eq(SysMerchantAccountDeptDO::getMerchantId, account.getMerchantId())
                        .eq(SysMerchantAccountDeptDO::getAccountId, account.getId()))
                .stream().map(SysMerchantAccountDeptDO::getDeptId).toList());
        dto.setPostIds(sysMerchantAccountPostMapper.selectList(Wrappers.<SysMerchantAccountPostDO>lambdaQuery()
                        .eq(SysMerchantAccountPostDO::getMerchantId, account.getMerchantId())
                        .eq(SysMerchantAccountPostDO::getAccountId, account.getId()))
                .stream().map(SysMerchantAccountPostDO::getPostId).toList());
        fillMfaStatus(account, dto);
        return dto;
    }

    /**
     * 构造角色dto对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param role role 输入值，参与 角色 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private RoleDTO toRoleDTO(SysRoleDO role) {
        RoleDTO dto = new RoleDTO();
        dto.setRoleId(role.getId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setRoleType(role.getRoleType());
        dto.setDataScope(role.getDataScope());
        dto.setDescription(role.getDescription());
        dto.setStatus(role.getStatus());
        dto.setSortNo(role.getSortNo());
        dto.setCreatedAt(role.getCreatedAt());
        dto.setUpdatedAt(role.getUpdatedAt());
        return dto;
    }

    /**
     * 构造权限dto对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param permission permission 输入值，参与 权限 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private PermissionDTO toPermissionDTO(SysPermissionDO permission) {
        PermissionDTO dto = new PermissionDTO();
        dto.setPermissionId(permission.getId());
        dto.setMenuId(permission.getMenuId());
        dto.setPermissionCode(permission.getPermissionCode());
        dto.setPermissionName(permission.getPermissionName());
        dto.setPermissionType(permission.getPermissionType());
        dto.setResourceMethod(permission.getResourceMethod());
        dto.setResourcePath(permission.getResourcePath());
        return dto;
    }

    /**
     * 构造grant树对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param menus menus 输入值，参与 菜单 的查询、校验、转换、写入或日志摘要
     * @param permissions permissions 输入值，参与 权限 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private List<AuthGrantNodeDTO> buildGrantTree(List<AuthMenuDTO> menus, List<PermissionDTO> permissions) {
        Map<Long, AuthMenuDTO> menuMap = flattenAuthMenus(menus).stream()
                .collect(Collectors.toMap(AuthMenuDTO::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<Long, List<PermissionDTO>> permissionMap = new LinkedHashMap<>();
        permissions.forEach(permission -> {
            Long displayMenuId = resolvePermissionDisplayMenuId(permission, menuMap);
            if (displayMenuId == null) {
                return;
            }
            permissionMap.computeIfAbsent(displayMenuId, ignored -> new ArrayList<>()).add(permission);
        });
        return menus.stream()
                .filter(this::isGrantDisplayMenu)
                .map(menu -> toGrantNode(menu, permissionMap))
                .toList();
    }

    /**
     * 构造grantnode对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param menu menu 输入值，参与 菜单 的查询、校验、转换、写入或日志摘要
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param permissionMap permission Map 输入值，参与 权限map 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private AuthGrantNodeDTO toGrantNode(AuthMenuDTO menu, Map<Long, List<PermissionDTO>> permissionMap) {
        AuthGrantNodeDTO node = new AuthGrantNodeDTO();
        node.setId("m_" + menu.getId());
        node.setNodeId(menu.getId());
        node.setMenuId(menu.getId());
        node.setNodeType(resolveGrantNodeType(menu.getMenuType()));
        node.setName(menu.getMenuName());
        node.setCode(menu.getPermissionCode());
        List<AuthGrantNodeDTO> children = new ArrayList<>();
        if (menu.getChildren() != null && !menu.getChildren().isEmpty()) {
            children.addAll(menu.getChildren().stream()
                    .filter(this::isGrantDisplayMenu)
                    .map(child -> toGrantNode(child, permissionMap))
                    .toList());
        }
        permissionMap.getOrDefault(menu.getId(), Collections.emptyList()).forEach(permission -> {
            AuthGrantNodeDTO permissionNode = new AuthGrantNodeDTO();
            permissionNode.setId("p_" + permission.getPermissionId());
            permissionNode.setNodeId(permission.getPermissionId());
            permissionNode.setPermissionId(permission.getPermissionId());
            permissionNode.setMenuId(permission.getMenuId());
            permissionNode.setNodeType("BTN");
            permissionNode.setName(permission.getPermissionName());
            permissionNode.setCode(permission.getPermissionCode());
            children.add(permissionNode);
        });
        node.setChildren(children);
        return node;
    }

    /**
     * 解析resolve权限展示菜单ID，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param permission permission 输入值，参与 权限 的查询、校验、转换、写入或日志摘要
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param menuMap menu Map 输入值，参与 菜单map 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private Long resolvePermissionDisplayMenuId(PermissionDTO permission, Map<Long, AuthMenuDTO> menuMap) {
        if (permission.getMenuId() == null) {
            return null;
        }
        AuthMenuDTO menu = menuMap.get(permission.getMenuId());
        if (menu == null) {
            return null;
        }
        if (isGrantDisplayMenu(menu)) {
            return menu.getId();
        }
        if (!"BUTTON".equals(menu.getMenuType())) {
            return null;
        }
        Long parentId = menu.getParentId();
        while (parentId != null && parentId > ROOT_PARENT_ID) {
            AuthMenuDTO parent = menuMap.get(parentId);
            if (parent == null) {
                return null;
            }
            if (isGrantDisplayMenu(parent)) {
                return parent.getId();
            }
            if (!"BUTTON".equals(parent.getMenuType())) {
                return null;
            }
            parentId = parent.getParentId();
        }
        return null;
    }

    /**
     * 判断 is grant display menu 条件是否成立，用于控制 Merchant System Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param menu menu 输入值，参与 菜单 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean isGrantDisplayMenu(AuthMenuDTO menu) {
        return menu != null
                && !"BUTTON".equals(menu.getMenuType())
                && AuthConstants.ENABLED == (menu.getVisible() == null ? AuthConstants.ENABLED : menu.getVisible());
    }

    /**
     * 构造auth菜单对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param menus menus 输入值，参与 菜单 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private List<AuthMenuDTO> flattenAuthMenus(List<AuthMenuDTO> menus) {
        List<AuthMenuDTO> result = new ArrayList<>();
        flattenAuthMenus(menus, result);
        return result;
    }

    /**
     * 构造auth菜单对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param menus menus 输入值，参与 菜单 的查询、校验、转换、写入或日志摘要
     * @param result 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     */
    private void flattenAuthMenus(List<AuthMenuDTO> menus, List<AuthMenuDTO> result) {
        if (menus == null || menus.isEmpty()) {
            return;
        }
        menus.forEach(menu -> {
            result.add(menu);
            flattenAuthMenus(menu.getChildren(), result);
        });
    }

    /**
     * 解析resolve授权nodetype，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param menuType menu Type 输入值，参与 菜单type 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String resolveGrantNodeType(String menuType) {
        if ("CATALOG".equals(menuType)) {
            return "DIR";
        }
        if ("BUTTON".equals(menuType)) {
            return "BTN";
        }
        return "MENU";
    }

    /**
     * 构造auth菜单dto对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param menu menu 输入值，参与 菜单 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private AuthMenuDTO toAuthMenuDTO(SysMenuDO menu) {
        AuthMenuDTO dto = new AuthMenuDTO();
        dto.setId(menu.getId());
        dto.setParentId(menu.getParentId());
        dto.setMenuCode(menu.getMenuCode());
        dto.setMenuName(menu.getMenuName());
        dto.setMenuType(menu.getMenuType());
        dto.setRoutePath(menu.getRoutePath());
        dto.setComponentPath(menu.getComponentPath());
        dto.setPermissionCode(menu.getPermissionCode());
        dto.setIcon(menu.getIcon());
        dto.setVisible(menu.getVisible());
        dto.setSortNo(menu.getSortNo());
        dto.setExternalLink(menu.getExternalLink());
        return dto;
    }

    /**
     * 构造菜单树对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param nodes nodes 输入值，参与 nodes 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private List<AuthMenuDTO> buildMenuTree(List<AuthMenuDTO> nodes) {
        Map<Long, AuthMenuDTO> nodeMap = nodes.stream().collect(Collectors.toMap(AuthMenuDTO::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<AuthMenuDTO> roots = new ArrayList<>();
        for (AuthMenuDTO node : nodes) {
            if (node.getParentId() == null || node.getParentId() == ROOT_PARENT_ID || !nodeMap.containsKey(node.getParentId())) {
                roots.add(node);
            } else {
                nodeMap.get(node.getParentId()).getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * 构造部门树对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param nodes nodes 输入值，参与 nodes 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private List<DeptDTO> buildDeptTree(List<DeptDTO> nodes) {
        Map<Long, DeptDTO> nodeMap = nodes.stream().collect(Collectors.toMap(DeptDTO::getDeptId, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<DeptDTO> roots = new ArrayList<>();
        for (DeptDTO node : nodes) {
            if (node.getParentId() == null || node.getParentId() == ROOT_PARENT_ID || !nodeMap.containsKey(node.getParentId())) {
                roots.add(node);
            } else {
                nodeMap.get(node.getParentId()).getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * 解析normalizeID，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param ids 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 构造、转换或解析后的业务值
     */
    private Set<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        return ids.stream().filter(Objects::nonNull).filter(id -> id > 0).collect(Collectors.toSet());
    }

    /**
     * 整理有效状态，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Integer validStatus(Integer status) {
        if (status == null) {
            return AuthConstants.ENABLED;
        }
        if (status == AuthConstants.ENABLED || status == AuthConstants.DISABLED) {
            return status;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "status is invalid");
    }

    /**
     * 解析resolvedatascope，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param dataScope data Scope 输入值，参与 datascope 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String resolveDataScope(String dataScope) {
        if (!StringUtils.hasText(dataScope)) {
            return DATA_SCOPE_SELF;
        }
        String normalized = dataScope.trim().toUpperCase();
        if (DATA_SCOPE_ALL.equals(normalized) || DATA_SCOPE_SELF.equals(normalized) || DATA_SCOPE_CUSTOM.equals(normalized)) {
            return normalized;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "dataScope is invalid");
    }

    /**
     * 判断 is system role 条件是否成立，用于控制 Merchant System Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param role role 输入值，参与 角色 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean isSystemRole(SysRoleDO role) {
        return role != null && ROLE_TYPE_SYSTEM.equals(role.getRoleType());
    }

    /**
     * 校验required输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 商户后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param field field 输入值，参与 field 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), field + " is required");
        }
        return value.trim();
    }

    /**
     * 解析normalize，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 解析parse查询date时间，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param endOfDay end Of Day 输入值，参与 endofday 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private LocalDateTime parseQueryDateTime(String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        try {
            if (normalized.length() == 10) {
                return LocalDate.parse(normalized).atTime(endOfDay ? LocalTime.MAX : LocalTime.MIN);
            }
            return LocalDateTime.parse(normalized.replace(' ', 'T'));
        } catch (DateTimeParseException ex) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "created time is invalid");
        }
    }

    /**
     * 构造账号loginname对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param loginAccount login Account 输入值，参与 login账号 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String toAccountLoginName(String merchantId, String loginAccount) {
        return required(loginAccount, "loginAccount") + ACCOUNT_LOGIN_SEPARATOR + merchantId;
    }

    /**
     * 整理商户app，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private SysAppDO merchantApp() {
        SysAppDO app = sysAppMapper.selectOne(Wrappers.<SysAppDO>lambdaQuery()
                .eq(SysAppDO::getAppCode, AuthConstants.APP_MERCHANT)
                .eq(SysAppDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (app == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "MERCHANT app not found");
        }
        return app;
    }

    /**
     * 整理当前商户号，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String currentMerchantId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return account.getMerchantId();
    }

    /**
     * 整理当前账号 ID，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Long currentAccountId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        return account == null ? null : account.getAccountId();
    }

    /**
     * 整理当前账号可授权角色，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private boolean currentAccountCanGrantRole() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getPermissions() == null) {
            return false;
        }
        return account.getPermissions().contains("*:*:*")
                || account.getPermissions().contains("merchant:system:role:grant")
                || account.getPermissions().contains("merchant:system:role:grantMenu")
                || account.getPermissions().contains("merchant:system:role:grantPermission");
    }
}
