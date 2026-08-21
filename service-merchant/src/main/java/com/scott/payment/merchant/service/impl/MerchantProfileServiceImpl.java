package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.dto.profile.MerchantProfileResponse;
import com.scott.payment.merchant.dto.profile.MerchantProfileUpdateRequest;
import com.scott.payment.merchant.service.MerchantProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileServiceImpl
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 商户主体资料领域服务实现，以 base_merchant_info 为事实源并与 Admin、OpenAPI 共用 merchant:info 永久缓存
 * @status : create
 */
@Service
public class MerchantProfileServiceImpl implements MerchantProfileService {

    /** 数据库逻辑未删除状态。 */
    private static final int NOT_DELETED = 0;

    /** 商户基础资料 Mapper；自助修改固定使用 MASTER。 */
    private final BaseMerchantInfoMapper merchantInfoMapper;

    /** Admin、Merchant Portal 和 OpenAPI 共用的商户非敏感资料缓存。 */
    private final MerchantRuntimeProfileCacheService runtimeProfileCacheService;

    /** 商户永久资料缓存的事务门禁与 Outbox 失效协调器。 */
    private final ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;

    /**
     * 创建商户主体资料领域服务。
     *
     * @param merchantInfoMapper 商户基础资料 Mapper
     * @param runtimeProfileCacheService 共享商户运行资料缓存
     * @param cacheInvalidationCoordinator 商户永久资料缓存可靠失效协调器
     */
    public MerchantProfileServiceImpl(
            BaseMerchantInfoMapper merchantInfoMapper,
            MerchantRuntimeProfileCacheService runtimeProfileCacheService,
            ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator) {
        this.merchantInfoMapper = merchantInfoMapper;
        this.runtimeProfileCacheService = runtimeProfileCacheService;
        this.cacheInvalidationCoordinator = cacheInvalidationCoordinator;
    }

    /**
     * 查询当前商户主体资料。
     *
     * <p>完整资料直接来自 Admin、Merchant Portal、OpenAPI 和支付服务共用的永久缓存；
     * 缓存未命中时由共享缓存组件统一回源主库，避免复制延迟将旧资料写回永久缓存。</p>
     *
     * @param merchantId 认证上下文中的商户号
     * @return 当前商户主体资料
     */
    @Override
    public MerchantProfileResponse getProfile(String merchantId) {
        String normalizedMerchantId = requireMerchantId(merchantId);
        MerchantRuntimeProfile runtimeProfile =
                runtimeProfileCacheService.findRuntimeProfile(normalizedMerchantId);
        if (runtimeProfile == null) {
            throw merchantNotFound();
        }
        return toResponse(runtimeProfile);
    }

