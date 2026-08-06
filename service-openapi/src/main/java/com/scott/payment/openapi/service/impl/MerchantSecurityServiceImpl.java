package com.scott.payment.openapi.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantKeyMetadataCacheService;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.MerchantOpenApiCredential;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.MerchantJwtKey;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.RsaKeyMaterial;
import com.scott.payment.openapi.dto.security.MerchantInfoDTO;
import com.scott.payment.openapi.dto.security.MerchantKeyRevisionDTO;
import com.scott.payment.openapi.dto.security.MerchantSecurityMaterialDTO;
import com.scott.payment.openapi.dto.security.MerchantSecuritySeedDTO;
import com.scott.payment.openapi.dto.security.ServerSecurityMaterialDTO;
import com.scott.payment.openapi.entity.MerchantInfoDO;
import com.scott.payment.openapi.entity.MerchantJwtKeyDO;
import com.scott.payment.openapi.entity.MerchantResponseKeyDO;
import com.scott.payment.openapi.entity.PlatformPayloadKeyDO;
import com.scott.payment.openapi.enums.MerchantRiskLevelEnum;
import com.scott.payment.openapi.enums.MerchantStatusEnum;
import com.scott.payment.openapi.mapper.MerchantInfoMapper;
import com.scott.payment.openapi.mapper.MerchantJwtKeyMapper;
import com.scott.payment.openapi.mapper.MerchantResponseKeyMapper;
import com.scott.payment.openapi.mapper.PlatformPayloadKeyMapper;
import com.scott.payment.openapi.security.OpenApiMerchantSecretCache;
import com.scott.payment.openapi.service.MerchantSecurityService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Primary
@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSecurityServiceImpl
 * @date : 2026-05-30 09:37
 * @email : scott_x@163.com
 * @description : Merchant Security Service Impl 服务实现，位于 商户开放接口服务，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。
 * @status : create
 */
public class MerchantSecurityServiceImpl implements MerchantSecurityService {

    /**
     * 当前 JWT 标准算法。
     */
    private static final String JWT_ALGORITHM = "HS256";

    /**
     * 当前请求体加密算法说明。
     */
    private static final String PAYLOAD_ALGORITHM = "RSA-OAEP-256+A256GCM";

    /**
     * 密钥迭代记录中用于标识商户 JWT 密钥的类型。
     */
    private static final String KEY_TYPE_JWT = "JWT_HS256";

    /**
     * 密钥迭代记录中用于标识商户响应加密公钥的类型。
     */
    private static final String KEY_TYPE_RESPONSE = "RESPONSE_RSA";

    /**
     * 默认 JWT 最大有效期，单位秒。
     */
    private static final long JWT_EXPIRES_SECONDS = 180L;

    /**
     * 默认 RSA 密钥位数。
     */
    private static final int RSA_KEY_SIZE = 2048;

    /**
     * 数据库未删除标识。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 数据库已启用标识。
     */
    private static final int ENABLED = 1;

    /**
     * 默认商户业务时区。
     */
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    /**
     * 默认结算币种。
     */
    private static final String DEFAULT_SETTLEMENT_CURRENCY = "USD";

    /**
     * 默认商户密钥版本。
     */
    private static final String DEFAULT_JWT_KEY_VERSION = "jwt-v1";

    /**
     * SDK live 联调专属商户号。该服务的初始化/轮换能力主要用于测试开户链路，不允许自动化流程改写此商户密钥。
     */
    private static final String SDK_LIVE_MERCHANT_ID = "200045";

    /**
     * 商户基础信息 Mapper。
     */
    private final MerchantInfoMapper merchantInfoMapper;

    /**
     * 商户 JWT 密钥 Mapper。
     */
    private final MerchantJwtKeyMapper merchantJwtKeyMapper;

    /**
     * 平台请求体 RSA 密钥 Mapper。
     */
    private final PlatformPayloadKeyMapper platformPayloadKeyMapper;

    /**
     * 商户响应公钥 Mapper。
     */
    private final MerchantResponseKeyMapper merchantResponseKeyMapper;

    /**
     * OpenAPI 报文加解密工具。
     */
    private final OpenApiPayloadCrypto payloadCrypto;

    /**
     * OpenAPI 密钥材料生成入口。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;

    /** Admin、Merchant Portal、OpenAPI 与支付服务共用的完整商户资料缓存；密钥材料不在该缓存中。 */
    private final MerchantRuntimeProfileCacheService merchantRuntimeProfileCacheService;

    /** 只保存密钥 ID、算法、时间和 revision 的永久缓存；不保存任何密钥正文。 */
    private final MerchantKeyMetadataCacheService merchantKeyMetadataCacheService;

    /** 在写事务提交前建立 pending 门禁和 Outbox，可靠删除跨服务永久旧缓存。 */
    private final ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;

    /** OpenAPI 单实例短时敏感密钥缓存；实际密钥材料不会写入 Redis。 */
    private final OpenApiMerchantSecretCache merchantSecretCache;

