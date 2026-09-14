package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.merchant.AdminMerchantInfoDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantFormOptionsDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantKeyBundleDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantKeyMaterialDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantKeySummaryDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantQueryRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantResponseKeyRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSaveRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSecurityMaterialDTO;
import com.scott.payment.admin.dto.merchant.MerchantOnboardingDTOs;
import com.scott.payment.admin.service.AdminMerchantInfoService;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanDO;
import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundAccountDO;
import com.scott.payment.admin.mapper.FeePlanMapper;
import com.scott.payment.admin.mapper.MerchantFundAccountMapper;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantJwtKeyDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantResponseKeyDO;
import com.scott.payment.component.db.auth.entity.BasePlatformPayloadKeyDO;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.mapper.BasePlatformPayloadKeyMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.auth.support.MerchantLocaleSupport;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.db.mcc.model.MccOptionSnapshot;
import com.scott.payment.component.db.mcc.service.MccOptionCacheReader;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.MerchantJwtKey;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.RsaKeyMaterial;
import com.scott.payment.component.security.openapi.OpenApiKeyType;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantInfoServiceImpl
 * @date : 2026-06-19 21:55
 * @email : scott_x@163.com
 * @description : 管理后台商户接入资料领域服务实现
 * @status : create
 *
 * <p>负责商户基础资料维护、商户状态切换以及 OpenAPI 密钥材料初始化与轮换等核心领域规则，
 * 不承担控制器协议适配和权限控制逻辑。</p>
 */
@Service
public class AdminMerchantInfoServiceImpl implements AdminMerchantInfoService {

    /**
     * {@code NOT_DELETED}常量，统一 {@code AdminMerchantInfoServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int NOT_DELETED = 0;
    /**
     * 启用标识，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：无；格式：布尔值或 0/1 标识；不允许为空；非敏感字段。
     * 取值范围：仅允许平台约定的真假取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int ENABLED = 1;
    /**
     * {@code DISABLED}，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：无；格式：布尔值或 0/1 标识；不允许为空；非敏感字段。
     * 取值范围：仅允许平台约定的真假取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int DISABLED = 0;
    /**
     * 默认状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final int DEFAULT_STATUS = 1;
    /**
     * 默认风控等级常量，统一 {@code AdminMerchantInfoServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int DEFAULT_RISK_LEVEL = 2;
    /**
     * 默认密钥大小，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int DEFAULT_KEY_SIZE = 2048;
    /**
     * {@code MERCHANT_ID_GENERATE_MAX_ATTEMPTS}常量，统一 {@code AdminMerchantInfoServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int MERCHANT_ID_GENERATE_MAX_ATTEMPTS = 5;
    /**
     * {@code MERCHANT_ID_PREFIX}常量，统一 {@code AdminMerchantInfoServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String MERCHANT_ID_PREFIX = "M";
    /**
     * {@code JWT_ALGORITHM}常量，统一 {@code AdminMerchantInfoServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String JWT_ALGORITHM = "HS256";
    /**
     * 报文算法，表示请求体、响应体或消息载荷，日志中只能保留脱敏摘要。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String PAYLOAD_ALGORITHM = "RSA-OAEP-256+A256GCM";
    /**
     * 商户主体名称和账单描述需要进入渠道侧及卡组织资料，限制为可打印英文字符避免中文导致渠道拒绝。
     */
    private static final String PRINTABLE_ASCII_PATTERN = "^[\\x20-\\x7E]+$";
    /**
     * 密钥版本格式化器，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter KEY_VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BaseMerchantInfoMapper merchantInfoMapper;
    /**
     * 收单支付敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private final BaseMerchantJwtKeyMapper jwtKeyMapper;
    /**
     * 收单支付敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private final BasePlatformPayloadKeyMapper platformPayloadKeyMapper;
    /**
     * 收单支付敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private final BaseMerchantResponseKeyMapper responseKeyMapper;
    /** 跨系统共享的 MCC 三级选项常驻缓存读取器。 */
    private final MccOptionCacheReader mccOptionCacheReader;

    /** 跨系统共享的 ISO 国家和币种常驻缓存服务。 */
    private final IsoDictionaryService isoDictionaryService;
    /**
     * 收单支付敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;
    /** Admin、Merchant Portal、OpenAPI 和支付服务共用的完整商户资料缓存。 */
    private final MerchantRuntimeProfileCacheService merchantRuntimeProfileCacheService;

    /** 密钥元数据永久缓存的事务型可靠失效协调器。 */
    private final ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;

    /** 已激活商户结算币种与现有资金账户的一致性服务。 */
    private final AdminMerchantFundAccountProvisioningService fundAccountProvisioningService;

    /** 商户登录账号查询组件，仅用于详情页判断登录体系是否已初始化。 */
    private final SysAccountMapper sysAccountMapper;

    /** 商户资金账户查询组件，仅用于详情页展示开户结果。 */
    private final MerchantFundAccountMapper fundAccountMapper;

    /** 商户费用方案查询组件，仅用于详情页展示当前生效版本。 */
    private final FeePlanMapper feePlanMapper;

    /** OpenAPI 密钥统一启停规则。 */
    private final OpenApiMerchantKeyMaterialService openApiKeyMaterialService;

    /** 商户密钥生命周期邮件通知。 */
    private final AdminMerchantSecurityNotificationService securityNotificationService;
    private final AdminMerchantStatusLifecycleService statusLifecycleService;

    /** 商户开户资料、审核与激活领域规则。 */
    private final MerchantOnboardingService merchantOnboardingService;

    /**
     * 创建管理后台商户信息服务实现。
     *
     * @param merchantInfoMapper        商户基础资料 Mapper
     * @param jwtKeyMapper              商户 JWT 密钥 Mapper
     * @param platformPayloadKeyMapper  平台请求体密钥 Mapper
     * @param responseKeyMapper         商户响应密钥 Mapper
     * @param mccOptionCacheReader      公共 MCC 三级选项缓存读取器
     * @param isoDictionaryService      公共 ISO 国家和币种缓存服务
     * @param keyMaterialFactory        密钥材料工厂
     * @param merchantRuntimeProfileCacheService 完整商户资料共享缓存
     * @param cacheInvalidationCoordinator 密钥元数据永久缓存可靠失效协调器
     * @param fundAccountProvisioningService 商户资金账户开户服务
     * @param sysAccountMapper 商户登录账号查询组件
     * @param fundAccountMapper 商户资金账户查询组件
     * @param feePlanMapper 商户费用方案查询组件
     * @param openApiKeyMaterialService OpenAPI 密钥统一领域服务
     * @param securityNotificationService 密钥生命周期通知服务
     * @param merchantOnboardingService 商户开户、审核与激活领域服务
     */
    public AdminMerchantInfoServiceImpl(BaseMerchantInfoMapper merchantInfoMapper,
                                        BaseMerchantJwtKeyMapper jwtKeyMapper,
                                        BasePlatformPayloadKeyMapper platformPayloadKeyMapper,
                                        BaseMerchantResponseKeyMapper responseKeyMapper,
                                        MccOptionCacheReader mccOptionCacheReader,
                                        IsoDictionaryService isoDictionaryService,
                                        OpenApiKeyMaterialFactory keyMaterialFactory,
                                        MerchantRuntimeProfileCacheService merchantRuntimeProfileCacheService,
                                        ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator,
                                        AdminMerchantFundAccountProvisioningService fundAccountProvisioningService,
                                        SysAccountMapper sysAccountMapper,
                                        MerchantFundAccountMapper fundAccountMapper,
                                        FeePlanMapper feePlanMapper,
                                        OpenApiMerchantKeyMaterialService openApiKeyMaterialService,
                                        AdminMerchantSecurityNotificationService securityNotificationService,
                                        AdminMerchantStatusLifecycleService statusLifecycleService,
                                        MerchantOnboardingService merchantOnboardingService) {
        this.merchantInfoMapper = merchantInfoMapper;
        this.jwtKeyMapper = jwtKeyMapper;
        this.platformPayloadKeyMapper = platformPayloadKeyMapper;
        this.responseKeyMapper = responseKeyMapper;
        this.mccOptionCacheReader = mccOptionCacheReader;
        this.isoDictionaryService = isoDictionaryService;
        this.keyMaterialFactory = keyMaterialFactory;
        this.merchantRuntimeProfileCacheService = merchantRuntimeProfileCacheService;
        this.cacheInvalidationCoordinator = cacheInvalidationCoordinator;
        this.fundAccountProvisioningService = fundAccountProvisioningService;
        this.sysAccountMapper = sysAccountMapper;
        this.fundAccountMapper = fundAccountMapper;
        this.feePlanMapper = feePlanMapper;
        this.openApiKeyMaterialService = openApiKeyMaterialService;
        this.securityNotificationService = securityNotificationService;
        this.statusLifecycleService = statusLifecycleService;
        this.merchantOnboardingService = merchantOnboardingService;
    }