    /**
     * 更新当前商户允许自助维护的主体资料。
     *
     * <p>更新固定路由到 MASTER，变更前登记门禁与 Outbox 失效意图，并把同一事务读到的
     * 最新完整资料交给事务感知 CacheManager。Redis 写入只在数据库提交成功后执行，
     * 写入失败时由 Outbox 确保旧缓存被可靠删除。</p>
     *
     * @param merchantId 认证上下文中的商户号
     * @param request 商户允许维护的字段
     * @return 当前事务内从主库读取的最新资料
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public MerchantProfileResponse updateProfile(String merchantId,
                                                 MerchantProfileUpdateRequest request) {
        String normalizedMerchantId = requireMerchantId(merchantId);
        MerchantProfileUpdateRequest normalized = normalizeAndValidate(request);
        BaseMerchantInfoDO existing = selectMerchant(normalizedMerchantId);
        if (existing == null || existing.getId() == null) {
            throw merchantNotFound();
        }

        cacheInvalidationCoordinator.prepare(
                PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                normalizedMerchantId
        );
        LocalDateTime now = LocalDateTime.now();
        int updated = merchantInfoMapper.update(
                null,
                Wrappers.<BaseMerchantInfoDO>lambdaUpdate()
                        .set(BaseMerchantInfoDO::getBillingDescriptor, normalized.getBillingDescriptor())
                        .set(BaseMerchantInfoDO::getMerchantShortName, normalized.getMerchantShortName())
                        .set(BaseMerchantInfoDO::getRegionCode, normalized.getRegionCode())
                        .set(BaseMerchantInfoDO::getCity, normalized.getCity())
                        .set(BaseMerchantInfoDO::getAddressLine, normalized.getAddressLine())
                        .set(BaseMerchantInfoDO::getPostalCode, normalized.getPostalCode())
                        .set(BaseMerchantInfoDO::getContactName, normalized.getContactName())
                        .set(BaseMerchantInfoDO::getContactEmail, normalized.getContactEmail())
                        .set(BaseMerchantInfoDO::getContactPhone, normalized.getContactPhone())
                        .set(BaseMerchantInfoDO::getTimezone, normalized.getTimezone())
                        .set(BaseMerchantInfoDO::getGmtModified, now)
                        .eq(BaseMerchantInfoDO::getId, existing.getId())
                        .eq(BaseMerchantInfoDO::getMerchantId, normalizedMerchantId)
                        .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED)
        );
        if (updated != 1) {
            throw new ServiceException(
                    ApiResultEnum.COMMON_FAILED.getCode(),
                    "merchant profile update did not affect exactly one row"
            );
        }
        BaseMerchantInfoDO latest = merchantInfoMapper.selectById(existing.getId());
        if (latest == null || !normalizedMerchantId.equals(latest.getMerchantId())) {
            throw merchantNotFound();
        }
        runtimeProfileCacheService.putRuntimeProfile(toRuntimeProfile(latest));
        return toResponse(latest);
    }

    /**
     * 从 MASTER 查询当前商户完整记录，供写事务进行行级约束和更新后缓存重建。
     *
     * @param merchantId 已规范化商户号
     * @return 完整商户记录；商户不存在时返回 null
     */
    private BaseMerchantInfoDO selectMerchant(String merchantId) {
        return merchantInfoMapper.selectOne(
                Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                        .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
    }

    /**
     * 规范化并校验商户自助更新字段；不接受商户号、状态、MCC、国家、结算币种或风险等级。
     *
     * @param request HTTP 层已完成 Bean Validation 的更新请求
     * @return 去除无意义首尾空白后的请求副本
     */
    private MerchantProfileUpdateRequest normalizeAndValidate(MerchantProfileUpdateRequest request) {
        if (request == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "merchant profile request is required");
        }
        MerchantProfileUpdateRequest normalized = new MerchantProfileUpdateRequest();
        normalized.setBillingDescriptor(requireText(request.getBillingDescriptor(), "billingDescriptor"));
        normalized.setMerchantShortName(requireText(request.getMerchantShortName(), "merchantShortName"));
        normalized.setRegionCode(trimToNull(request.getRegionCode()));
        normalized.setCity(trimToNull(request.getCity()));
        normalized.setAddressLine(trimToNull(request.getAddressLine()));
        normalized.setPostalCode(trimToNull(request.getPostalCode()));
        normalized.setContactName(trimToNull(request.getContactName()));
        normalized.setContactEmail(requireText(request.getContactEmail(), "contactEmail"));
        normalized.setContactPhone(trimToNull(request.getContactPhone()));
        normalized.setTimezone(requireText(request.getTimezone(), "timezone"));
        validateTimezone(normalized.getTimezone());
        return normalized;
    }