    /**
     * 创建商户安全材料服务实现。
     *
     * @param merchantInfoMapper        商户基础信息 Mapper
     * @param merchantJwtKeyMapper      商户 JWT 密钥 Mapper
     * @param platformPayloadKeyMapper  平台请求体 RSA 密钥 Mapper
     * @param merchantResponseKeyMapper 商户响应公钥 Mapper
     * @param payloadCrypto             OpenAPI 报文加解密工具
     * @param keyMaterialFactory        OpenAPI 密钥材料生成入口
     * @param merchantRuntimeProfileCacheService 共享商户运行资料缓存
     * @param merchantKeyMetadataCacheService 非敏感密钥版本永久缓存
     * @param cacheInvalidationCoordinator 永久缓存可靠失效协调器
     * @param merchantSecretCache OpenAPI 单实例短时敏感密钥缓存
     */
    public MerchantSecurityServiceImpl(MerchantInfoMapper merchantInfoMapper,
                                       MerchantJwtKeyMapper merchantJwtKeyMapper,
                                       PlatformPayloadKeyMapper platformPayloadKeyMapper,
                                       MerchantResponseKeyMapper merchantResponseKeyMapper,
                                       OpenApiPayloadCrypto payloadCrypto,
                                       OpenApiKeyMaterialFactory keyMaterialFactory,
                                       MerchantRuntimeProfileCacheService merchantRuntimeProfileCacheService,
                                       MerchantKeyMetadataCacheService merchantKeyMetadataCacheService,
                                       ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator,
                                       OpenApiMerchantSecretCache merchantSecretCache) {
        this.merchantInfoMapper = merchantInfoMapper;
        this.merchantJwtKeyMapper = merchantJwtKeyMapper;
        this.platformPayloadKeyMapper = platformPayloadKeyMapper;
        this.merchantResponseKeyMapper = merchantResponseKeyMapper;
        this.payloadCrypto = payloadCrypto;
        this.keyMaterialFactory = keyMaterialFactory;
        this.merchantRuntimeProfileCacheService = merchantRuntimeProfileCacheService;
        this.merchantKeyMetadataCacheService = merchantKeyMetadataCacheService;
        this.cacheInvalidationCoordinator = cacheInvalidationCoordinator;
        this.merchantSecretCache = merchantSecretCache;
    }

    /**
     * 初始化商户安全材料并写入主库。
     *
     * @param seedDTO 商户开户与测试初始化入参
     * @return 商户侧需要保存的安全材料
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public MerchantSecurityMaterialDTO provisionMerchantSecurityMaterial(MerchantSecuritySeedDTO seedDTO) {
        validateSeed(seedDTO);
        rejectSdkLiveMerchantMutation(seedDTO.getMerchantId());
        LocalDateTime now = LocalDateTime.now();
        RsaKeyMaterial platformPayloadKey = keyMaterialFactory.generatePlatformPayloadRsaKey(seedDTO.getMerchantId());
        RsaKeyMaterial merchantResponseKey = keyMaterialFactory.generateMerchantResponseRsaKey(seedDTO.getMerchantId());
        MerchantOpenApiCredential merchantCredential = keyMaterialFactory.generateMerchantCredential(
                seedDTO.getMerchantId(),
                platformPayloadKey,
                merchantResponseKey
        );

        prepareSecurityMaterialCacheInvalidation(seedDTO.getMerchantId(), true);
        upsertMerchantInfo(seedDTO, now);
        upsertMerchantJwtKey(seedDTO.getMerchantId(), merchantCredential.merchantKey(), now);
        upsertPlatformPayloadKey(seedDTO.getMerchantId(), platformPayloadKey, now);
        upsertMerchantResponseKey(seedDTO.getMerchantId(), merchantResponseKey.publicKeyX509Base64(), now);
        refreshSecurityMaterialCaches(seedDTO.getMerchantId(), true);

        MerchantSecurityMaterialDTO materialDTO = new MerchantSecurityMaterialDTO();
        materialDTO.setMerchantId(seedDTO.getMerchantId());
        materialDTO.setMerchantName(seedDTO.getMerchantName());
        materialDTO.setMerchantKey(merchantCredential.merchantKey());
        materialDTO.setJwtAlgorithm(JWT_ALGORITHM);
        materialDTO.setJwtExpiresSeconds(JWT_EXPIRES_SECONDS);
        materialDTO.setPlatformPublicKeyX509Base64(platformPayloadKey.publicKeyX509Base64());
        materialDTO.setPlatformPublicKeyPem(platformPayloadKey.publicKeyPem());
        materialDTO.setMerchantResponsePublicKeyX509Base64(merchantCredential.merchantResponsePublicKeyX509Base64());
        materialDTO.setMerchantResponsePrivateKeyPkcs8Base64(merchantCredential.merchantResponsePrivateKeyPkcs8Base64());
        materialDTO.setMerchantResponsePrivateKeyPem(merchantCredential.merchantResponsePrivateKeyPem());
        return materialDTO;
    }

    /**
     * 查询商户侧默认需要保存的对接密钥材料。
     * <p>
     * 这里会返回商户生成 JWT 所需的 merchantKey 和商户加密请求体所需的平台公钥，
     * 不返回平台私钥，也不返回商户响应私钥，避免把服务端或商户自持私钥误暴露给调用方。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户侧默认密钥材料
     */
    @Override
    @DS(DataSourceName.MASTER)
    public MerchantSecurityMaterialDTO getMerchantClientSecurityMaterial(String merchantId) {
        MerchantInfoDO merchantInfoDO = getActiveMerchant(merchantId);
        MerchantJwtKeyDO jwtKeyDO = selectActiveMerchantJwtKey(merchantId);
        PlatformPayloadKeyDO platformKeyDO = selectActivePlatformPayloadKey(merchantId);
        MerchantResponseKeyDO responseKeyDO = selectActiveMerchantResponseKey(merchantId);
        return toMerchantClientSecurityMaterial(merchantInfoDO, jwtKeyDO, platformKeyDO, responseKeyDO);
    }

