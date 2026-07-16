package com.scott.payment.admin.service.impl;

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
import com.scott.payment.admin.service.AdminMerchantInfoService;
import com.scott.payment.admin.entity.base.MccEntities;
import com.scott.payment.admin.mapper.BaseMccCodeMapper;
import com.scott.payment.admin.mapper.BaseMccLevel1Mapper;
import com.scott.payment.admin.mapper.BaseMccLevel2Mapper;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantJwtKeyDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantResponseKeyDO;
import com.scott.payment.component.db.auth.entity.BasePlatformPayloadKeyDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.mapper.BasePlatformPayloadKeyMapper;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.MerchantJwtKey;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.RsaKeyMaterial;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantInfoServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Admin Merchant Info Service Impl，位于 service-admin 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminMerchantInfoServiceImpl implements AdminMerchantInfoService {

    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String MCC_LEVEL1_VALUE_PREFIX = "L1:";
    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String MCC_LEVEL2_VALUE_PREFIX = "L2:";
    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final int NOT_DELETED = 0;
    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final int ENABLED = 1;
    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final int DISABLED = 0;
    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final int DEFAULT_STATUS = 1;
    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final int DEFAULT_RISK_LEVEL = 2;
    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final int DEFAULT_KEY_SIZE = 2048;
    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final int MERCHANT_ID_GENERATE_MAX_ATTEMPTS = 5;
    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String MERCHANT_ID_PREFIX = "M";
    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String JWT_ALGORITHM = "HS256";
    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String PAYLOAD_ALGORITHM = "RSA-OAEP-256+A256GCM";
    /**
     * 商户主体名称和账单描述需要进入渠道侧及卡组织资料，限制为可打印英文字符避免中文导致渠道拒绝。
     */
    private static final String PRINTABLE_ASCII_PATTERN = "^[\\x20-\\x7E]+$";
    private static final DateTimeFormatter KEY_VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
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
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final BaseMccLevel1Mapper mccLevel1Mapper;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final BaseMccLevel2Mapper mccLevel2Mapper;
    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private final BaseMccCodeMapper mccCodeMapper;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final IsoCountryMapper isoCountryMapper;
    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    private final IsoCurrencyMapper isoCurrencyMapper;
    /**
     * 收单支付敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;

    /**
     * 创建管理后台商户信息服务实现。
     *
     * @param merchantInfoMapper        商户基础资料 Mapper
     * @param jwtKeyMapper              商户 JWT 密钥 Mapper
     * @param platformPayloadKeyMapper  平台请求体密钥 Mapper
     * @param responseKeyMapper         商户响应密钥 Mapper
     * @param mccLevel1Mapper           MCC 一级分类 Mapper
     * @param mccLevel2Mapper           MCC 二级分类 Mapper
     * @param mccCodeMapper             MCC 编码 Mapper
     * @param isoCountryMapper          国家地区 Mapper
     * @param isoCurrencyMapper         币种 Mapper
     * @param keyMaterialFactory        密钥材料工厂
     */
    public AdminMerchantInfoServiceImpl(BaseMerchantInfoMapper merchantInfoMapper,
                                        BaseMerchantJwtKeyMapper jwtKeyMapper,
                                        BasePlatformPayloadKeyMapper platformPayloadKeyMapper,
                                        BaseMerchantResponseKeyMapper responseKeyMapper,
                                        BaseMccLevel1Mapper mccLevel1Mapper,
                                        BaseMccLevel2Mapper mccLevel2Mapper,
                                        BaseMccCodeMapper mccCodeMapper,
                                        IsoCountryMapper isoCountryMapper,
                                        IsoCurrencyMapper isoCurrencyMapper,
                                        OpenApiKeyMaterialFactory keyMaterialFactory) {
        this.merchantInfoMapper = merchantInfoMapper;
        this.jwtKeyMapper = jwtKeyMapper;
        this.platformPayloadKeyMapper = platformPayloadKeyMapper;
        this.responseKeyMapper = responseKeyMapper;
        this.mccLevel1Mapper = mccLevel1Mapper;
        this.mccLevel2Mapper = mccLevel2Mapper;
        this.mccCodeMapper = mccCodeMapper;
        this.isoCountryMapper = isoCountryMapper;
        this.isoCurrencyMapper = isoCurrencyMapper;
        this.keyMaterialFactory = keyMaterialFactory;
    }

    /**
     * 查询商户新增和编辑表单选项。
     *
     * <p>该接口只读取启用且未删除的 MCC、国家/地区和币种基础数据。MCC 叶子节点 value 为最终保存到
     * 商户资料的 MCC code；国家和币种 value 使用标准三位字母代码，避免页面保存展示文案。</p>
     *
     * @return 商户新增和编辑表单选项
     */
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public AdminMerchantFormOptionsDTO getFormOptions() {
        AdminMerchantFormOptionsDTO result = new AdminMerchantFormOptionsDTO();
        result.setMccOptions(buildMccOptions());
        result.setCountries(isoCountryMapper.selectList(Wrappers.<IsoCountryDO>lambdaQuery()
                        .eq(IsoCountryDO::getDeleted, NOT_DELETED)
                        .eq(IsoCountryDO::getStatus, ENABLED)
                        .orderByAsc(IsoCountryDO::getAlpha3Code))
                .stream()
                .map(row -> optionItem(row.getAlpha3Code(),
                        label(row.getAlpha3Code(), row.getChineseName(), row.getEnglishName()),
                        row.getChineseName(),
                        row.getEnglishName(),
                        null,
                        null))
                .toList());
        result.setCurrencies(isoCurrencyMapper.selectList(Wrappers.<IsoCurrencyDO>lambdaQuery()
                        .eq(IsoCurrencyDO::getDeleted, NOT_DELETED)
                        .eq(IsoCurrencyDO::getStatus, ENABLED)
                        .orderByAsc(IsoCurrencyDO::getAlpha3Code))
                .stream()
                .map(row -> optionItem(row.getAlpha3Code(),
                        currencyLabel(row),
                        row.getChineseName(),
                        row.getEnglishName(),
                        row.getFractionDigits(),
                        row.getMinimumAmount()))
                .toList());
        return result;
    }

    /**
     * 分页查询商户基础资料。
     *
     * @param request 查询条件
     * @return 商户分页结果
     */
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
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
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public AdminMerchantInfoDTO getMerchant(Long id) {
        return toDTO(requireMerchantById(id));
    }

    /**
     * 新增商户资料。
     *
     * @param request 保存请求
     * @return 商户详情
     */
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantInfoDTO createMerchant(AdminMerchantSaveRequest request) {
        String merchantId = generateUniqueMerchantId();
        LocalDateTime now = LocalDateTime.now();
        BaseMerchantInfoDO row = new BaseMerchantInfoDO();
        row.setMerchantId(merchantId);
        merge(row, request);
        row.setGmtCreate(now);
        row.setGmtModified(now);
        row.setDeleted(NOT_DELETED);
        merchantInfoMapper.insert(row);
        return toDTO(row);
    }

    /**
     * 更新商户资料。
     *
     * @param id      商户主键
     * @param request 保存请求
     * @return 商户详情
     */
    /**
     * 更新收单支付数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantInfoDTO updateMerchant(Long id, AdminMerchantSaveRequest request) {
        BaseMerchantInfoDO row = requireMerchantById(id);
        String newMerchantId = normalizeMerchantId(request.getMerchantId());
        if (!row.getMerchantId().equals(newMerchantId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户号创建后不允许修改");
        }
        merge(row, request);
        row.setGmtModified(LocalDateTime.now());
        merchantInfoMapper.updateById(row);
        return toDTO(row);
    }

    /**
     * 更新商户状态。
     *
     * @param id             商户主键
     * @param merchantStatus 商户状态
     * @return 商户详情
     */
    /**
     * 更新收单支付数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param merchantStatus 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantInfoDTO updateStatus(Long id, Integer merchantStatus) {
        BaseMerchantInfoDO row = requireMerchantById(id);
        validateStatus(merchantStatus);
        row.setMerchantStatus(merchantStatus);
        row.setGmtModified(LocalDateTime.now());
        merchantInfoMapper.updateById(row);
        return toDTO(row);
    }

    /**
     * 一次性初始化商户的 JWT、平台请求体和响应密钥材料。
     *
     * @param merchantId 商户号
     * @return 一次性安全材料
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantSecurityMaterialDTO provisionSecurityMaterial(String merchantId) {
        BaseMerchantInfoDO merchant = requireMerchantByMerchantId(merchantId);
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
        return dto;
    }

    /**
     * 查询商户当前全部密钥概览。
     *
     * @param merchantId 商户号
     * @return 密钥集合
     */
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantSecurityMaterialDTO rotateJwtKey(String merchantId) {
        BaseMerchantInfoDO merchant = requireMerchantByMerchantId(merchantId);
        MerchantJwtKey jwtKey = rotateJwtKeyInternal(merchant.getMerchantId());
        AdminMerchantSecurityMaterialDTO dto = baseMaterial(merchant);
        dto.setMerchantKey(jwtKey.merchantKey());
        dto.setMerchantKeyMasked(mask(jwtKey.merchantKey()));
        dto.setJwtAlgorithm(jwtKey.algorithm());
        dto.setJwtExpiresSeconds(jwtKey.expiresSeconds());
        dto.setOneTimeSecret(true);
        return dto;
    }

    /**
     * 轮换平台请求体加密密钥。
     *
     * @param merchantId 商户号
     * @return 最新安全材料
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantSecurityMaterialDTO rotatePlatformPayloadKey(String merchantId) {
        BaseMerchantInfoDO merchant = requireMerchantByMerchantId(merchantId);
        RsaKeyMaterial platformKey = rotatePlatformKeyInternal(merchant.getMerchantId());
        AdminMerchantSecurityMaterialDTO dto = baseMaterial(merchant);
        dto.setPlatformPublicKeyX509Base64(platformKey.publicKeyX509Base64());
        dto.setOneTimeSecret(false);
        return dto;
    }

    /**
     * 轮换商户响应密钥对。
     *
     * @param merchantId 商户号
     * @return 最新安全材料
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantSecurityMaterialDTO rotateMerchantResponseKey(String merchantId) {
        BaseMerchantInfoDO merchant = requireMerchantByMerchantId(merchantId);
        RsaKeyMaterial responseKey = rotateResponseKeyInternal(merchant.getMerchantId());
        AdminMerchantSecurityMaterialDTO dto = baseMaterial(merchant);
        dto.setMerchantResponsePublicKeyX509Base64(responseKey.publicKeyX509Base64());
        dto.setMerchantResponsePrivateKeyPkcs8Base64(responseKey.privateKeyPkcs8Base64());
        dto.setOneTimeSecret(true);
        return dto;
    }

    /**
     * 更新商户自维护的响应公钥材料。
     *
     * @param merchantId 商户号
     * @param request    公钥更新请求
     * @return 商户详情
     */
    /**
     * 更新收单支付数据，保持已有记录、状态和审计字段的一致性。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantInfoDTO updateMerchantResponseKey(String merchantId, AdminMerchantResponseKeyRequest request) {
        BaseMerchantInfoDO merchant = requireMerchantByMerchantId(merchantId);
        String publicKey = normalizeBase64(request.getPublicKeyX509Base64(), "响应公钥格式不正确");
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
        String billingDescriptor = trimRequiredAscii(request.getBillingDescriptor(), "账单描述仅支持英文、数字、空格及常见英文符号");
        row.setMerchantName(merchantName);
        row.setBillingDescriptor(billingDescriptor);
        row.setMerchantShortName(request.getMerchantShortName().trim());
        row.setMerchantCategoryCode(request.getMerchantCategoryCode().trim());
        row.setCountryCode(trimUpper(request.getCountryCode()));
        row.setRegionCode(trimToNull(request.getRegionCode()));
        row.setCity(trimToNull(request.getCity()));
        row.setAddressLine(trimToNull(request.getAddressLine()));
        row.setPostalCode(trimToNull(request.getPostalCode()));
        row.setContactName(trimToNull(request.getContactName()));
        row.setContactEmail(request.getContactEmail().trim());
        row.setContactPhone(trimToNull(request.getContactPhone()));
        row.setSettlementCurrency(trimUpper(request.getSettlementCurrency()));
        row.setTimezone(request.getTimezone().trim());
        row.setMerchantStatus(request.getMerchantStatus());
        row.setRiskLevel(request.getRiskLevel() == null ? DEFAULT_RISK_LEVEL : request.getRiskLevel());
        validateStatus(row.getMerchantStatus());
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
    private List<AdminMerchantFormOptionsDTO.OptionNode> buildMccOptions() {
        List<MccEntities.BaseMccLevel1DO> level1Rows = mccLevel1Mapper.selectList(Wrappers.<MccEntities.BaseMccLevel1DO>lambdaQuery()
                .eq(MccEntities.BaseMccLevel1DO::getDeleted, (long) NOT_DELETED)
                .eq(MccEntities.BaseMccLevel1DO::getStatus, ENABLED)
                .orderByAsc(MccEntities.BaseMccLevel1DO::getSortNo)
                .orderByAsc(MccEntities.BaseMccLevel1DO::getLevel1Code));
        List<MccEntities.BaseMccLevel2DO> level2Rows = mccLevel2Mapper.selectList(Wrappers.<MccEntities.BaseMccLevel2DO>lambdaQuery()
                .eq(MccEntities.BaseMccLevel2DO::getDeleted, (long) NOT_DELETED)
                .eq(MccEntities.BaseMccLevel2DO::getStatus, ENABLED)
                .orderByAsc(MccEntities.BaseMccLevel2DO::getSortNo)
                .orderByAsc(MccEntities.BaseMccLevel2DO::getLevel2Code));
        List<MccEntities.BaseMccCodeDO> codeRows = mccCodeMapper.selectList(Wrappers.<MccEntities.BaseMccCodeDO>lambdaQuery()
                .eq(MccEntities.BaseMccCodeDO::getDeleted, (long) NOT_DELETED)
                .eq(MccEntities.BaseMccCodeDO::getStatus, ENABLED)
                .orderByAsc(MccEntities.BaseMccCodeDO::getMccCode));

        Map<Long, AdminMerchantFormOptionsDTO.OptionNode> level1Options = new LinkedHashMap<>();
        level1Rows.forEach(row -> level1Options.put(row.getId(),
                optionNode(MCC_LEVEL1_VALUE_PREFIX + row.getId(), label(row.getLevel1Code(), row.getNameCn(), row.getNameEn()), row.getNameCn(), row.getNameEn())));

        Map<Long, AdminMerchantFormOptionsDTO.OptionNode> level2Options = new LinkedHashMap<>();
        level2Rows.forEach(row -> {
            AdminMerchantFormOptionsDTO.OptionNode parent = level1Options.get(row.getLevel1Id());
            if (parent == null) {
                return;
            }
            AdminMerchantFormOptionsDTO.OptionNode node = optionNode(MCC_LEVEL2_VALUE_PREFIX + row.getId(),
                    label(row.getLevel2Code(), row.getNameCn(), row.getNameEn()),
                    row.getNameCn(),
                    row.getNameEn());
            level2Options.put(row.getId(), node);
            parent.getChildren().add(node);
        });

        codeRows.forEach(row -> {
            AdminMerchantFormOptionsDTO.OptionNode parent = level2Options.get(row.getLevel2Id());
            if (parent == null) {
                return;
            }
            parent.getChildren().add(optionNode(row.getMccCode(), label(row.getMccCode(), row.getNameCn(), row.getNameEn()), row.getNameCn(), row.getNameEn()));
        });
        return new ArrayList<>(level1Options.values());
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
    private String currencyLabel(IsoCurrencyDO row) {
        StringBuilder builder = new StringBuilder(label(row.getAlpha3Code(), row.getChineseName(), row.getEnglishName()));
        if (row.getFractionDigits() != null) {
            builder.append("，辅币位：").append(row.getFractionDigits());
        }
        if (row.getMinimumAmount() != null) {
            builder.append("，最小金额：").append(row.getMinimumAmount().stripTrailingZeros().toPlainString());
        }
        return builder.toString();
    }

    private AdminMerchantInfoDTO toDTO(BaseMerchantInfoDO row) {
        AdminMerchantInfoDTO dto = new AdminMerchantInfoDTO();
        dto.setId(row.getId());
        dto.setMerchantId(row.getMerchantId());
        dto.setMerchantName(row.getMerchantName());
        dto.setMerchantShortName(row.getMerchantShortName());
        dto.setMerchantStatus(row.getMerchantStatus());
        dto.setMerchantCategoryCode(row.getMerchantCategoryCode());
        dto.setCountryCode(row.getCountryCode());
        dto.setRegionCode(row.getRegionCode());
        dto.setCity(row.getCity());
        dto.setAddressLine(row.getAddressLine());
        dto.setBillingDescriptor(row.getBillingDescriptor());
        dto.setPostalCode(row.getPostalCode());
        dto.setContactName(row.getContactName());
        dto.setContactEmail(row.getContactEmail());
        dto.setContactPhone(row.getContactPhone());
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
        return value == null ? null : value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
