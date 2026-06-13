package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.merchant.AdminMerchantInfoDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantKeyBundleDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantKeyMaterialDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantKeySummaryDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantQueryRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantResponseKeyRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSaveRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSecurityMaterialDTO;
import com.scott.payment.admin.service.AdminMerchantInfoService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantJwtKeyDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantResponseKeyDO;
import com.scott.payment.component.db.auth.entity.BasePlatformPayloadKeyDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.mapper.BasePlatformPayloadKeyMapper;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.MerchantJwtKey;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.RsaKeyMaterial;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/**
 * 管理后台商户信息服务实现。
 */
@Service
public class AdminMerchantInfoServiceImpl implements AdminMerchantInfoService {

    private static final int NOT_DELETED = 0;
    private static final int ENABLED = 1;
    private static final int DISABLED = 0;
    private static final int DEFAULT_STATUS = 1;
    private static final int DEFAULT_RISK_LEVEL = 2;
    private static final int DEFAULT_KEY_SIZE = 2048;
    private static final String JWT_ALGORITHM = "HS256";
    private static final String PAYLOAD_ALGORITHM = "RSA-OAEP-256+A256GCM";
    private static final DateTimeFormatter KEY_VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BaseMerchantInfoMapper merchantInfoMapper;
    private final BaseMerchantJwtKeyMapper jwtKeyMapper;
    private final BasePlatformPayloadKeyMapper platformPayloadKeyMapper;
    private final BaseMerchantResponseKeyMapper responseKeyMapper;
    private final OpenApiKeyMaterialFactory keyMaterialFactory;

    public AdminMerchantInfoServiceImpl(BaseMerchantInfoMapper merchantInfoMapper,
                                        BaseMerchantJwtKeyMapper jwtKeyMapper,
                                        BasePlatformPayloadKeyMapper platformPayloadKeyMapper,
                                        BaseMerchantResponseKeyMapper responseKeyMapper,
                                        OpenApiKeyMaterialFactory keyMaterialFactory) {
        this.merchantInfoMapper = merchantInfoMapper;
        this.jwtKeyMapper = jwtKeyMapper;
        this.platformPayloadKeyMapper = platformPayloadKeyMapper;
        this.responseKeyMapper = responseKeyMapper;
        this.keyMaterialFactory = keyMaterialFactory;
    }

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

    @Override
    public AdminMerchantInfoDTO getMerchant(Long id) {
        return toDTO(requireMerchantById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMerchantInfoDTO createMerchant(AdminMerchantSaveRequest request) {
        String merchantId = normalizeMerchantId(request.getMerchantId());
        BaseMerchantInfoDO existing = selectMerchantByMerchantId(merchantId);
        if (existing != null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户号已存在");
        }
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
        row.setPrivateKeyPkcs8Base64(normalizeOptionalBase64(request.getPrivateKeyPkcs8Base64(), "响应私钥格式不正确"));
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
        row.setMerchantName(request.getMerchantName().trim());
        row.setMerchantShortName(trimToNull(request.getMerchantShortName()));
        row.setMerchantCategoryCode(request.getMerchantCategoryCode().trim());
        row.setCountryCode(trimUpper(request.getCountryCode()));
        row.setRegionCode(trimToNull(request.getRegionCode()));
        row.setCity(trimToNull(request.getCity()));
        row.setAddressLine(trimToNull(request.getAddressLine()));
        row.setContactEmail(trimToNull(request.getContactEmail()));
        row.setContactPhone(trimToNull(request.getContactPhone()));
        row.setSettlementCurrency(trimUpper(request.getSettlementCurrency()));
        row.setTimezone(request.getTimezone().trim());
        row.setMerchantStatus(request.getMerchantStatus() == null ? DEFAULT_STATUS : request.getMerchantStatus());
        row.setRiskLevel(request.getRiskLevel() == null ? DEFAULT_RISK_LEVEL : request.getRiskLevel());
        validateStatus(row.getMerchantStatus());
        validateRiskLevel(row.getRiskLevel());
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
        dto.setMerchantKey(row.getMerchantKey());
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
        dto.setPublicKeyX509Base64(row.getPublicKeyX509Base64());
        dto.setPrivateKeyPkcs8Base64(row.getPrivateKeyPkcs8Base64());
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
        dto.setPublicKeyX509Base64(row.getPublicKeyX509Base64());
        dto.setPrivateKeyPkcs8Base64(row.getPrivateKeyPkcs8Base64());
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