    /**
     * 查询服务端验签、解密和响应加密所需的内部密钥材料。
     * <p>
     * 该方法只允许服务端内部测试或运维诊断使用，返回值包含平台私钥和 merchantKey，
     * 调用方必须使用脱敏日志，不允许把真实密钥写入业务日志或接口响应。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 服务端内部密钥材料
     */
    @Override
    @DS(DataSourceName.MASTER)
    public ServerSecurityMaterialDTO getServerSecurityMaterial(String merchantId) {
        MerchantInfoDO merchantInfoDO = getActiveMerchant(merchantId);
        MerchantJwtKeyDO jwtKeyDO = selectActiveMerchantJwtKey(merchantId);
        PlatformPayloadKeyDO platformKeyDO = selectActivePlatformPayloadKey(merchantId);
        MerchantResponseKeyDO responseKeyDO = selectActiveMerchantResponseKey(merchantId);
        return toServerSecurityMaterial(merchantInfoDO, jwtKeyDO, platformKeyDO, responseKeyDO);
    }

    /**
     * 查询所有未删除商户的基础资料。
     *
     * @return 商户基础资料列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<MerchantInfoDTO> listMerchantInfos() {
        return merchantInfoMapper.selectList(
                        Wrappers.<MerchantInfoDO>lambdaQuery()
                                .eq(MerchantInfoDO::getDeleted, NOT_DELETED)
                                .orderByAsc(MerchantInfoDO::getMerchantId)
                )
                .stream()
                .map(this::toMerchantInfoDTO)
                .toList();
    }

    /**
     * 查询商户所有可见的密钥迭代记录。
     * <p>
     * 当前返回 JWT 签名密钥版本和响应加密公钥版本；平台请求体私钥是平台级材料，
     * 不按商户轮换，因此不放在商户密钥迭代列表里。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户密钥迭代记录
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<MerchantKeyRevisionDTO> listMerchantKeyRevisions(String merchantId) {
        validateMerchantId(merchantId);
        List<MerchantKeyRevisionDTO> revisionList = new ArrayList<>();
        selectMerchantJwtKeyList(merchantId).stream()
                .map(this::toJwtKeyRevisionDTO)
                .forEach(revisionList::add);
        selectMerchantResponseKeyList(merchantId).stream()
                .map(this::toResponseKeyRevisionDTO)
                .forEach(revisionList::add);
        return revisionList;
    }

    /**
     * 为商户生成一个新的 JWT HS256 签名密钥版本。
     * <p>
     * 该方法模拟商户密钥轮换流程：新版本写入后会成为默认生效密钥，
     * 老版本保留用于迭代记录查询和灰度窗口内的审计追踪。
     *
     * @param merchantId  支付框架颁发的商户号
     * @param keyVersion  新密钥版本号
     * @return 新密钥迭代记录
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public MerchantKeyRevisionDTO rotateMerchantJwtKey(String merchantId, String keyVersion) {
        validateMerchantId(merchantId);
        rejectSdkLiveMerchantMutation(merchantId);
        if (!StringUtils.hasText(keyVersion)) {
            throw new ApiException(ApiResultEnum.PARAM_MISSING, "keyVersion");
        }
        getActiveMerchant(merchantId);
        LocalDateTime now = LocalDateTime.now();
        MerchantJwtKey merchantJwtKey = keyMaterialFactory.generateMerchantJwtKey(merchantId);
        MerchantJwtKeyDO entity = new MerchantJwtKeyDO();
        entity.setMerchantId(merchantId);
        entity.setKeyVersion(keyVersion);
        entity.setMerchantKey(merchantJwtKey.merchantKey());
        entity.setAlgorithm(JWT_ALGORITHM);
        entity.setExpiresSeconds(JWT_EXPIRES_SECONDS);
        entity.setEnabled(ENABLED);
        entity.setEffectiveTime(now);
        entity.setDeleted(NOT_DELETED);
        prepareSecurityMaterialCacheInvalidation(merchantId, false);
        upsertMerchantJwtKey(entity, now);
        refreshSecurityMaterialCaches(merchantId, false);
        return toJwtKeyRevisionDTO(entity);
    }

    /**
     * 查询商户 JWT HS256 签名密钥。
     *
     * <p>先校验共享商户状态，再按 Redis 非敏感 revision 读取进程内短时密钥材料；
     * 本地未命中时固定回源主库，JWT Secret 不进入 Redis。</p>
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户 JWT 签名密钥
     */
    @Override
    @DS(DataSourceName.MASTER)
    public String getMerchantKey(String merchantId) {
        validateMerchantId(merchantId);
        MerchantInfoDO merchantInfoDO = getActiveMerchant(merchantId);
        return merchantSecretCache.getMerchantKey(merchantInfoDO.getMerchantId());
    }

    /**
     * 查询商户独立的平台 RSA 私钥。
     *
     * <p>按 Redis 非敏感 revision 读取进程内短时密钥材料，本地未命中时固定回源主库，
     * RSA 私钥不进入 Redis。</p>
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 平台 RSA 私钥
     */
    @Override
    @DS(DataSourceName.MASTER)
    public PrivateKey getPlatformPrivateKey(String merchantId) {
        validateMerchantId(merchantId);
        return merchantSecretCache.getPlatformPrivateKey(merchantId.trim());
    }