    /**
     * 查询商户新增和编辑表单选项。
     *
     * <p>该接口只读取启用且未删除的 MCC、国家/地区和币种基础数据。MCC 叶子节点 value 为最终保存到
     * 商户资料的 MCC code；国家和币种 value 使用标准三位字母代码，避免页面保存展示文案。</p>
     *
     * @return 商户新增和编辑表单选项
     */
    @Override
    @DS(DataSourceName.MASTER)
    public AdminMerchantFormOptionsDTO getFormOptions() {
        AdminMerchantFormOptionsDTO result = new AdminMerchantFormOptionsDTO();
        result.setMccOptions(mccOptionCacheReader.listOptions()
                .stream()
                .map(this::toMccOption)
                .collect(Collectors.toCollection(ArrayList::new)));
        result.setCountries(isoDictionaryService.listCountries()
                .stream()
                .map(row -> optionItem(row.alpha3(),
                        label(row.alpha3(), row.chineseName(), row.englishName()),
                        row.chineseName(),
                        row.englishName(),
                        null,
                        null))
                .collect(Collectors.toCollection(ArrayList::new)));
        result.setCurrencies(isoDictionaryService.listCurrencies()
                .stream()
                .map(row -> optionItem(row.alphabeticCode(),
                        currencyLabel(row),
                        row.chineseName(),
                        row.englishName(),
                        row.defaultFractionDigits(),
                        row.minimumAmount()))
                .collect(Collectors.toCollection(ArrayList::new)));
        return result;
    }

    /**
     * 分页查询商户基础资料。
     *
     * @param request 查询条件
     * @return 商户分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<AdminMerchantInfoDTO> pageMerchants(AdminMerchantQueryRequest request) {
        AdminMerchantQueryRequest query = request == null ? new AdminMerchantQueryRequest() : request;
        LambdaQueryWrapper<BaseMerchantInfoDO> wrapper = Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED)
                .eq(query.getMerchantStatus() != null, BaseMerchantInfoDO::getMerchantStatus, query.getMerchantStatus())
                .eq(StringUtils.hasText(query.getCountryCode()), BaseMerchantInfoDO::getCountryCode, trimUpper(query.getCountryCode()))
                .eq(StringUtils.hasText(query.getSettlementCurrency()), BaseMerchantInfoDO::getSettlementCurrency, trimUpper(query.getSettlementCurrency()))
                .orderByDesc(BaseMerchantInfoDO::getGmtCreate);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(BaseMerchantInfoDO::getMerchantId, keyword)
                    .or().like(BaseMerchantInfoDO::getMerchantName, keyword)
                    .or().like(BaseMerchantInfoDO::getMerchantShortName, keyword));
        }
        Page<BaseMerchantInfoDO> page = merchantInfoMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()), wrapper);
        List<AdminMerchantInfoDTO> records = page.getRecords().stream().map(this::toDTO).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 查询单个商户详情。
     *
     * @param id 商户主键
     * @return 商户详情
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public AdminMerchantInfoDTO getMerchant(Long id) {
        BaseMerchantInfoDO row = requireMerchantById(id);
        return toDetailDTO(row);
    }

    /**
     * 补充商户详情页的登录、资金账户和当前费率初始化状态。
     *
     * <p>该方法只在单商户详情查询执行，避免列表页逐商户查询产生 N+1。</p>
     *
     * @param dto 已完成基础资料与密钥摘要转换的商户详情
     */
    private void enrichOperationalFoundation(AdminMerchantInfoDTO dto) {
        String merchantId = dto.getMerchantId();
        dto.setLoginInitialized(sysAccountMapper.selectCount(Wrappers.<SysAccountDO>lambdaQuery()
                .eq(SysAccountDO::getMerchantId, merchantId)
                .eq(SysAccountDO::getDeleted, 0L)) > 0);

        MerchantFundAccountDO fundAccount = fundAccountMapper.selectOne(
                Wrappers.<MerchantFundAccountDO>lambdaQuery()
                        .eq(MerchantFundAccountDO::getMerchantId, merchantId)
                        .eq(MerchantFundAccountDO::getDeleted, 0L)
                        .orderByAsc(MerchantFundAccountDO::getId)
                        .last("LIMIT 1"));
        if (fundAccount != null) {
            dto.setFundAccountNo(fundAccount.getAccountNo());
            dto.setFundAccountStatus(fundAccount.getAccountStatus());
        }

        FeePlanDO feePlan = feePlanMapper.selectOne(Wrappers.<FeePlanDO>lambdaQuery()
                .eq(FeePlanDO::getPlanType, "MERCHANT")
                .eq(FeePlanDO::getMerchantId, merchantId)
                .eq(FeePlanDO::getDeleted, 0L)
                .last("LIMIT 1"));
        if (feePlan != null) {
            dto.setCurrentFeeVersionNo(feePlan.getCurrentVersionNo());
        }
    }

    /**
     * 新增商户资料。
     *
     * @param request 保存请求
     * @return 商户详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantInfoDTO createMerchant(AdminMerchantSaveRequest request) {
        String merchantId = generateUniqueMerchantId();
        LocalDateTime now = LocalDateTime.now();
        BaseMerchantInfoDO row = new BaseMerchantInfoDO();
        row.setMerchantId(merchantId);
        merge(row, request);
        merchantOnboardingService.initializeDraft(row);
        row.setGmtCreate(now);
        row.setGmtModified(now);
        row.setDeleted(NOT_DELETED);
        prepareRuntimeProfileInvalidation(merchantId);
        merchantInfoMapper.insert(row);
        merchantOnboardingService.replaceRelatedPersons(merchantId, request.getRelatedPersons(), now);
        merchantRuntimeProfileCacheService.putRuntimeProfile(toRuntimeProfile(row));
        return toDetailDTO(row);
    }

    /**
     * 更新商户资料。
     *
     * @param id      商户主键
     * @param request 保存请求
     * @return 商户详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantInfoDTO updateMerchant(Long id, AdminMerchantSaveRequest request) {
        BaseMerchantInfoDO row = requireMerchantById(id);
        String requestedMerchantId = trimToNull(request.getMerchantId());
        if (requestedMerchantId != null && !row.getMerchantId().equals(requestedMerchantId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户号创建后不允许修改");
        }
        if (request.getMerchantStatus() != null
                && !Objects.equals(row.getMerchantStatus(), request.getMerchantStatus())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请使用冻结或解冻操作修改商户状态");
        }
        String requestedSettlementCurrency = trimUpper(request.getSettlementCurrency());
        boolean settlementCurrencyChanged = !Objects.equals(
                trimUpper(row.getSettlementCurrency()), requestedSettlementCurrency);
        if (settlementCurrencyChanged && hasFundAccount(row.getMerchantId())) {
            if (!StringUtils.hasText(requestedSettlementCurrency)) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                        "已开户商户的结算币种不能为空");
            }
            fundAccountProvisioningService.synchronizeSettlementCurrency(row.getMerchantId(), requestedSettlementCurrency);
        }
        merge(row, request);
        LocalDateTime now = LocalDateTime.now();
        row.setGmtModified(now);
        prepareRuntimeProfileInvalidation(row.getMerchantId());
        merchantInfoMapper.updateById(row);
        merchantOnboardingService.replaceRelatedPersons(row.getMerchantId(), request.getRelatedPersons(), now);
        merchantRuntimeProfileCacheService.putRuntimeProfile(toRuntimeProfile(row));
        return toDetailDTO(row);
    }

    /**
     * 提交商户开户资料进入人工审核。
     *
     * @param id 商户主键
     * @return 更新后的商户详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantInfoDTO submitReview(Long id) {
        BaseMerchantInfoDO row = requireMerchantById(id);
        merchantOnboardingService.submitForReview(row);
        return persistOnboardingState(row, null);
    }

    /**
     * 审核商户开户资料，支持通过、要求补件和驳回。
     *
     * @param id 商户主键
     * @param request 审核决定与意见
     * @return 更新后的商户详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantInfoDTO review(Long id, MerchantOnboardingDTOs.ReviewRequest request) {
        BaseMerchantInfoDO row = requireMerchantById(id);
        merchantOnboardingService.review(row, request);
        return persistOnboardingState(row, null);
    }

    /**
     * 激活审核通过且业务配置已就绪的商户。
     *
     * @param id 商户主键
     * @return 更新后的商户详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantInfoDTO activate(Long id) {
        BaseMerchantInfoDO row = requireMerchantById(id);
        Integer previousStatus = row.getMerchantStatus();
        merchantOnboardingService.activate(row);
        return persistOnboardingState(row, previousStatus);
    }

    /**
     * 持久化开户状态，并同步运行时缓存和商户启停联动。
     *
     * @param row 已完成状态迁移的商户主档，不允许为空
     * @param previousStatus 状态迁移前的运行状态；非运行状态变更时允许为空
     * @return 已重新加载关联资料和运行基础信息的商户详情
     */
    private AdminMerchantInfoDTO persistOnboardingState(BaseMerchantInfoDO row, Integer previousStatus) {
        LocalDateTime now = LocalDateTime.now();
        row.setGmtModified(now);
        prepareRuntimeProfileInvalidation(row.getMerchantId());
        merchantInfoMapper.updateById(row);
        merchantRuntimeProfileCacheService.putRuntimeProfile(toRuntimeProfile(row));
        if (previousStatus != null && !Objects.equals(previousStatus, row.getMerchantStatus())) {
            statusLifecycleService.onStatusChanged(row, row.getMerchantStatus(), now);
        }
        return toDetailDTO(row);
    }

