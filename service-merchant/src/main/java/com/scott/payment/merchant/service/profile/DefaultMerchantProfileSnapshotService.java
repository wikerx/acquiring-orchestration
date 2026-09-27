package com.scott.payment.merchant.service.profile;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.merchant.entity.MerchantRelatedPersonDO;
import com.scott.payment.component.db.merchant.mapper.SharedMerchantRelatedPersonMapper;
import com.scott.payment.component.security.crypto.SensitiveFieldCipher;
import com.scott.payment.merchant.config.MerchantProfileProperties;
import com.scott.payment.merchant.dto.profile.MerchantProfileChangeDTOs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantProfileSnapshotService
 * @date : 2026-09-21 09:30
 * @email : scott_x@163.com
 * @description : 商户正式资料快照实现，负责高风险主档和相关人员的一致读取与原子替换
 * @status : create
 */
@Service
public class DefaultMerchantProfileSnapshotService implements MerchantProfileSnapshotService {

    private static final int NOT_DELETED = 0;

    private final BaseMerchantInfoMapper merchantInfoMapper;
    private final SharedMerchantRelatedPersonMapper relatedPersonMapper;
    private final ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;
    private final MerchantProfileProperties properties;

    /** 创建商户正式资料快照服务。 */
    public DefaultMerchantProfileSnapshotService(
            BaseMerchantInfoMapper merchantInfoMapper,
            SharedMerchantRelatedPersonMapper relatedPersonMapper,
            ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator,
            MerchantProfileProperties properties) {
        this.merchantInfoMapper = merchantInfoMapper;
        this.relatedPersonMapper = relatedPersonMapper;
        this.cacheInvalidationCoordinator = cacheInvalidationCoordinator;
        this.properties = properties;
    }

    /** 从主库读取当前正式高风险资料，证件号仅返回脱敏值。 */
    @Override
    @DS(DataSourceName.MASTER)
    public MerchantProfileChangeDTOs.ProfileSnapshot loadCurrent(String merchantId) {
        BaseMerchantInfoDO merchant = requireMerchant(merchantId);
        MerchantProfileChangeDTOs.ProfileSnapshot snapshot = toSnapshot(merchant);
        snapshot.setRelatedPersons(relatedPersonMapper.selectList(
                        Wrappers.<MerchantRelatedPersonDO>lambdaQuery()
                                .eq(MerchantRelatedPersonDO::getMerchantId, merchant.getMerchantId())
                                .eq(MerchantRelatedPersonDO::getDeleted, NOT_DELETED)
                                .orderByAsc(MerchantRelatedPersonDO::getDisplayOrder)
                                .orderByAsc(MerchantRelatedPersonDO::getId))
                .stream().map(this::toPerson).toList());
        snapshot.setCapturedAt(LocalDateTime.now());
        return snapshot;
    }