    /**
     * 校验 IANA 时区，防止保存前端自由文本后导致交易时间解释错误。
     *
     * @param timezone 已去除首尾空白的时区名称
     */
    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "invalid merchant timezone");
        }
    }

    /**
     * 将共享缓存资料转换为商户门户响应。
     *
     * @param profile 完整商户缓存资料
     * @return 商户门户资料响应
     */
    private MerchantProfileResponse toResponse(MerchantRuntimeProfile profile) {
        MerchantProfileResponse response = new MerchantProfileResponse();
        response.setMerchantId(profile.getMerchantId());
        response.setMerchantName(profile.getMerchantName());
        response.setBillingDescriptor(profile.getBillingDescriptor());
        response.setMerchantShortName(profile.getMerchantShortName());
        response.setMerchantStatus(profile.getMerchantStatus());
        response.setDefaultLocale(profile.getDefaultLocale());
        response.setMerchantCategoryCode(profile.getMerchantCategoryCode());
        response.setCountryCode(profile.getCountryCode());
        response.setRegionCode(profile.getRegionCode());
        response.setCity(profile.getCity());
        response.setAddressLine(profile.getAddressLine());
        response.setPostalCode(profile.getPostalCode());
        response.setContactName(profile.getContactName());
        response.setContactEmail(profile.getContactEmail());
        response.setContactPhone(profile.getContactPhone());
        response.setSettlementCurrency(profile.getSettlementCurrency());
        response.setTimezone(profile.getTimezone());
        response.setRiskLevel(profile.getRiskLevel());
        response.setGmtCreate(profile.getGmtCreate());
        response.setGmtModified(profile.getGmtModified());
        return response;
    }

    /** 将更新后主库完整记录转换为接口响应，不在事务提交前读取旧缓存。 */
    private MerchantProfileResponse toResponse(BaseMerchantInfoDO row) {
        MerchantProfileResponse response = new MerchantProfileResponse();
        response.setMerchantId(row.getMerchantId());
        response.setMerchantName(row.getMerchantName());
        response.setBillingDescriptor(row.getBillingDescriptor());
        response.setMerchantShortName(row.getMerchantShortName());
        response.setMerchantStatus(row.getMerchantStatus());
        response.setDefaultLocale(row.getDefaultLocale());
        response.setMerchantCategoryCode(row.getMerchantCategoryCode());
        response.setCountryCode(row.getCountryCode());
        response.setRegionCode(row.getRegionCode());
        response.setCity(row.getCity());
        response.setAddressLine(row.getAddressLine());
        response.setPostalCode(row.getPostalCode());
        response.setContactName(row.getContactName());
        response.setContactEmail(row.getContactEmail());
        response.setContactPhone(row.getContactPhone());
        response.setSettlementCurrency(row.getSettlementCurrency());
        response.setTimezone(row.getTimezone());
        response.setRiskLevel(row.getRiskLevel());
        response.setGmtCreate(row.getGmtCreate());
        response.setGmtModified(row.getGmtModified());
        return response;
    }

    /**
     * 将主库完整商户记录转换为共享缓存 DTO。
     *
     * @param row 已确认提交到主库的商户记录
     * @return 不包含任何密钥材料的完整商户缓存资料
     */
    private MerchantRuntimeProfile toRuntimeProfile(BaseMerchantInfoDO row) {
        MerchantRuntimeProfile profile = new MerchantRuntimeProfile();
        profile.setId(row.getId());
        profile.setMerchantId(row.getMerchantId());
        profile.setMerchantName(row.getMerchantName());
        profile.setBillingDescriptor(row.getBillingDescriptor());
        profile.setMerchantShortName(row.getMerchantShortName());
        profile.setMerchantStatus(row.getMerchantStatus());
        profile.setDefaultLocale(row.getDefaultLocale());
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

    /** 校验并规范化认证上下文中的商户号。 */
    private String requireMerchantId(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return merchantId.trim();
    }

    /** 校验必填文本并移除首尾空白。 */
    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), fieldName + " is required");
        }
        return value.trim();
    }

    /** 将可选文本规范化为空值或去除首尾空白后的值。 */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 构造不泄露内部主键或查询细节的商户不存在异常。 */
    private ServiceException merchantNotFound() {
        return new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "merchant profile not found");
    }
}