    /**
     * 判断商户是否已经创建至少一个未删除资金账户。
     *
     * @param merchantId 平台商户号，不允许为空
     * @return true 表示结算币种变更必须同步现有资金账户
     */
    private boolean hasFundAccount(String merchantId) {
        Long count = fundAccountMapper.selectCount(Wrappers.<MerchantFundAccountDO>lambdaQuery()
                .eq(MerchantFundAccountDO::getMerchantId, merchantId)
                .eq(MerchantFundAccountDO::getDeleted, 0L));
        return count != null && count > 0;
    }

    /**
     * 更新商户状态。
     *
     * @param id             商户主键
     * @param merchantStatus 商户状态
     * @return 商户详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantInfoDTO updateStatus(Long id, Integer merchantStatus) {
        BaseMerchantInfoDO row = requireMerchantById(id);
        validateStatus(merchantStatus);
        validateStatusTransition(row.getMerchantStatus(), merchantStatus);
        validateUnfreezeEligibility(row, merchantStatus);
        LocalDateTime operationTime = LocalDateTime.now();
        row.setMerchantStatus(merchantStatus);
        row.setGmtModified(operationTime);
        prepareRuntimeProfileInvalidation(row.getMerchantId());
        merchantInfoMapper.updateById(row);
        merchantRuntimeProfileCacheService.putRuntimeProfile(toRuntimeProfile(row));
        statusLifecycleService.onStatusChanged(row, merchantStatus, operationTime);
        return toDTO(row);
    }

    private void validateStatusTransition(Integer currentStatus, Integer targetStatus) {
        boolean allowed = (Integer.valueOf(1).equals(currentStatus) && Integer.valueOf(2).equals(targetStatus))
                || (Integer.valueOf(2).equals(currentStatus) && Integer.valueOf(1).equals(targetStatus));
        if (!allowed) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户状态仅允许正常与冻结之间切换");
        }
    }

    /**
     * 校验冻结商户是否已经完成开户激活，防止草稿或审核中商户通过通用解冻接口绕过激活门禁。
     *
     * @param merchant 商户主档
     * @param targetStatus 目标运行状态
     */
    private void validateUnfreezeEligibility(BaseMerchantInfoDO merchant, Integer targetStatus) {
        if (Integer.valueOf(1).equals(targetStatus)
                && !MerchantOnboardingService.ACTIVATION_ACTIVE.equals(merchant.getActivationStatus())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "商户尚未完成开户激活，不能通过解冻操作启用");
        }
    }

    /**
     * 软删除商户和全部 OpenAPI 密钥记录，并在事务提交后清除共享商户缓存。
     *
     * @param id 商户主键
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteMerchant(Long id) {
        BaseMerchantInfoDO merchant = requireMerchantById(id);
        prepareRuntimeProfileInvalidation(merchant.getMerchantId());
        prepareKeyMetadataInvalidation(merchant.getMerchantId());
        cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_ROUTE, merchant.getMerchantId());
        cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_OPENAPI_ACCESS, merchant.getMerchantId());
        cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_ACTIVE_FEE, merchant.getMerchantId());
        LocalDateTime now = LocalDateTime.now();
        merchantInfoMapper.update(null, Wrappers.<BaseMerchantInfoDO>lambdaUpdate()
                .set(BaseMerchantInfoDO::getDeleted, 1)
                .set(BaseMerchantInfoDO::getGmtModified, now)
                .eq(BaseMerchantInfoDO::getId, merchant.getId())
                .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED));
        jwtKeyMapper.update(null, Wrappers.<BaseMerchantJwtKeyDO>lambdaUpdate()
                .set(BaseMerchantJwtKeyDO::getDeleted, 1)
                .set(BaseMerchantJwtKeyDO::getGmtModified, now)
                .eq(BaseMerchantJwtKeyDO::getMerchantId, merchant.getMerchantId())
                .eq(BaseMerchantJwtKeyDO::getDeleted, NOT_DELETED));
        platformPayloadKeyMapper.update(null, Wrappers.<BasePlatformPayloadKeyDO>lambdaUpdate()
                .set(BasePlatformPayloadKeyDO::getDeleted, 1)
                .set(BasePlatformPayloadKeyDO::getGmtModified, now)
                .eq(BasePlatformPayloadKeyDO::getMerchantId, merchant.getMerchantId())
                .eq(BasePlatformPayloadKeyDO::getDeleted, NOT_DELETED));
        responseKeyMapper.update(null, Wrappers.<BaseMerchantResponseKeyDO>lambdaUpdate()
                .set(BaseMerchantResponseKeyDO::getDeleted, 1)
                .set(BaseMerchantResponseKeyDO::getGmtModified, now)
                .eq(BaseMerchantResponseKeyDO::getMerchantId, merchant.getMerchantId())
                .eq(BaseMerchantResponseKeyDO::getDeleted, NOT_DELETED));
        merchantRuntimeProfileCacheService.evictRuntimeProfile(merchant.getMerchantId());
    }

    /**
     * 一次性初始化商户的 JWT、平台请求体和响应密钥材料。
     *
     * @param merchantId 商户号
     * @return 一次性安全材料
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantSecurityMaterialDTO provisionSecurityMaterial(String merchantId) {
        BaseMerchantInfoDO merchant = requireMerchantByMerchantId(merchantId);
        prepareKeyMetadataInvalidation(merchant.getMerchantId());
        MerchantJwtKey jwtKey = rotateJwtKeyInternal(merchant.getMerchantId());
        RsaKeyMaterial platformKey = rotatePlatformKeyInternal(merchant.getMerchantId());
        RsaKeyMaterial responseKey = rotateResponseKeyInternal(merchant.getMerchantId());
        AdminMerchantSecurityMaterialDTO dto = new AdminMerchantSecurityMaterialDTO();
        dto.setMerchantId(merchant.getMerchantId());
        dto.setMerchantName(merchant.getMerchantName());
        dto.setMerchantKey(jwtKey.merchantKey());
        dto.setMerchantKeyMasked(mask(jwtKey.merchantKey()));
        dto.setJwtAlgorithm(jwtKey.algorithm());
        dto.setJwtExpiresSeconds(jwtKey.expiresSeconds());
        dto.setPlatformPublicKeyX509Base64(platformKey.publicKeyX509Base64());
        dto.setMerchantResponsePublicKeyX509Base64(responseKey.publicKeyX509Base64());
        dto.setMerchantResponsePrivateKeyPkcs8Base64(responseKey.privateKeyPkcs8Base64());
        dto.setOneTimeSecret(true);
        securityNotificationService.sendAfterCommit(merchant,
                AdminMerchantSecurityNotificationService.TEMPLATE_CREATED,
                "OpenAPI 接入密钥套件",
                jwtKey.merchantKey());
        return dto;
    }

    /**
     * 查询商户当前全部密钥概览。
     *
     * @param merchantId 商户号
     * @return 密钥集合
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public AdminMerchantKeyBundleDTO getMerchantKeys(String merchantId) {
        BaseMerchantInfoDO merchant = requireMerchantByMerchantId(merchantId);
        AdminMerchantKeyBundleDTO bundle = new AdminMerchantKeyBundleDTO();
        bundle.setMerchantId(merchant.getMerchantId());
        bundle.setMerchantName(merchant.getMerchantName());

        List<BaseMerchantJwtKeyDO> jwtKeys = jwtKeyMapper.selectList(Wrappers.<BaseMerchantJwtKeyDO>lambdaQuery()
                .eq(BaseMerchantJwtKeyDO::getMerchantId, merchant.getMerchantId())
                .eq(BaseMerchantJwtKeyDO::getDeleted, NOT_DELETED)
                .orderByDesc(BaseMerchantJwtKeyDO::getEffectiveTime));
        jwtKeys.forEach(row -> bundle.getKeys().add(toJwtMaterial(row)));

        BasePlatformPayloadKeyDO platformKey = selectPlatformKey(merchant.getMerchantId());
        if (platformKey != null) {
            bundle.getKeys().add(toPlatformMaterial(platformKey));
        }

        BaseMerchantResponseKeyDO responseKey = selectResponseKey(merchant.getMerchantId());
        if (responseKey != null) {
            bundle.getKeys().add(toResponseMaterial(responseKey));
        }
        return bundle;
    }

    /**
     * 轮换商户 JWT 对称密钥。
     *
     * @param merchantId 商户号
     * @return 最新安全材料
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantSecurityMaterialDTO rotateJwtKey(String merchantId) {
        BaseMerchantInfoDO merchant = requireMerchantByMerchantId(merchantId);
        prepareKeyMetadataInvalidation(merchant.getMerchantId());
        MerchantJwtKey jwtKey = rotateJwtKeyInternal(merchant.getMerchantId());
        AdminMerchantSecurityMaterialDTO dto = baseMaterial(merchant);
        dto.setMerchantKey(jwtKey.merchantKey());
        dto.setMerchantKeyMasked(mask(jwtKey.merchantKey()));
        dto.setJwtAlgorithm(jwtKey.algorithm());
        dto.setJwtExpiresSeconds(jwtKey.expiresSeconds());
        dto.setOneTimeSecret(true);
        securityNotificationService.sendAfterCommit(merchant,
                AdminMerchantSecurityNotificationService.TEMPLATE_RESET,
                "JWT 签名密钥",
                jwtKey.merchantKey());
        return dto;
    }

    /**
     * 轮换平台请求体加密密钥。
     *
     * @param merchantId 商户号
     * @return 最新安全材料
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantSecurityMaterialDTO rotatePlatformPayloadKey(String merchantId) {
        BaseMerchantInfoDO merchant = requireMerchantByMerchantId(merchantId);
        prepareKeyMetadataInvalidation(merchant.getMerchantId());
        RsaKeyMaterial platformKey = rotatePlatformKeyInternal(merchant.getMerchantId());
        AdminMerchantSecurityMaterialDTO dto = baseMaterial(merchant);
        dto.setPlatformPublicKeyX509Base64(platformKey.publicKeyX509Base64());
        dto.setOneTimeSecret(false);
        securityNotificationService.sendAfterCommit(merchant,
                AdminMerchantSecurityNotificationService.TEMPLATE_RESET,
                "平台请求体密钥",
                platformKey.publicKeyX509Base64());
        return dto;
    }

    /**
     * 轮换商户响应密钥对。
     *
     * @param merchantId 商户号
     * @return 最新安全材料
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantSecurityMaterialDTO rotateMerchantResponseKey(String merchantId) {
        BaseMerchantInfoDO merchant = requireMerchantByMerchantId(merchantId);
        prepareKeyMetadataInvalidation(merchant.getMerchantId());
        RsaKeyMaterial responseKey = rotateResponseKeyInternal(merchant.getMerchantId());
        AdminMerchantSecurityMaterialDTO dto = baseMaterial(merchant);
        dto.setMerchantResponsePublicKeyX509Base64(responseKey.publicKeyX509Base64());
        dto.setMerchantResponsePrivateKeyPkcs8Base64(responseKey.privateKeyPkcs8Base64());
        dto.setOneTimeSecret(true);
        securityNotificationService.sendAfterCommit(merchant,
                AdminMerchantSecurityNotificationService.TEMPLATE_RESET,
                "商户响应密钥",
                responseKey.publicKeyX509Base64());
        return dto;
    }

    /**
     * 启用或停用当前 OpenAPI 密钥材料，登记缓存失效并在提交后发送安全通知。
     *
     * @param merchantId 商户号
     * @param keyType 密钥类型
     * @param enabled true 启用，false 停用
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void setOpenApiKeyEnabled(String merchantId, OpenApiKeyType keyType, boolean enabled) {
        BaseMerchantInfoDO merchant = requireMerchantByMerchantId(merchantId);
        prepareKeyMetadataInvalidation(merchant.getMerchantId());
        openApiKeyMaterialService.setEnabled(merchant.getMerchantId(), keyType, enabled);
        securityNotificationService.sendAfterCommit(
                merchant,
                enabled
                        ? AdminMerchantSecurityNotificationService.TEMPLATE_ENABLED
                        : AdminMerchantSecurityNotificationService.TEMPLATE_DISABLED,
                keyDisplayName(keyType),
                keyFingerprintSource(merchant.getMerchantId(), keyType)
        );
    }

    /**
     * 更新商户自维护的响应公钥材料。
     *
     * @param merchantId 商户号
     * @param request    公钥更新请求
     * @return 商户详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantInfoDTO updateMerchantResponseKey(String merchantId, AdminMerchantResponseKeyRequest request) {
        BaseMerchantInfoDO merchant = requireMerchantByMerchantId(merchantId);
        String publicKey = normalizeBase64(request.getPublicKeyX509Base64(), "响应公钥格式不正确");
        prepareKeyMetadataInvalidation(merchant.getMerchantId());
        BaseMerchantResponseKeyDO row = selectResponseKey(merchant.getMerchantId());
        LocalDateTime now = LocalDateTime.now();
        if (row == null) {
            row = new BaseMerchantResponseKeyDO();
            row.setMerchantId(merchant.getMerchantId());
            row.setGmtCreate(now);
            row.setDeleted(NOT_DELETED);
        }
        row.setPublicKeyX509Base64(publicKey);
        if (StringUtils.hasText(request.getPrivateKeyPkcs8Base64())) {
            row.setPrivateKeyPkcs8Base64(normalizeOptionalBase64(request.getPrivateKeyPkcs8Base64(), "响应私钥格式不正确"));
        }
        row.setAlgorithm(PAYLOAD_ALGORITHM);
        row.setKeySize(DEFAULT_KEY_SIZE);
        row.setEnabled(request.getEnabled() == null ? ENABLED : request.getEnabled());
        row.setGmtModified(now);
        if (row.getId() == null) {
            responseKeyMapper.insert(row);
        } else {
            responseKeyMapper.updateById(row);
        }
        return toDTO(merchant);
    }

    /**
     * 在密钥表变更前登记永久元数据缓存失效门禁和 Outbox 意图。
     *
     * @param merchantId 已确认存在的商户号
     */
    private void prepareKeyMetadataInvalidation(String merchantId) {
        cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_KEY_METADATA, merchantId);
    }

    /**
     * 在商户主表变更前登记完整资料缓存的门禁和 Outbox 失效意图。
     * <p>
     * 事务提交后，Outbox 先可靠删除旧缓存，transaction-aware CachePut 再写入新资料；
     * 即使新值写入 Redis 失败，也不会继续暴露旧的永久缓存。
     * </p>
     *
     * @param merchantId 已确认的商户号
     */
    private void prepareRuntimeProfileInvalidation(String merchantId) {
        cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE, merchantId);
    }

    /**
     * 使商户当前启用 JWT 密钥立即过期并创建新的对称密钥记录。
     *
     * <p>生成的密钥属于一次性敏感材料，仅允许由受控上层响应交付，
     * 禁止写入日志或在后续查询接口中再次返回明文。</p>
     *
     * @param merchantId 已确认存在的商户号
     * @return 新生成的 JWT 密钥及有效期
     */
    private MerchantJwtKey rotateJwtKeyInternal(String merchantId) {
        LocalDateTime now = LocalDateTime.now();
        jwtKeyMapper.update(null, Wrappers.<BaseMerchantJwtKeyDO>lambdaUpdate()
                .eq(BaseMerchantJwtKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantJwtKeyDO::getDeleted, NOT_DELETED)
                .eq(BaseMerchantJwtKeyDO::getEnabled, ENABLED)
                .set(BaseMerchantJwtKeyDO::getEnabled, DISABLED)
                .set(BaseMerchantJwtKeyDO::getExpireTime, now)
                .set(BaseMerchantJwtKeyDO::getGmtModified, now));
        MerchantJwtKey generated = keyMaterialFactory.generateMerchantJwtKey(merchantId);
        BaseMerchantJwtKeyDO row = new BaseMerchantJwtKeyDO();
        row.setMerchantId(merchantId);
        row.setKeyVersion("jwt-" + now.format(KEY_VERSION_FORMATTER));
        row.setMerchantKey(generated.merchantKey());
        row.setAlgorithm(JWT_ALGORITHM);
        row.setExpiresSeconds(generated.expiresSeconds());
        row.setEnabled(ENABLED);
        row.setEffectiveTime(now);
        row.setGmtCreate(now);
        row.setGmtModified(now);
        row.setDeleted(NOT_DELETED);
        jwtKeyMapper.insert(row);
        return generated;
    }

    /**
     * 生成并持久化平台请求体解密 RSA 密钥对，替换该商户现有材料。
     *
     * <p>私钥仅保存于受控密钥记录，不进入本方法返回值之外的日志或审计正文；
     * 上层只向商户交付平台公钥。</p>
     *
     * @param merchantId 已确认存在的商户号
     * @return 新生成的平台请求体 RSA 密钥材料
     */
    private RsaKeyMaterial rotatePlatformKeyInternal(String merchantId) {
        LocalDateTime now = LocalDateTime.now();
        RsaKeyMaterial generated = keyMaterialFactory.generatePlatformPayloadRsaKey(merchantId);
        BasePlatformPayloadKeyDO row = selectPlatformKey(merchantId);
        if (row == null) {
            row = new BasePlatformPayloadKeyDO();
            row.setMerchantId(merchantId);
            row.setGmtCreate(now);
            row.setDeleted(NOT_DELETED);
        }
        row.setPublicKeyX509Base64(generated.publicKeyX509Base64());
        row.setPrivateKeyPkcs8Base64(generated.privateKeyPkcs8Base64());
        row.setAlgorithm(PAYLOAD_ALGORITHM);
        row.setKeySize(generated.keySize());
        row.setEnabled(ENABLED);
        row.setGmtModified(now);
        if (row.getId() == null) {
            platformPayloadKeyMapper.insert(row);
        } else {
            platformPayloadKeyMapper.updateById(row);
        }
        return generated;
    }

    private RsaKeyMaterial rotateResponseKeyInternal(String merchantId) {
        LocalDateTime now = LocalDateTime.now();
        RsaKeyMaterial generated = keyMaterialFactory.generateMerchantResponseRsaKey(merchantId);
        BaseMerchantResponseKeyDO row = selectResponseKey(merchantId);
        if (row == null) {
            row = new BaseMerchantResponseKeyDO();
            row.setMerchantId(merchantId);
            row.setGmtCreate(now);
            row.setDeleted(NOT_DELETED);
        }
        row.setPublicKeyX509Base64(generated.publicKeyX509Base64());
        row.setPrivateKeyPkcs8Base64(generated.privateKeyPkcs8Base64());
        row.setAlgorithm(PAYLOAD_ALGORITHM);
        row.setKeySize(generated.keySize());
        row.setEnabled(ENABLED);
        row.setGmtModified(now);
        if (row.getId() == null) {
            responseKeyMapper.insert(row);
        } else {
            responseKeyMapper.updateById(row);
        }
        return generated;
    }

    private void merge(BaseMerchantInfoDO row, AdminMerchantSaveRequest request) {
        String merchantName = trimRequiredAscii(request.getMerchantName(), "商户名称仅支持英文、数字、空格及常见英文符号");
        row.setMerchantName(merchantName);
        row.setBillingDescriptor(trimOptionalAscii(
                request.getBillingDescriptor(), "账单描述仅支持英文、数字、空格及常见英文符号"));
        row.setMerchantShortName(request.getMerchantShortName().trim());
        row.setMerchantCategoryCode(trimUpper(request.getMerchantCategoryCode()));
        row.setCountryCode(trimUpper(request.getCountryCode()));
        row.setMerchantType(trimUpper(request.getMerchantType()));
        row.setOperatingCountry(trimUpper(request.getOperatingCountry()));
        row.setBusinessType(trimUpper(request.getBusinessType()));
        row.setIndustryCategory(trimUpper(request.getIndustryCategory()));
        row.setMerchantDescription(trimToNull(request.getMerchantDescription()));
        row.setRegistrationNumber(trimToNull(request.getRegistrationNumber()));
        row.setLegalEntityType(trimUpper(request.getLegalEntityType()));
        row.setIncorporationDate(request.getIncorporationDate());
        row.setIncorporationCountry(trimUpper(request.getIncorporationCountry()));
        row.setRegisteredState(trimToNull(request.getRegisteredState()));
        row.setRegisteredCity(trimToNull(request.getRegisteredCity()));
        row.setRegisteredPostcode(trimToNull(request.getRegisteredPostcode()));
        row.setRegisteredAddress(trimToNull(request.getRegisteredAddress()));
        row.setOperatingSameAsRegistered(booleanFlag(request.getOperatingSameAsRegistered()));
        row.setTaxId(trimToNull(request.getTaxId()));
        row.setCompanySize(trimUpper(request.getCompanySize()));
        row.setEmployeeCount(request.getEmployeeCount());
        row.setRegionCode(trimToNull(request.getRegionCode()));
        row.setCity(trimToNull(request.getCity()));
        row.setAddressLine(trimToNull(request.getAddressLine()));
        row.setPostalCode(trimToNull(request.getPostalCode()));
        row.setContactName(trimToNull(request.getContactName()));
        row.setContactTitle(trimToNull(request.getContactTitle()));
        row.setPhoneCountryCode(trimToNull(request.getPhoneCountryCode()));
        row.setContactEmail(trimLower(request.getContactEmail()));
        row.setContactPhone(trimToNull(request.getContactPhone()));
        row.setAlternateEmail(trimLower(request.getAlternateEmail()));
        row.setFinanceContactName(trimToNull(request.getFinanceContactName()));
        row.setFinanceContactEmail(trimLower(request.getFinanceContactEmail()));
        row.setTechnicalContactName(trimToNull(request.getTechnicalContactName()));
        row.setTechnicalContactEmail(trimLower(request.getTechnicalContactEmail()));
        row.setBusinessModel(trimUpper(request.getBusinessModel()));
        row.setSalesChannels(joinUpper(request.getSalesChannels()));
        row.setProductsServices(trimToNull(request.getProductsServices()));
        row.setTargetMarkets(joinUpper(request.getTargetMarkets()));
        row.setCustomerType(trimUpper(request.getCustomerType()));
        row.setTransactionCurrencies(joinUpper(request.getTransactionCurrencies()));
        row.setExpectedMonthlyVolume(request.getExpectedMonthlyVolume());
        row.setExpectedVolumeCurrency(trimUpper(request.getExpectedVolumeCurrency()));
        row.setAverageTicket(request.getAverageTicket());
        row.setMaxTicket(request.getMaxTicket());
        row.setExpectedMonthlyCount(request.getExpectedMonthlyCount());
        row.setExpectedRefundRate(request.getExpectedRefundRate());
        row.setExpectedChargebackRate(request.getExpectedChargebackRate());
        row.setRecurringPaymentFlag(booleanFlag(request.getRecurringPaymentFlag()));
        row.setPresaleFlag(booleanFlag(request.getPresaleFlag()));
        row.setFulfillmentDays(request.getFulfillmentDays());
        row.setDigitalGoodsFlag(booleanFlag(request.getDigitalGoodsFlag()));
        row.setRestrictedBusinessFlag(booleanFlag(request.getRestrictedBusinessFlag()));
        row.setExpectedGoLiveDate(request.getExpectedGoLiveDate());
        row.setWebsiteUrl(trimToNull(request.getWebsiteUrl()));
        row.setAppStoreUrl(trimToNull(request.getAppStoreUrl()));
        row.setGooglePlayUrl(trimToNull(request.getGooglePlayUrl()));
        row.setOtherSalesUrl(trimToNull(request.getOtherSalesUrl()));
        row.setWebsiteLanguages(joinUpper(request.getWebsiteLanguages()));
        row.setWebsiteLiveFlag(booleanFlag(request.getWebsiteLiveFlag()));
        row.setPrivacyPolicyUrl(trimToNull(request.getPrivacyPolicyUrl()));
        row.setRefundPolicyUrl(trimToNull(request.getRefundPolicyUrl()));
        row.setTermsUrl(trimToNull(request.getTermsUrl()));
        row.setShippingPolicyUrl(trimToNull(request.getShippingPolicyUrl()));
        row.setSettlementCurrency(trimUpper(request.getSettlementCurrency()));
        row.setTimezone(request.getTimezone().trim());
        row.setDefaultLocale(MerchantLocaleSupport.normalize(request.getDefaultLocale()));
        row.setRiskLevel(request.getRiskLevel() == null ? DEFAULT_RISK_LEVEL : request.getRiskLevel());
        validateRiskLevel(row.getRiskLevel());
    }

    /**
     * 组装商户资料表单使用的 MCC 三级级联树。
     *
     * <p>一级、二级分类 value 使用带前缀的内部定位值，避免与真实四位 MCC code 冲突；叶子节点 value
     * 使用 MCC code，前端保存时只提交叶子节点。</p>
     *
     * @return MCC 级联选项
     */
    private AdminMerchantFormOptionsDTO.OptionNode toMccOption(MccOptionSnapshot source) {
        AdminMerchantFormOptionsDTO.OptionNode option = optionNode(
                source.getValue(), source.getLabel(), source.getNameCn(), source.getNameEn());
        option.setChildren(source.getChildren().stream()
                .map(this::toMccOption)
                .collect(Collectors.toCollection(ArrayList::new)));
        return option;
    }

    /**
     * 创建 MCC 级联节点。
     *
     * @param value  节点值；父级为内部定位值，叶子节点为 MCC code
     * @param label  兼容展示标签
     * @param nameCn 中文名称
     * @param nameEn 英文名称
     * @return MCC 级联节点
     */
    private AdminMerchantFormOptionsDTO.OptionNode optionNode(String value, String label, String nameCn, String nameEn) {
        AdminMerchantFormOptionsDTO.OptionNode node = new AdminMerchantFormOptionsDTO.OptionNode();
        node.setValue(value);
        node.setLabel(label);
        node.setNameCn(nameCn);
        node.setNameEn(nameEn);
        return node;
    }

    /**
     * 创建普通表单下拉选项。
     *
     * @param value           标准三位字母代码
     * @param label           兼容展示标签
     * @param nameCn          中文名称
     * @param nameEn          英文名称
     * @param fractionDigits  币种默认辅币位，仅币种选项有值
     * @param minimumAmount   币种最小金额单位，仅币种选项有值
     * @return 表单下拉选项
     */
    private AdminMerchantFormOptionsDTO.OptionItem optionItem(String value,
                                                             String label,
                                                             String nameCn,
                                                             String nameEn,
                                                             Integer fractionDigits,
                                                             BigDecimal minimumAmount) {
        AdminMerchantFormOptionsDTO.OptionItem item = new AdminMerchantFormOptionsDTO.OptionItem();
        item.setValue(value);
        item.setLabel(label);
        item.setNameCn(nameCn);
        item.setNameEn(nameEn);
        item.setFractionDigits(fractionDigits);
        item.setMinimumAmount(minimumAmount);
        return item;
    }

    /**
     * 生成兼容展示标签。
     *
     * <p>前端会优先根据当前语言使用 nameCn/nameEn 重新生成选中回显；该字段保留给旧调用方或异常兜底。</p>
     *
     * @param code   标准代码
     * @param nameCn 中文名称
     * @param nameEn 英文名称
     * @return 兼容展示标签
     */
    private String label(String code, String nameCn, String nameEn) {
        StringBuilder builder = new StringBuilder(code == null ? "" : code);
        if (StringUtils.hasText(nameCn)) {
            builder.append(" — ").append(nameCn.trim());
        }
        if (StringUtils.hasText(nameEn)) {
            builder.append(" / ").append(nameEn.trim());
        }
        return builder.toString();
    }

    /**
     * 生成币种兼容展示标签。
     *
     * <p>标签包含默认辅币位和最小金额，便于旧前端或接口调试时直接识别币种金额精度约束。</p>
     *
     * @param row 币种数据库实体
     * @return 币种兼容展示标签
     */
    private String currencyLabel(IsoCurrencyInfo row) {
        StringBuilder builder = new StringBuilder(label(
                row.alphabeticCode(), row.chineseName(), row.englishName()));
        if (row.defaultFractionDigits() >= 0) {
            builder.append("，辅币位：").append(row.defaultFractionDigits());
        }
        if (row.minimumAmount() != null) {
            builder.append("，最小金额：").append(row.minimumAmount().stripTrailingZeros().toPlainString());
        }
        return builder.toString();
    }

    private AdminMerchantInfoDTO toDTO(BaseMerchantInfoDO row) {
        AdminMerchantInfoDTO dto = new AdminMerchantInfoDTO();
        dto.setId(row.getId());
        dto.setMerchantId(row.getMerchantId());
        dto.setApplicationNo(row.getApplicationNo());
        dto.setOnboardingSource(row.getOnboardingSource());
        dto.setOnboardingStatus(row.getOnboardingStatus());
        dto.setReviewStatus(row.getReviewStatus());
        dto.setActivationStatus(row.getActivationStatus());
        dto.setMerchantName(row.getMerchantName());
        dto.setBillingDescriptor(row.getBillingDescriptor());
        dto.setMerchantShortName(row.getMerchantShortName());
        dto.setMerchantType(row.getMerchantType());
        dto.setMerchantStatus(row.getMerchantStatus());
        dto.setDefaultLocale(MerchantLocaleSupport.normalize(row.getDefaultLocale()));
        dto.setMerchantCategoryCode(row.getMerchantCategoryCode());
        dto.setCountryCode(row.getCountryCode());
        dto.setOperatingCountry(row.getOperatingCountry());
        dto.setBusinessType(row.getBusinessType());
        dto.setIndustryCategory(row.getIndustryCategory());
        dto.setMerchantDescription(row.getMerchantDescription());
        dto.setRegistrationNumber(row.getRegistrationNumber());
        dto.setLegalEntityType(row.getLegalEntityType());
        dto.setIncorporationDate(row.getIncorporationDate());
        dto.setIncorporationCountry(row.getIncorporationCountry());
        dto.setRegisteredState(row.getRegisteredState());
        dto.setRegisteredCity(row.getRegisteredCity());
        dto.setRegisteredPostcode(row.getRegisteredPostcode());
        dto.setRegisteredAddress(row.getRegisteredAddress());
        dto.setOperatingSameAsRegistered(toBoolean(row.getOperatingSameAsRegistered()));
        dto.setTaxId(row.getTaxId());
        dto.setCompanySize(row.getCompanySize());
        dto.setEmployeeCount(row.getEmployeeCount());
        dto.setRegionCode(row.getRegionCode());
        dto.setCity(row.getCity());
        dto.setAddressLine(row.getAddressLine());
        dto.setPostalCode(row.getPostalCode());
        dto.setContactName(row.getContactName());
        dto.setContactTitle(row.getContactTitle());
        dto.setPhoneCountryCode(row.getPhoneCountryCode());
        dto.setContactEmail(row.getContactEmail());
        dto.setContactPhone(row.getContactPhone());
        dto.setAlternateEmail(row.getAlternateEmail());
        dto.setFinanceContactName(row.getFinanceContactName());
        dto.setFinanceContactEmail(row.getFinanceContactEmail());
        dto.setTechnicalContactName(row.getTechnicalContactName());
        dto.setTechnicalContactEmail(row.getTechnicalContactEmail());
        dto.setBusinessModel(row.getBusinessModel());
        dto.setSalesChannels(splitValues(row.getSalesChannels()));
        dto.setProductsServices(row.getProductsServices());
        dto.setTargetMarkets(splitValues(row.getTargetMarkets()));
        dto.setCustomerType(row.getCustomerType());
        dto.setTransactionCurrencies(splitValues(row.getTransactionCurrencies()));
        dto.setExpectedMonthlyVolume(row.getExpectedMonthlyVolume());
        dto.setExpectedVolumeCurrency(row.getExpectedVolumeCurrency());
        dto.setAverageTicket(row.getAverageTicket());
        dto.setMaxTicket(row.getMaxTicket());
        dto.setExpectedMonthlyCount(row.getExpectedMonthlyCount());
        dto.setExpectedRefundRate(row.getExpectedRefundRate());
        dto.setExpectedChargebackRate(row.getExpectedChargebackRate());
        dto.setRecurringPaymentFlag(toBoolean(row.getRecurringPaymentFlag()));
        dto.setPresaleFlag(toBoolean(row.getPresaleFlag()));
        dto.setFulfillmentDays(row.getFulfillmentDays());
        dto.setDigitalGoodsFlag(toBoolean(row.getDigitalGoodsFlag()));
        dto.setRestrictedBusinessFlag(toBoolean(row.getRestrictedBusinessFlag()));
        dto.setExpectedGoLiveDate(row.getExpectedGoLiveDate());
        dto.setWebsiteUrl(row.getWebsiteUrl());
        dto.setAppStoreUrl(row.getAppStoreUrl());
        dto.setGooglePlayUrl(row.getGooglePlayUrl());
        dto.setOtherSalesUrl(row.getOtherSalesUrl());
        dto.setWebsiteLanguages(splitValues(row.getWebsiteLanguages()));
        dto.setWebsiteLiveFlag(toBoolean(row.getWebsiteLiveFlag()));
        dto.setPrivacyPolicyUrl(row.getPrivacyPolicyUrl());
        dto.setRefundPolicyUrl(row.getRefundPolicyUrl());
        dto.setTermsUrl(row.getTermsUrl());
        dto.setShippingPolicyUrl(row.getShippingPolicyUrl());
        dto.setSettlementCurrency(row.getSettlementCurrency());
        dto.setTimezone(row.getTimezone());
        dto.setRiskLevel(row.getRiskLevel());
        dto.setGmtCreate(row.getGmtCreate());
        dto.setGmtModified(row.getGmtModified());
        dto.setJwtKey(toJwtSummary(selectActiveJwtKey(row.getMerchantId())));
        dto.setPlatformPayloadKey(toPlatformSummary(selectPlatformKey(row.getMerchantId())));
        dto.setResponseKey(toResponseSummary(selectResponseKey(row.getMerchantId())));
        return dto;
    }

    /**
     * 组装单商户详情并补充一对多开户资料；列表查询不调用该方法，避免额外查询。
     *
     * @param row 商户主档，不允许为空
     * @return 包含相关人员、资料、审核轨迹和运行基础信息的详情 DTO
     */
    private AdminMerchantInfoDTO toDetailDTO(BaseMerchantInfoDO row) {
        AdminMerchantInfoDTO dto = toDTO(row);
        merchantOnboardingService.enrich(row, dto);
        enrichOperationalFoundation(dto);
        return dto;
    }

    /**
     * 将主库商户记录转换为跨服务共享缓存资料。
     *
     * @param row 已写入主库的完整商户记录
     * @return 不包含密钥材料的完整商户缓存资料
     */
    private MerchantRuntimeProfile toRuntimeProfile(BaseMerchantInfoDO row) {
        MerchantRuntimeProfile profile = new MerchantRuntimeProfile();
        profile.setId(row.getId());
        profile.setMerchantId(row.getMerchantId());
        profile.setMerchantName(row.getMerchantName());
        profile.setBillingDescriptor(row.getBillingDescriptor());
        profile.setMerchantShortName(row.getMerchantShortName());
        profile.setMerchantStatus(row.getMerchantStatus());
        profile.setDefaultLocale(MerchantLocaleSupport.normalize(row.getDefaultLocale()));
        profile.setMerchantCategoryCode(row.getMerchantCategoryCode());
        profile.setCountryCode(row.getCountryCode());
        profile.setRegionCode(row.getRegionCode());
        profile.setCity(row.getCity());
        profile.setAddressLine(row.getAddressLine());
        profile.setPostalCode(row.getPostalCode());
        profile.setContactName(row.getContactName());
        profile.setContactEmail(row.getContactEmail());
        profile.setContactPhone(row.getContactPhone());
        profile.setSettlementCurrency(row.getSettlementCurrency());
        profile.setTimezone(row.getTimezone());
        profile.setRiskLevel(row.getRiskLevel());
        profile.setGmtCreate(row.getGmtCreate());
        profile.setGmtModified(row.getGmtModified());
        return profile;
    }

    private AdminMerchantSecurityMaterialDTO baseMaterial(BaseMerchantInfoDO merchant) {
        AdminMerchantSecurityMaterialDTO dto = new AdminMerchantSecurityMaterialDTO();
        dto.setMerchantId(merchant.getMerchantId());
        dto.setMerchantName(merchant.getMerchantName());
        return dto;
    }

    private AdminMerchantKeySummaryDTO toJwtSummary(BaseMerchantJwtKeyDO row) {
        if (row == null) {
            return null;
        }
        AdminMerchantKeySummaryDTO dto = new AdminMerchantKeySummaryDTO();
        dto.setId(row.getId());
        dto.setKeyVersion(row.getKeyVersion());
        dto.setAlgorithm(row.getAlgorithm());
        dto.setExpiresSeconds(row.getExpiresSeconds());
        dto.setEnabled(row.getEnabled());
        dto.setFingerprint(fingerprint(row.getMerchantKey()));
        dto.setEffectiveTime(row.getEffectiveTime());
        dto.setExpireTime(row.getExpireTime());
        dto.setGmtModified(row.getGmtModified());
        return dto;
    }

    private AdminMerchantKeySummaryDTO toPlatformSummary(BasePlatformPayloadKeyDO row) {
        if (row == null) {
            return null;
        }
        AdminMerchantKeySummaryDTO dto = new AdminMerchantKeySummaryDTO();
        dto.setId(row.getId());
        dto.setAlgorithm(row.getAlgorithm());
        dto.setKeySize(row.getKeySize());
        dto.setEnabled(row.getEnabled());
        dto.setFingerprint(fingerprint(row.getPublicKeyX509Base64()));
        dto.setGmtModified(row.getGmtModified());
        return dto;
    }

    private AdminMerchantKeySummaryDTO toResponseSummary(BaseMerchantResponseKeyDO row) {
        if (row == null) {
            return null;
        }
        AdminMerchantKeySummaryDTO dto = new AdminMerchantKeySummaryDTO();
        dto.setId(row.getId());
        dto.setAlgorithm(row.getAlgorithm());
        dto.setKeySize(row.getKeySize());
        dto.setEnabled(row.getEnabled());
        dto.setFingerprint(fingerprint(row.getPublicKeyX509Base64()));
        dto.setGmtModified(row.getGmtModified());
        return dto;
    }

    private AdminMerchantKeyMaterialDTO toJwtMaterial(BaseMerchantJwtKeyDO row) {
        AdminMerchantKeyMaterialDTO dto = new AdminMerchantKeyMaterialDTO();
        dto.setKeyType("MERCHANT_JWT");
        dto.setKeyName("merchantKey");
        dto.setOwner("商户");
        dto.setUsage("商户服务端使用 merchantKey 签发 OpenAPI JWT；平台使用同一密钥验签。");
        dto.setKeyVersion(row.getKeyVersion());
        dto.setAlgorithm(row.getAlgorithm());
        dto.setExpiresSeconds(row.getExpiresSeconds());
        dto.setEnabled(row.getEnabled());
        dto.setMerchantKey(null);
        dto.setFingerprint(fingerprint(row.getMerchantKey()));
        dto.setStored(StringUtils.hasText(row.getMerchantKey()));
        dto.setEffectiveTime(row.getEffectiveTime());
        dto.setExpireTime(row.getExpireTime());
        dto.setGmtModified(row.getGmtModified());
        return dto;
    }

    private AdminMerchantKeyMaterialDTO toPlatformMaterial(BasePlatformPayloadKeyDO row) {
        AdminMerchantKeyMaterialDTO dto = new AdminMerchantKeyMaterialDTO();
        dto.setKeyType("PLATFORM_REQUEST_PAYLOAD_RSA");
        dto.setKeyName("platformPayloadKey");
        dto.setOwner("平台");
        dto.setUsage("商户使用 platformPublicKeyX509Base64 加密请求 data；平台使用 platformPrivateKeyPkcs8Base64 解密商户请求 data。");
        dto.setAlgorithm(row.getAlgorithm());
        dto.setKeySize(row.getKeySize());
        dto.setEnabled(row.getEnabled());
        dto.setPublicKeyX509Base64(null);
        dto.setPrivateKeyPkcs8Base64(null);
        dto.setFingerprint(fingerprint(row.getPublicKeyX509Base64()));
        dto.setStored(StringUtils.hasText(row.getPrivateKeyPkcs8Base64()));
        dto.setGmtModified(row.getGmtModified());
        return dto;
    }

    private AdminMerchantKeyMaterialDTO toResponseMaterial(BaseMerchantResponseKeyDO row) {
        AdminMerchantKeyMaterialDTO dto = new AdminMerchantKeyMaterialDTO();
        dto.setKeyType("MERCHANT_RESPONSE_PAYLOAD_RSA");
        dto.setKeyName("merchantResponseKey");
        dto.setOwner("商户");
        dto.setUsage("平台使用 merchantResponsePublicKeyX509Base64 加密 API 响应 data；商户使用 merchantResponsePrivateKeyPkcs8Base64 解密响应 data。");
        dto.setAlgorithm(row.getAlgorithm());
        dto.setKeySize(row.getKeySize());
        dto.setEnabled(row.getEnabled());
        dto.setPublicKeyX509Base64(null);
        dto.setPrivateKeyPkcs8Base64(null);
        dto.setFingerprint(fingerprint(row.getPublicKeyX509Base64()));
        dto.setStored(StringUtils.hasText(row.getPrivateKeyPkcs8Base64()));
        dto.setGmtModified(row.getGmtModified());
        return dto;
    }

    private BaseMerchantInfoDO requireMerchantById(Long id) {
        BaseMerchantInfoDO row = merchantInfoMapper.selectOne(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                .eq(BaseMerchantInfoDO::getId, id)
                .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "商户不存在");
        }
        return row;
    }

    private BaseMerchantInfoDO requireMerchantByMerchantId(String merchantId) {
        BaseMerchantInfoDO row = selectMerchantByMerchantId(normalizeMerchantId(merchantId));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "商户不存在");
        }
        return row;
    }

    private BaseMerchantInfoDO selectMerchantByMerchantId(String merchantId) {
        return merchantInfoMapper.selectOne(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    private String generateUniqueMerchantId() {
        for (int attempt = 0; attempt < MERCHANT_ID_GENERATE_MAX_ATTEMPTS; attempt++) {
            String merchantId = PaymentOrderNoGenerator.nextOrderNo(MERCHANT_ID_PREFIX);
            if (selectMerchantByMerchantId(merchantId) == null) {
                return merchantId;
            }
        }
        throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "商户号生成失败，请稍后重试");
    }

    private BaseMerchantJwtKeyDO selectActiveJwtKey(String merchantId) {
        return jwtKeyMapper.selectOne(Wrappers.<BaseMerchantJwtKeyDO>lambdaQuery()
                .eq(BaseMerchantJwtKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantJwtKeyDO::getDeleted, NOT_DELETED)
                .eq(BaseMerchantJwtKeyDO::getEnabled, ENABLED)
                .orderByDesc(BaseMerchantJwtKeyDO::getEffectiveTime)
                .last("LIMIT 1"));
    }

    /** 查询最新 JWT 记录，包括已停用记录，供安全通知生成不可逆指纹。 */
    private BaseMerchantJwtKeyDO selectLatestJwtKey(String merchantId) {
        return jwtKeyMapper.selectOne(Wrappers.<BaseMerchantJwtKeyDO>lambdaQuery()
                .eq(BaseMerchantJwtKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantJwtKeyDO::getDeleted, NOT_DELETED)
                .orderByDesc(BaseMerchantJwtKeyDO::getEffectiveTime)
                .orderByDesc(BaseMerchantJwtKeyDO::getId)
                .last("LIMIT 1"));
    }

    /** 返回邮件中可展示的密钥材料名称。 */
    private String keyDisplayName(OpenApiKeyType keyType) {
        if (keyType == OpenApiKeyType.JWT_KEY) {
            return "JWT 签名密钥";
        }
        if (keyType == OpenApiKeyType.PLATFORM_PUBLIC_KEY || keyType == OpenApiKeyType.PLATFORM_PAYLOAD_KEY) {
            return "平台请求体密钥";
        }
        if (keyType == OpenApiKeyType.MERCHANT_RESPONSE_PUBLIC_KEY
                || keyType == OpenApiKeyType.MERCHANT_RESPONSE_PRIVATE_KEY
                || keyType == OpenApiKeyType.MERCHANT_RESPONSE_KEY) {
            return "商户响应密钥";
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "keyType 不支持启停");
    }

    /** 返回仅用于计算通知指纹的材料，原文不会离开当前进程或写入日志。 */
    private String keyFingerprintSource(String merchantId, OpenApiKeyType keyType) {
        if (keyType == OpenApiKeyType.JWT_KEY) {
            BaseMerchantJwtKeyDO row = selectLatestJwtKey(merchantId);
            return row == null ? null : row.getMerchantKey();
        }
        if (keyType == OpenApiKeyType.PLATFORM_PUBLIC_KEY || keyType == OpenApiKeyType.PLATFORM_PAYLOAD_KEY) {
            BasePlatformPayloadKeyDO row = selectPlatformKey(merchantId);
            return row == null ? null : row.getPublicKeyX509Base64();
        }
        BaseMerchantResponseKeyDO row = selectResponseKey(merchantId);
        return row == null ? null : row.getPublicKeyX509Base64();
    }

    private BasePlatformPayloadKeyDO selectPlatformKey(String merchantId) {
        return platformPayloadKeyMapper.selectOne(Wrappers.<BasePlatformPayloadKeyDO>lambdaQuery()
                .eq(BasePlatformPayloadKeyDO::getMerchantId, merchantId)
                .eq(BasePlatformPayloadKeyDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    private BaseMerchantResponseKeyDO selectResponseKey(String merchantId) {
        return responseKeyMapper.selectOne(Wrappers.<BaseMerchantResponseKeyDO>lambdaQuery()
                .eq(BaseMerchantResponseKeyDO::getMerchantId, merchantId)
                .eq(BaseMerchantResponseKeyDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    private String normalizeMerchantId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户号不能为空");
        }
        return value.trim();
    }

    private String normalizeBase64(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), errorMessage);
        }
        String normalized = value
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            Base64.getDecoder().decode(normalized);
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), errorMessage);
        }
    }

    private String normalizeOptionalBase64(String value, String errorMessage) {
        return StringUtils.hasText(value) ? normalizeBase64(value, errorMessage) : null;
    }

    private String trimUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimLower(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String joinUpper(List<String> values) {
        if (values == null) {
            return null;
        }
        String joined = values.stream()
                .filter(StringUtils::hasText)
                .map(this::trimUpper)
                .distinct()
                .collect(Collectors.joining(","));
        return StringUtils.hasText(joined) ? joined : null;
    }

    private List<String> splitValues(String value) {
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Integer booleanFlag(Boolean value) {
        return value == null ? null : Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private Boolean toBoolean(Integer value) {
        return value == null ? null : Integer.valueOf(1).equals(value);
    }

    private String trimRequiredAscii(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), errorMessage);
        }
        String trimmed = value.trim();
        if (!trimmed.matches(PRINTABLE_ASCII_PATTERN)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), errorMessage);
        }
        return trimmed;
    }

    private String trimOptionalAscii(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return trimRequiredAscii(value, errorMessage);
    }

    private String fingerprint(String value) {
        return StringUtils.hasText(value) ? keyMaterialFactory.fingerprint(value) : null;
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() <= 12) {
            return "******";
        }
        return value.substring(0, 6) + "******" + value.substring(value.length() - 4);
    }

    private void validateStatus(Integer status) {
        if (status == null || status < 1 || status > 3) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户状态必须为1正常、2冻结或3关闭");
        }
    }

    private void validateRiskLevel(Integer riskLevel) {
        if (riskLevel == null || riskLevel < 1 || riskLevel > 3) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "风险等级必须为1低、2中或3高");
        }
    }
}