    /** 原子应用审核通过的高风险资料，不覆盖联系人、语言、时区等即时维护字段。 */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void apply(String merchantId, MerchantProfileChangeDTOs.ProfileSnapshot snapshot) {
        if (snapshot == null) {
            throw invalid("merchant profile snapshot is required");
        }
        BaseMerchantInfoDO merchant = requireMerchant(merchantId);
        cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE, merchant.getMerchantId());
        LocalDateTime now = LocalDateTime.now();
        int affected = merchantInfoMapper.update(null,
                Wrappers.<BaseMerchantInfoDO>lambdaUpdate()
                        .set(BaseMerchantInfoDO::getMerchantName, trimToNull(snapshot.getMerchantName()))
                        .set(BaseMerchantInfoDO::getBillingDescriptor, trimToNull(snapshot.getBillingDescriptor()))
                        .set(BaseMerchantInfoDO::getMerchantType, upper(snapshot.getMerchantType()))
                        .set(BaseMerchantInfoDO::getCountryCode, upper(snapshot.getCountryCode()))
                        .set(BaseMerchantInfoDO::getOperatingCountry, upper(snapshot.getOperatingCountry()))
                        .set(BaseMerchantInfoDO::getBusinessType, upper(snapshot.getBusinessType()))
                        .set(BaseMerchantInfoDO::getIndustryCategory, upper(snapshot.getIndustryCategory()))
                        .set(BaseMerchantInfoDO::getMerchantDescription, trimToNull(snapshot.getMerchantDescription()))
                        .set(BaseMerchantInfoDO::getRegistrationNumber, trimToNull(snapshot.getRegistrationNumber()))
                        .set(BaseMerchantInfoDO::getLegalEntityType, upper(snapshot.getLegalEntityType()))
                        .set(BaseMerchantInfoDO::getIncorporationDate, snapshot.getIncorporationDate())
                        .set(BaseMerchantInfoDO::getIncorporationCountry, upper(snapshot.getIncorporationCountry()))
                        .set(BaseMerchantInfoDO::getRegisteredState, trimToNull(snapshot.getRegisteredState()))
                        .set(BaseMerchantInfoDO::getRegisteredCity, trimToNull(snapshot.getRegisteredCity()))
                        .set(BaseMerchantInfoDO::getRegisteredPostcode, trimToNull(snapshot.getRegisteredPostcode()))
                        .set(BaseMerchantInfoDO::getRegisteredAddress, trimToNull(snapshot.getRegisteredAddress()))
                        .set(BaseMerchantInfoDO::getOperatingSameAsRegistered, bool(snapshot.getOperatingSameAsRegistered()))
                        .set(BaseMerchantInfoDO::getTaxId, trimToNull(snapshot.getTaxId()))
                        .set(BaseMerchantInfoDO::getCompanySize, upper(snapshot.getCompanySize()))
                        .set(BaseMerchantInfoDO::getEmployeeCount, snapshot.getEmployeeCount())
                        .set(BaseMerchantInfoDO::getRegionCode, trimToNull(snapshot.getRegionCode()))
                        .set(BaseMerchantInfoDO::getCity, trimToNull(snapshot.getCity()))
                        .set(BaseMerchantInfoDO::getAddressLine, trimToNull(snapshot.getAddressLine()))
                        .set(BaseMerchantInfoDO::getPostalCode, trimToNull(snapshot.getPostalCode()))
                        .set(BaseMerchantInfoDO::getBusinessModel, upper(snapshot.getBusinessModel()))
                        .set(BaseMerchantInfoDO::getSalesChannels, join(snapshot.getSalesChannels()))
                        .set(BaseMerchantInfoDO::getProductsServices, trimToNull(snapshot.getProductsServices()))
                        .set(BaseMerchantInfoDO::getTargetMarkets, joinUpper(snapshot.getTargetMarkets()))
                        .set(BaseMerchantInfoDO::getCustomerType, upper(snapshot.getCustomerType()))
                        .set(BaseMerchantInfoDO::getTransactionCurrencies, joinUpper(snapshot.getTransactionCurrencies()))
                        .set(BaseMerchantInfoDO::getExpectedMonthlyVolume, snapshot.getExpectedMonthlyVolume())
                        .set(BaseMerchantInfoDO::getExpectedVolumeCurrency, upper(snapshot.getExpectedVolumeCurrency()))
                        .set(BaseMerchantInfoDO::getAverageTicket, snapshot.getAverageTicket())
                        .set(BaseMerchantInfoDO::getMaxTicket, snapshot.getMaxTicket())
                        .set(BaseMerchantInfoDO::getExpectedMonthlyCount, snapshot.getExpectedMonthlyCount())
                        .set(BaseMerchantInfoDO::getExpectedRefundRate, snapshot.getExpectedRefundRate())
                        .set(BaseMerchantInfoDO::getExpectedChargebackRate, snapshot.getExpectedChargebackRate())
                        .set(BaseMerchantInfoDO::getRecurringPaymentFlag, bool(snapshot.getRecurringPaymentFlag()))
                        .set(BaseMerchantInfoDO::getPresaleFlag, bool(snapshot.getPresaleFlag()))
                        .set(BaseMerchantInfoDO::getFulfillmentDays, snapshot.getFulfillmentDays())
                        .set(BaseMerchantInfoDO::getDigitalGoodsFlag, bool(snapshot.getDigitalGoodsFlag()))
                        .set(BaseMerchantInfoDO::getRestrictedBusinessFlag, bool(snapshot.getRestrictedBusinessFlag()))
                        .set(BaseMerchantInfoDO::getExpectedGoLiveDate, snapshot.getExpectedGoLiveDate())
                        .set(BaseMerchantInfoDO::getWebsiteUrl, trimToNull(snapshot.getWebsiteUrl()))
                        .set(BaseMerchantInfoDO::getAppStoreUrl, trimToNull(snapshot.getAppStoreUrl()))
                        .set(BaseMerchantInfoDO::getGooglePlayUrl, trimToNull(snapshot.getGooglePlayUrl()))
                        .set(BaseMerchantInfoDO::getOtherSalesUrl, trimToNull(snapshot.getOtherSalesUrl()))
                        .set(BaseMerchantInfoDO::getWebsiteLanguages, join(snapshot.getWebsiteLanguages()))
                        .set(BaseMerchantInfoDO::getWebsiteLiveFlag, bool(snapshot.getWebsiteLiveFlag()))
                        .set(BaseMerchantInfoDO::getPrivacyPolicyUrl, trimToNull(snapshot.getPrivacyPolicyUrl()))
                        .set(BaseMerchantInfoDO::getRefundPolicyUrl, trimToNull(snapshot.getRefundPolicyUrl()))
                        .set(BaseMerchantInfoDO::getTermsUrl, trimToNull(snapshot.getTermsUrl()))
                        .set(BaseMerchantInfoDO::getShippingPolicyUrl, trimToNull(snapshot.getShippingPolicyUrl()))
                        .set(BaseMerchantInfoDO::getGmtModified, now)
                        .eq(BaseMerchantInfoDO::getId, merchant.getId())
                        .eq(BaseMerchantInfoDO::getMerchantId, merchant.getMerchantId())
                        .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED));
        if (affected != 1) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "merchant profile approval did not affect exactly one row");
        }
        replaceRelatedPersons(merchant.getMerchantId(), snapshot.getRelatedPersons(), now);
    }

    /** 软删除旧人员并插入审核快照人员，未重新输入证件号时复用原密文。 */
    private void replaceRelatedPersons(String merchantId,
                                       List<MerchantProfileChangeDTOs.RelatedPerson> persons,
                                       LocalDateTime now) {
        List<MerchantRelatedPersonDO> existing = relatedPersonMapper.selectList(
                Wrappers.<MerchantRelatedPersonDO>lambdaQuery()
                        .eq(MerchantRelatedPersonDO::getMerchantId, merchantId)
                        .eq(MerchantRelatedPersonDO::getDeleted, NOT_DELETED));
        Map<Long, MerchantRelatedPersonDO> existingById = existing.stream()
                .filter(row -> row.getId() != null)
                .collect(Collectors.toMap(MerchantRelatedPersonDO::getId, row -> row));
        if (!existing.isEmpty()) {
            relatedPersonMapper.update(null, Wrappers.<MerchantRelatedPersonDO>lambdaUpdate()
                    .set(MerchantRelatedPersonDO::getDeleted, 1)
                    .set(MerchantRelatedPersonDO::getGmtModified, now)
                    .eq(MerchantRelatedPersonDO::getMerchantId, merchantId)
                    .eq(MerchantRelatedPersonDO::getDeleted, NOT_DELETED));
        }
        List<MerchantProfileChangeDTOs.RelatedPerson> normalized = persons == null
                ? Collections.emptyList() : persons;
        for (int index = 0; index < normalized.size(); index++) {
            MerchantProfileChangeDTOs.RelatedPerson source = normalized.get(index);
            if (source == null || !StringUtils.hasText(source.getFullName())) {
                continue;
            }
            MerchantRelatedPersonDO previous = source.getId() == null ? null : existingById.get(source.getId());
            relatedPersonMapper.insert(toPersonEntity(merchantId, source, previous, index, now));
        }
    }

    private MerchantRelatedPersonDO toPersonEntity(
            String merchantId,
            MerchantProfileChangeDTOs.RelatedPerson source,
            MerchantRelatedPersonDO previous,
            int displayOrder,
            LocalDateTime now) {
        MerchantRelatedPersonDO row = new MerchantRelatedPersonDO();
        row.setMerchantId(merchantId);
        row.setFullName(source.getFullName().trim());
        row.setPersonRoles(joinUpper(source.getPersonRoles()));
        row.setNationality(upper(source.getNationality()));
        row.setDateOfBirth(source.getDateOfBirth());
        row.setResidenceCountry(upper(source.getResidenceCountry()));
        row.setResidentialAddress(trimToNull(source.getResidentialAddress()));
        row.setIdType(upper(source.getIdType()));
        applyIdNumber(row, source, previous, merchantId);
        row.setIdExpiryDate(source.getIdExpiryDate());
        row.setOwnershipPercentage(source.getOwnershipPercentage());
        row.setControllerFlag(bool(source.getControllerFlag()));
        row.setPepFlag(bool(source.getPepFlag()));
        row.setEmail(lower(source.getEmail()));
        row.setPhone(trimToNull(source.getPhone()));
        row.setDisplayOrder(displayOrder);
        row.setGmtCreate(now);
        row.setGmtModified(now);
        row.setDeleted(NOT_DELETED);
        return row;
    }

    private void applyIdNumber(MerchantRelatedPersonDO target,
                               MerchantProfileChangeDTOs.RelatedPerson source,
                               MerchantRelatedPersonDO previous,
                               String merchantId) {
        if (StringUtils.hasText(source.getIdNumber())) {
            requireEncryptionSecret();
            String idNumber = source.getIdNumber().trim();
            target.setIdNumberCipher(SensitiveFieldCipher.encrypt(
                    idNumber, properties.getEncryptionSecret(), merchantId));
            target.setIdNumberMasked(mask(idNumber));
            return;
        }
        if (previous != null) {
            target.setIdNumberCipher(previous.getIdNumberCipher());
            target.setIdNumberMasked(previous.getIdNumberMasked());
        }
    }

    private BaseMerchantInfoDO requireMerchant(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw invalid("merchant id is required");
        }
        BaseMerchantInfoDO merchant = merchantInfoMapper.selectOne(
                Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .eq(BaseMerchantInfoDO::getMerchantId, merchantId.trim())
                        .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1"));
        if (merchant == null || merchant.getId() == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "merchant was not found");
        }
        return merchant;
    }

    private MerchantProfileChangeDTOs.ProfileSnapshot toSnapshot(BaseMerchantInfoDO row) {
        MerchantProfileChangeDTOs.ProfileSnapshot value = new MerchantProfileChangeDTOs.ProfileSnapshot();
        value.setMerchantName(row.getMerchantName());
        value.setBillingDescriptor(row.getBillingDescriptor());
        value.setMerchantType(row.getMerchantType());
        value.setCountryCode(row.getCountryCode());
        value.setOperatingCountry(row.getOperatingCountry());
        value.setBusinessType(row.getBusinessType());
        value.setIndustryCategory(row.getIndustryCategory());
        value.setMerchantDescription(row.getMerchantDescription());
        value.setRegistrationNumber(row.getRegistrationNumber());
        value.setLegalEntityType(row.getLegalEntityType());
        value.setIncorporationDate(row.getIncorporationDate());
        value.setIncorporationCountry(row.getIncorporationCountry());
        value.setRegisteredState(row.getRegisteredState());
        value.setRegisteredCity(row.getRegisteredCity());
        value.setRegisteredPostcode(row.getRegisteredPostcode());
        value.setRegisteredAddress(row.getRegisteredAddress());
        value.setOperatingSameAsRegistered(isTrue(row.getOperatingSameAsRegistered()));
        value.setTaxId(row.getTaxId());
        value.setCompanySize(row.getCompanySize());
        value.setEmployeeCount(row.getEmployeeCount());
        value.setRegionCode(row.getRegionCode());
        value.setCity(row.getCity());
        value.setAddressLine(row.getAddressLine());
        value.setPostalCode(row.getPostalCode());
        value.setBusinessModel(row.getBusinessModel());
        value.setSalesChannels(split(row.getSalesChannels()));
        value.setProductsServices(row.getProductsServices());
        value.setTargetMarkets(split(row.getTargetMarkets()));
        value.setCustomerType(row.getCustomerType());
        value.setTransactionCurrencies(split(row.getTransactionCurrencies()));
        value.setExpectedMonthlyVolume(row.getExpectedMonthlyVolume());
        value.setExpectedVolumeCurrency(row.getExpectedVolumeCurrency());
        value.setAverageTicket(row.getAverageTicket());
        value.setMaxTicket(row.getMaxTicket());
        value.setExpectedMonthlyCount(row.getExpectedMonthlyCount());
        value.setExpectedRefundRate(row.getExpectedRefundRate());
        value.setExpectedChargebackRate(row.getExpectedChargebackRate());
        value.setRecurringPaymentFlag(isTrue(row.getRecurringPaymentFlag()));
        value.setPresaleFlag(isTrue(row.getPresaleFlag()));
        value.setFulfillmentDays(row.getFulfillmentDays());
        value.setDigitalGoodsFlag(isTrue(row.getDigitalGoodsFlag()));
        value.setRestrictedBusinessFlag(isTrue(row.getRestrictedBusinessFlag()));
        value.setExpectedGoLiveDate(row.getExpectedGoLiveDate());
        value.setWebsiteUrl(row.getWebsiteUrl());
        value.setAppStoreUrl(row.getAppStoreUrl());
        value.setGooglePlayUrl(row.getGooglePlayUrl());
        value.setOtherSalesUrl(row.getOtherSalesUrl());
        value.setWebsiteLanguages(split(row.getWebsiteLanguages()));
        value.setWebsiteLiveFlag(isTrue(row.getWebsiteLiveFlag()));
        value.setPrivacyPolicyUrl(row.getPrivacyPolicyUrl());
        value.setRefundPolicyUrl(row.getRefundPolicyUrl());
        value.setTermsUrl(row.getTermsUrl());
        value.setShippingPolicyUrl(row.getShippingPolicyUrl());
        return value;
    }

    private MerchantProfileChangeDTOs.RelatedPerson toPerson(MerchantRelatedPersonDO row) {
        MerchantProfileChangeDTOs.RelatedPerson value = new MerchantProfileChangeDTOs.RelatedPerson();
        value.setId(row.getId());
        value.setFullName(row.getFullName());
        value.setPersonRoles(split(row.getPersonRoles()));
        value.setNationality(row.getNationality());
        value.setDateOfBirth(row.getDateOfBirth());
        value.setResidenceCountry(row.getResidenceCountry());
        value.setResidentialAddress(row.getResidentialAddress());
        value.setIdType(row.getIdType());
        value.setIdNumberMasked(row.getIdNumberMasked());
        value.setIdExpiryDate(row.getIdExpiryDate());
        value.setOwnershipPercentage(row.getOwnershipPercentage());
        value.setControllerFlag(isTrue(row.getControllerFlag()));
        value.setPepFlag(isTrue(row.getPepFlag()));
        value.setEmail(row.getEmail());
        value.setPhone(row.getPhone());
        return value;
    }

    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) return Collections.emptyList();
        return Arrays.stream(value.split(","))
                .map(String::trim).filter(StringUtils::hasText).distinct().toList();
    }

    private String join(List<String> values) {
        if (values == null) return null;
        String joined = values.stream().filter(Objects::nonNull).map(String::trim)
                .filter(StringUtils::hasText).distinct().collect(Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    private String joinUpper(List<String> values) {
        if (values == null) return null;
        String joined = values.stream().filter(Objects::nonNull).map(this::upper)
                .filter(Objects::nonNull).distinct().collect(Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    private Integer bool(Boolean value) {
        return value == null ? null : value ? 1 : 0;
    }

    private Boolean isTrue(Integer value) {
        return value == null ? null : value == 1;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String lower(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String mask(String value) {
        if (value.length() <= 4) return "*".repeat(value.length());
        if (value.length() <= 8) {
            return value.substring(0, 2) + "*".repeat(value.length() - 4)
                    + value.substring(value.length() - 2);
        }
        return value.substring(0, 3) + "*".repeat(value.length() - 7)
                + value.substring(value.length() - 4);
    }

    private void requireEncryptionSecret() {
        if (!StringUtils.hasText(properties.getEncryptionSecret())) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "merchant profile encryption secret is not configured");
        }
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