    /**
     * 查询商户独立的平台 RSA 公钥，确保加密测试与服务端解密使用同一版本。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 平台 RSA 公钥
     */
    @Override
    @DS(DataSourceName.MASTER)
    public PublicKey getPlatformPublicKey(String merchantId) {
        validateMerchantId(merchantId);
        return merchantSecretCache.getPlatformPublicKey(merchantId.trim());
    }

    /**
     * 从共享永久缓存查询可交易商户基础信息，缓存未命中、失效 pending 或 Redis 异常时回源主库。
     *
     * <p>共享 Value 是 {@code base_merchant_info} 的完整资料读模型，包含受保护的联系人和地址字段，
     * 但 OpenAPI 适配结果只提取交易鉴权所需字段。JWT Secret、RSA 私钥等密钥材料始终不进入 Redis。
     * 返回旧 OpenAPI 实体类型只用于兼容既有服务契约，不代表重新查询 OpenAPI 私有商户表。</p>
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户基础信息
     */
    @Override
    @DS(DataSourceName.MASTER)
    public MerchantInfoDO getActiveMerchant(String merchantId) {
        validateMerchantId(merchantId);
        MerchantRuntimeProfile runtimeProfile =
                merchantRuntimeProfileCacheService.findRuntimeProfile(merchantId.trim());
        if (runtimeProfile == null
                || !Integer.valueOf(MerchantStatusEnum.ACTIVE.getCode())
                .equals(runtimeProfile.getMerchantStatus())) {
            throw new ApiException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND, merchantId);
        }
        return toMerchantInfoDO(runtimeProfile);
    }

    /**
     * 查询商户响应 RSA 公钥，避免密钥轮换后的复制延迟导致响应加密版本回退。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户响应 RSA 公钥
     */
    @Override
    @DS(DataSourceName.MASTER)
    public PublicKey getMerchantResponsePublicKey(String merchantId) {
        validateMerchantId(merchantId);
        return merchantSecretCache.getMerchantResponsePublicKey(merchantId.trim());
    }

    /**
     * 将共享完整商户资料适配为既有 OpenAPI 商户实体契约。
     *
     * <p>适配结果故意不填充联系人和详细地址，避免 OpenAPI 交易鉴权链路传播不需要的受保护资料。</p>
     *
     * @param profile 已通过状态校验的共享商户运行资料
     * @return 仅包含 OpenAPI 交易鉴权所需字段的兼容实体
     */
    private MerchantInfoDO toMerchantInfoDO(MerchantRuntimeProfile profile) {
        MerchantInfoDO entity = new MerchantInfoDO();
        entity.setId(profile.getId());
        entity.setMerchantId(profile.getMerchantId());
        entity.setMerchantName(profile.getMerchantName());
        entity.setBillingDescriptor(profile.getBillingDescriptor());
        entity.setMerchantShortName(profile.getMerchantShortName());
        entity.setMerchantStatus(profile.getMerchantStatus());
        entity.setDefaultLocale(profile.getDefaultLocale());
        entity.setMerchantCategoryCode(profile.getMerchantCategoryCode());
        entity.setCountryCode(profile.getCountryCode());
        entity.setRegionCode(profile.getRegionCode());
        entity.setCity(profile.getCity());
        entity.setPostalCode(profile.getPostalCode());
        entity.setSettlementCurrency(profile.getSettlementCurrency());
        entity.setTimezone(profile.getTimezone());
        entity.setRiskLevel(profile.getRiskLevel());
        entity.setGmtModified(profile.getGmtModified());
        entity.setDeleted(NOT_DELETED);
        return entity;
    }

    /**
     * 在商户或密钥写入前登记永久缓存失效门禁和同事务 Outbox 意图。
     *
     * <p>pending 门禁阻止并发请求在数据库提交与 Redis 更新之间继续使用旧值；事务提交后中继先删除
     * 旧永久缓存，随后 transaction-aware CachePut 写入本事务准备的新快照。回滚时协调器释放门禁。</p>
     *
     * @param merchantId 已校验的商户号
     * @param includeRuntimeProfile 是否同时变更完整商户资料
     */
    private void prepareSecurityMaterialCacheInvalidation(String merchantId,
                                                          boolean includeRuntimeProfile) {
        if (includeRuntimeProfile) {
            cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE, merchantId);
        }
        cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_KEY_METADATA, merchantId);
    }

    /**
     * 在写事务内准备提交后的最新永久快照，并清除当前实例的旧敏感材料。
     *
     * <p>两个共享缓存的 CachePut 由 transaction-aware Redis Cache 延迟到数据库提交后执行；
     * JVM 清理可立即执行，因为事务回滚时多一次主库读取不会暴露旧密钥，也不会改变数据库事实。</p>
     *
     * @param merchantId 已完成主库写入的商户号
     * @param includeRuntimeProfile 是否同时刷新完整商户资料
     */
    private void refreshSecurityMaterialCaches(String merchantId,
                                               boolean includeRuntimeProfile) {
        merchantSecretCache.evictMerchant(merchantId);
        if (includeRuntimeProfile) {
            merchantRuntimeProfileCacheService.refreshRuntimeProfile(merchantId);
        }
        merchantKeyMetadataCacheService.refreshKeyMetadata(merchantId);
    }

    /**
     * 根据开户入参组装商户基础信息实体。
     *
     * @param seedDTO 商户开户与测试初始化入参
     * @param now     当前写库时间
     */
    private void upsertMerchantInfo(MerchantSecuritySeedDTO seedDTO, LocalDateTime now) {
        MerchantInfoDO entity = new MerchantInfoDO();
        entity.setMerchantId(seedDTO.getMerchantId());
        entity.setMerchantName(seedDTO.getMerchantName());
        entity.setBillingDescriptor(seedDTO.getMerchantName());
        entity.setMerchantShortName(defaultIfBlank(seedDTO.getMerchantShortName(), seedDTO.getMerchantName()));
        entity.setMerchantStatus(MerchantStatusEnum.ACTIVE.getCode());
        entity.setDefaultLocale(com.scott.payment.component.db.auth.support.MerchantLocaleSupport.CHINESE);
        entity.setMerchantCategoryCode(defaultIfBlank(seedDTO.getMerchantCategoryCode(), "5311"));
        entity.setCountryCode(defaultIfBlank(seedDTO.getCountryCode(), "USA"));
        entity.setRegionCode(defaultIfBlank(seedDTO.getRegionCode(), "CA"));
        entity.setCity(defaultIfBlank(seedDTO.getCity(), "San Jose"));
        entity.setAddressLine(defaultIfBlank(seedDTO.getAddressLine(), "Default acquiring merchant address"));
        entity.setPostalCode("95113");
        entity.setContactName(defaultIfBlank(seedDTO.getMerchantName(), "Merchant Contact"));
        entity.setContactEmail(defaultIfBlank(seedDTO.getContactEmail(), "merchant@example.com"));
        entity.setContactPhone(defaultIfBlank(seedDTO.getContactPhone(), "+1-408-555-0100"));
        entity.setSettlementCurrency(defaultIfBlank(seedDTO.getSettlementCurrency(), DEFAULT_SETTLEMENT_CURRENCY));
        entity.setTimezone(defaultIfBlank(seedDTO.getTimezone(), DEFAULT_TIMEZONE));
        entity.setRiskLevel(defaultIfNull(seedDTO.getRiskLevel(), MerchantRiskLevelEnum.NORMAL.getCode()));
        entity.setDeleted(NOT_DELETED);
        upsertMerchantInfo(entity, now);
    }

    /**
     * 根据商户号和新生成的明文 merchantKey 组装 JWT 密钥实体。
     *
     * @param merchantId  支付框架颁发的商户号
     * @param merchantKey 商户 JWT HS256 签名密钥
     * @param now         当前写库时间
     */
    private void upsertMerchantJwtKey(String merchantId, String merchantKey, LocalDateTime now) {
        MerchantJwtKeyDO entity = new MerchantJwtKeyDO();
        entity.setMerchantId(merchantId);
        entity.setKeyVersion(DEFAULT_JWT_KEY_VERSION);
        entity.setMerchantKey(merchantKey);
        entity.setAlgorithm(JWT_ALGORITHM);
        entity.setExpiresSeconds(JWT_EXPIRES_SECONDS);
        entity.setEnabled(ENABLED);
        entity.setEffectiveTime(now);
        entity.setDeleted(NOT_DELETED);
        upsertMerchantJwtKey(entity, now);
    }

    /**
     * 将平台请求体 RSA 密钥写入平台密钥实体。
     *
     * @param merchantId          支付框架颁发的商户号
     * @param platformPayloadKey  平台请求体 RSA 密钥材料
     * @param now                当前写库时间
     */
    private void upsertPlatformPayloadKey(String merchantId, RsaKeyMaterial platformPayloadKey, LocalDateTime now) {
        PlatformPayloadKeyDO entity = new PlatformPayloadKeyDO();
        entity.setMerchantId(merchantId);
        entity.setPublicKeyX509Base64(platformPayloadKey.publicKeyX509Base64());
        entity.setPrivateKeyPkcs8Base64(platformPayloadKey.privateKeyPkcs8Base64());
        entity.setAlgorithm(PAYLOAD_ALGORITHM);
        entity.setKeySize(platformPayloadKey.keySize());
        entity.setEnabled(ENABLED);
        entity.setDeleted(NOT_DELETED);
        upsertPlatformPayloadKey(entity, now);
    }

    /**
     * 将商户响应公钥写入响应加密密钥实体。
     *
     * @param merchantId          支付框架颁发的商户号
     * @param publicKeyX509Base64 商户响应 X.509 DER Base64 公钥
     * @param now                 当前写库时间
     */
    private void upsertMerchantResponseKey(String merchantId,
                                           String publicKeyX509Base64,
                                           LocalDateTime now) {
        MerchantResponseKeyDO entity = new MerchantResponseKeyDO();
        entity.setMerchantId(merchantId);
        entity.setPublicKeyX509Base64(publicKeyX509Base64);
        entity.setAlgorithm(PAYLOAD_ALGORITHM);
        entity.setKeySize(RSA_KEY_SIZE);
        entity.setEnabled(ENABLED);
        entity.setDeleted(NOT_DELETED);
        upsertMerchantResponseKey(entity, now);
    }

    /**
     * 按 merchantId 幂等写入商户基础信息。
     *
     * @param entity 商户基础信息实体
     * @param now    当前写库时间
     */
    private void upsertMerchantInfo(MerchantInfoDO entity, LocalDateTime now) {
        MerchantInfoDO existing = merchantInfoMapper.selectOne(
                Wrappers.<MerchantInfoDO>lambdaQuery()
                        .eq(MerchantInfoDO::getMerchantId, entity.getMerchantId())
                        .last("LIMIT 1")
        );
        saveOrUpdate(entity, existing == null ? null : existing.getId(), now, merchantInfoMapper::insert, merchantInfoMapper::updateById);
    }

    /**
     * 按 merchantId 和 keyVersion 幂等写入商户 JWT 密钥。
     *
     * @param entity 商户 JWT 密钥实体
     * @param now    当前写库时间
     */
    private void upsertMerchantJwtKey(MerchantJwtKeyDO entity, LocalDateTime now) {
        MerchantJwtKeyDO existing = merchantJwtKeyMapper.selectOne(
                Wrappers.<MerchantJwtKeyDO>lambdaQuery()
                        .eq(MerchantJwtKeyDO::getMerchantId, entity.getMerchantId())
                        .eq(MerchantJwtKeyDO::getKeyVersion, entity.getKeyVersion())
                        .last("LIMIT 1")
        );
        saveOrUpdate(entity, existing == null ? null : existing.getId(), now, merchantJwtKeyMapper::insert, merchantJwtKeyMapper::updateById);
    }

    /**
     * 按 merchantId 幂等写入平台请求体 RSA 密钥。
     *
     * @param entity 平台请求体 RSA 密钥实体
     * @param now    当前写库时间
     */
    private void upsertPlatformPayloadKey(PlatformPayloadKeyDO entity, LocalDateTime now) {
        PlatformPayloadKeyDO existing = platformPayloadKeyMapper.selectOne(
                Wrappers.<PlatformPayloadKeyDO>lambdaQuery()
                        .eq(PlatformPayloadKeyDO::getMerchantId, entity.getMerchantId())
                        .last("LIMIT 1")
        );
        saveOrUpdate(entity, existing == null ? null : existing.getId(), now, platformPayloadKeyMapper::insert, platformPayloadKeyMapper::updateById);
    }

    /**
     * 按 merchantId 幂等写入商户响应公钥。
     *
     * @param entity 商户响应公钥实体
     * @param now    当前写库时间
     */
    private void upsertMerchantResponseKey(MerchantResponseKeyDO entity, LocalDateTime now) {
        MerchantResponseKeyDO existing = merchantResponseKeyMapper.selectOne(
                Wrappers.<MerchantResponseKeyDO>lambdaQuery()
                        .eq(MerchantResponseKeyDO::getMerchantId, entity.getMerchantId())
                        .last("LIMIT 1")
        );
        saveOrUpdate(entity, existing == null ? null : existing.getId(), now, merchantResponseKeyMapper::insert, merchantResponseKeyMapper::updateById);
    }

    /**
     * 查询商户当前启用的 JWT 签名密钥。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 当前启用的商户 JWT 密钥实体
     */
    private MerchantJwtKeyDO selectActiveMerchantJwtKey(String merchantId) {
        MerchantJwtKeyDO keyDO = merchantJwtKeyMapper.selectOne(
                Wrappers.<MerchantJwtKeyDO>lambdaQuery()
                        .eq(MerchantJwtKeyDO::getMerchantId, merchantId)
                        .eq(MerchantJwtKeyDO::getAlgorithm, JWT_ALGORITHM)
                        .eq(MerchantJwtKeyDO::getEnabled, ENABLED)
                        .eq(MerchantJwtKeyDO::getDeleted, NOT_DELETED)
                        .orderByDesc(MerchantJwtKeyDO::getEffectiveTime)
                        .last("LIMIT 1")
        );
        if (keyDO == null) {
            throw new ApiException(ApiResultEnum.MERCHANT_SIGNING_KEY_NOT_CONFIGURED);
        }
        return keyDO;
    }

    /**
     * 查询商户历史和当前 JWT 签名密钥。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户 JWT 密钥列表
     */
    private List<MerchantJwtKeyDO> selectMerchantJwtKeyList(String merchantId) {
        return merchantJwtKeyMapper.selectList(
                Wrappers.<MerchantJwtKeyDO>lambdaQuery()
                        .eq(MerchantJwtKeyDO::getMerchantId, merchantId)
                        .eq(MerchantJwtKeyDO::getDeleted, NOT_DELETED)
                        .orderByDesc(MerchantJwtKeyDO::getEffectiveTime)
        );
    }

    /**
     * 查询商户当前启用的平台请求体 RSA 密钥。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 平台请求体 RSA 密钥实体
     */
    private PlatformPayloadKeyDO selectActivePlatformPayloadKey(String merchantId) {
        validateMerchantId(merchantId);
        PlatformPayloadKeyDO keyDO = platformPayloadKeyMapper.selectOne(
                Wrappers.<PlatformPayloadKeyDO>lambdaQuery()
                        .eq(PlatformPayloadKeyDO::getMerchantId, merchantId)
                        .eq(PlatformPayloadKeyDO::getEnabled, ENABLED)
                        .eq(PlatformPayloadKeyDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (keyDO == null) {
            throw new ApiException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND, "platformPayloadKey");
        }
        return keyDO;
    }

    /**
     * 查询商户当前启用的响应公钥。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户响应公钥实体
     */
    private MerchantResponseKeyDO selectActiveMerchantResponseKey(String merchantId) {
        validateMerchantId(merchantId);
        var queryWrapper = Wrappers.<MerchantResponseKeyDO>lambdaQuery()
                .eq(MerchantResponseKeyDO::getMerchantId, merchantId)
                .eq(MerchantResponseKeyDO::getEnabled, ENABLED)
                .eq(MerchantResponseKeyDO::getDeleted, NOT_DELETED)
                .orderByDesc(MerchantResponseKeyDO::getGmtModified)
                .last("LIMIT 1");
        MerchantResponseKeyDO keyDO = merchantResponseKeyMapper.selectOne(queryWrapper);
        if (keyDO == null) {
            throw new ApiException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND, "merchantResponseKey");
        }
        return keyDO;
    }

    /**
     * 查询商户响应公钥历史列表。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户响应公钥列表
     */
    private List<MerchantResponseKeyDO> selectMerchantResponseKeyList(String merchantId) {
        return merchantResponseKeyMapper.selectList(
                Wrappers.<MerchantResponseKeyDO>lambdaQuery()
                        .eq(MerchantResponseKeyDO::getMerchantId, merchantId)
                        .eq(MerchantResponseKeyDO::getDeleted, NOT_DELETED)
                        .orderByDesc(MerchantResponseKeyDO::getGmtModified)
        );
    }

    /**
     * 将数据库实体转换为商户基础信息查询 DTO。
     *
     * @param entity 商户基础信息实体
     * @return 商户基础信息 DTO
     */
    private MerchantInfoDTO toMerchantInfoDTO(MerchantInfoDO entity) {
        MerchantInfoDTO dto = new MerchantInfoDTO();
        dto.setMerchantId(entity.getMerchantId());
        dto.setMerchantName(entity.getMerchantName());
        dto.setBillingDescriptor(entity.getBillingDescriptor());
        dto.setMerchantShortName(entity.getMerchantShortName());
        dto.setMerchantStatus(entity.getMerchantStatus());
        dto.setDefaultLocale(entity.getDefaultLocale());
        dto.setMerchantCategoryCode(entity.getMerchantCategoryCode());
        dto.setCountryCode(entity.getCountryCode());
        dto.setRegionCode(entity.getRegionCode());
        dto.setCity(entity.getCity());
        dto.setAddressLine(entity.getAddressLine());
        dto.setPostalCode(entity.getPostalCode());
        dto.setContactName(entity.getContactName());
        dto.setContactEmail(entity.getContactEmail());
        dto.setContactPhone(entity.getContactPhone());
        dto.setSettlementCurrency(entity.getSettlementCurrency());
        dto.setTimezone(entity.getTimezone());
        dto.setRiskLevel(entity.getRiskLevel());
        return dto;
    }

    /**
     * 将数据库安全材料转换为商户侧可见的密钥 DTO。
     *
     * @param merchantInfoDO 商户基础信息
     * @param jwtKeyDO       商户 JWT 密钥
     * @param platformKeyDO  平台请求体 RSA 公钥
     * @param responseKeyDO  商户响应公钥，可为空
     * @return 商户侧可见密钥材料
     */
    private MerchantSecurityMaterialDTO toMerchantClientSecurityMaterial(MerchantInfoDO merchantInfoDO,
                                                                         MerchantJwtKeyDO jwtKeyDO,
                                                                         PlatformPayloadKeyDO platformKeyDO,
                                                                         MerchantResponseKeyDO responseKeyDO) {
        MerchantSecurityMaterialDTO dto = new MerchantSecurityMaterialDTO();
        dto.setMerchantId(merchantInfoDO.getMerchantId());
        dto.setMerchantName(merchantInfoDO.getMerchantName());
        dto.setMerchantKey(jwtKeyDO.getMerchantKey());
        dto.setJwtAlgorithm(jwtKeyDO.getAlgorithm());
        dto.setJwtExpiresSeconds(jwtKeyDO.getExpiresSeconds());
        dto.setPlatformPublicKeyX509Base64(platformKeyDO.getPublicKeyX509Base64());
        dto.setPlatformPublicKeyPem(payloadCrypto.toPublicKeyPem(platformKeyDO.getPublicKeyX509Base64()));
        if (responseKeyDO != null) {
            dto.setMerchantResponsePublicKeyX509Base64(responseKeyDO.getPublicKeyX509Base64());
        }
        return dto;
    }

    /**
     * 将数据库安全材料转换为服务端内部诊断 DTO。
     *
     * @param merchantInfoDO 商户基础信息
     * @param jwtKeyDO       商户 JWT 密钥
     * @param platformKeyDO  平台请求体 RSA 密钥
     * @param responseKeyDO  商户响应公钥，可为空
     * @return 服务端内部安全材料
     */
    private ServerSecurityMaterialDTO toServerSecurityMaterial(MerchantInfoDO merchantInfoDO,
                                                               MerchantJwtKeyDO jwtKeyDO,
                                                               PlatformPayloadKeyDO platformKeyDO,
                                                               MerchantResponseKeyDO responseKeyDO) {
        ServerSecurityMaterialDTO dto = new ServerSecurityMaterialDTO();
        dto.setMerchantId(merchantInfoDO.getMerchantId());
        dto.setMerchantKey(jwtKeyDO.getMerchantKey());
        dto.setMerchantKeyFingerprint(keyMaterialFactory.fingerprint(jwtKeyDO.getMerchantKey()));
        dto.setJwtAlgorithm(jwtKeyDO.getAlgorithm());
        dto.setJwtExpiresSeconds(jwtKeyDO.getExpiresSeconds());
        dto.setPlatformPublicKeyX509Base64(platformKeyDO.getPublicKeyX509Base64());
        dto.setPlatformPrivateKeyPkcs8Base64(platformKeyDO.getPrivateKeyPkcs8Base64());
        dto.setPlatformKeyFingerprint(keyMaterialFactory.fingerprint(platformKeyDO.getPublicKeyX509Base64()));
        if (responseKeyDO != null) {
            dto.setMerchantResponsePublicKeyX509Base64(responseKeyDO.getPublicKeyX509Base64());
            dto.setMerchantResponseKeyFingerprint(keyMaterialFactory.fingerprint(responseKeyDO.getPublicKeyX509Base64()));
        }
        return dto;
    }

    /**
     * 将 JWT 密钥实体转换为密钥迭代记录。
     *
     * @param entity 商户 JWT 密钥实体
     * @return 密钥迭代记录
     */
    private MerchantKeyRevisionDTO toJwtKeyRevisionDTO(MerchantJwtKeyDO entity) {
        MerchantKeyRevisionDTO dto = new MerchantKeyRevisionDTO();
        dto.setMerchantId(entity.getMerchantId());
        dto.setKeyType(KEY_TYPE_JWT);
        dto.setKeyVersion(entity.getKeyVersion());
        dto.setAlgorithm(entity.getAlgorithm());
        dto.setKeyFingerprint(keyMaterialFactory.fingerprint(entity.getMerchantKey()));
        dto.setEnabled(Integer.valueOf(ENABLED).equals(entity.getEnabled()));
        dto.setEffectiveTime(entity.getEffectiveTime());
        dto.setExpireTime(entity.getExpireTime());
        return dto;
    }

    /**
     * 将响应公钥实体转换为密钥迭代记录。
     *
     * @param entity 商户响应公钥实体
     * @return 密钥迭代记录
     */
    private MerchantKeyRevisionDTO toResponseKeyRevisionDTO(MerchantResponseKeyDO entity) {
        MerchantKeyRevisionDTO dto = new MerchantKeyRevisionDTO();
        dto.setMerchantId(entity.getMerchantId());
        dto.setKeyType(KEY_TYPE_RESPONSE);
        dto.setKeyVersion(entity.getMerchantId());
        dto.setAlgorithm(entity.getAlgorithm());
        dto.setKeyFingerprint(keyMaterialFactory.fingerprint(entity.getPublicKeyX509Base64()));
        dto.setEnabled(Integer.valueOf(ENABLED).equals(entity.getEnabled()));
        dto.setEffectiveTime(entity.getGmtCreate());
        dto.setExpireTime(null);
        return dto;
    }

    /**
     * 根据主键是否存在选择插入或更新，避免写出全表更新。
     *
     * @param entity         待保存实体
     * @param existingId     已存在记录主键，为空时执行插入
     * @param now            当前写库时间
     * @param insertFunction 插入函数
     * @param updateFunction 按主键更新函数
     * @param <T>            MyBatisPlus 基础实体类型
     */
    private <T extends com.scott.payment.component.db.entity.BaseEntity> void saveOrUpdate(T entity,
                                                                                          Long existingId,
                                                                                          LocalDateTime now,
                                                                                          java.util.function.Function<T, Integer> insertFunction,
                                                                                          java.util.function.Function<T, Integer> updateFunction) {
        entity.setGmtModified(now);
        if (existingId == null) {
            entity.setGmtCreate(now);
            insertFunction.apply(entity);
            return;
        }
        entity.setId(existingId);
        updateFunction.apply(entity);
    }

    /**
     * 校验商户开户入参，避免写入缺少核心业务主键的数据。
     *
     * @param seedDTO 商户开户与测试初始化入参
     */
    private void validateSeed(MerchantSecuritySeedDTO seedDTO) {
        if (seedDTO == null || !StringUtils.hasText(seedDTO.getMerchantId())) {
            throw new ApiException(ApiResultEnum.PARAM_MISSING, "merchantId");
        }
        if (!StringUtils.hasText(seedDTO.getMerchantName())) {
            throw new ApiException(ApiResultEnum.PARAM_MISSING, "merchantName");
        }
    }

    /**
     * 校验商户号是否为空。
     *
     * @param merchantId 支付框架颁发的商户号
     */
    private void validateMerchantId(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiResultEnum.MERCHANT_INVALID);
        }
    }

    /**
     * 阻止测试初始化服务误改 SDK live 联调商户密钥。
     *
     * @param merchantId 支付框架颁发的商户号
     */
    private void rejectSdkLiveMerchantMutation(String merchantId) {
        if (SDK_LIVE_MERCHANT_ID.equals(merchantId)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "merchant 200045 is reserved for SDK live tests");
        }
    }

    /**
     * 返回非空文本，否则返回默认值。
     *
     * @param value        候选值
     * @param defaultValue 默认值
     * @return 最终文本
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 返回非空整数值，避免开户测试数据未指定枚举码时写入空状态。
     *
     * @param value        入参中的整数值
     * @param defaultValue 默认整数值
     * @return 非空整数值
     */
    private Integer defaultIfNull(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

}
