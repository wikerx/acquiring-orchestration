package com.scott.payment.component.db.auth.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRuntimeProfileCacheReader
 * @date : 2026-07-30 21:25
 * @email : scott_x@163.com
 * @description : 完整商户资料缓存读写器，负责主库加载、事务提交后的精确缓存更新
 * @status : create
 */
@Service
public class MerchantRuntimeProfileCacheReader {

    /**
     * 逻辑未删除状态。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 单服务实例允许同时执行的最大商户资料主库查询数，防止 Redis 故障把连接池耗尽。
     */
    private static final int MAX_CONCURRENT_DATABASE_LOADS = 64;

    /**
     * 商户基础资料 Mapper。
     */
    private final BaseMerchantInfoMapper merchantInfoMapper;

    /**
     * 主库回源并发许可；公平模式避免持续流量下等待线程长期饥饿。
     */
    private final Semaphore databaseLoadPermits;

    /**
     * 创建商户运行时资料读取器。
     *
     * @param merchantInfoMapper 商户基础资料 Mapper
     */
    @Autowired
    public MerchantRuntimeProfileCacheReader(BaseMerchantInfoMapper merchantInfoMapper) {
        this(merchantInfoMapper, MAX_CONCURRENT_DATABASE_LOADS);
    }

    /**
     * 创建可指定回源并发上限的读取器，供并发边界测试使用。
     *
     * @param merchantInfoMapper        商户基础资料 Mapper
     * @param maxConcurrentDatabaseLoads 单实例最大并发主库查询数，必须大于零
     */
    MerchantRuntimeProfileCacheReader(BaseMerchantInfoMapper merchantInfoMapper,
                                      int maxConcurrentDatabaseLoads) {
        if (maxConcurrentDatabaseLoads <= 0) {
            throw new IllegalArgumentException("Merchant runtime profile DB concurrency limit must be positive");
        }
        this.merchantInfoMapper = merchantInfoMapper;
        this.databaseLoadPermits = new Semaphore(maxConcurrentDatabaseLoads, true);
    }

    /**
     * 正常状态下读取缓存，未命中时从主库加载。
     *
     * <p>商户资料缓存没有过期时间，缓存重建必须读取主库，避免失效后因主从复制延迟
     * 把旧资料再次写入永久缓存。</p>
     *
     * @param merchantId 已规范化的商户号
     * @return 商户运行时资料；不存在时返回 null
     */
    @DS(DataSourceName.MASTER)
    @Cacheable(
            cacheNames = PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
            key = "#p0",
            unless = "#result == null"
    )
    public MerchantRuntimeProfile findCached(String merchantId) {
        return load(merchantId);
    }

    /**
     * 绕过方法级缓存读取当前数据源中的最新业务值。
     * <p>
     * 只读操作；实现必须沿用 公共组件库 既有权限、数据范围和空结果约定。
     * </p>
     * @param merchantId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @DS(DataSourceName.MASTER)
    public MerchantRuntimeProfile findFresh(String merchantId) {
        return load(merchantId);
    }

    /**
     * 从主库读取最新商户资料并覆盖共享缓存。
     *
     * <p>该入口用于管理端写后刷新和可靠补偿，避免把只读库复制延迟重新写入永久缓存。</p>
     *
     * @param merchantId 商户号
     * @return 主库最新商户资料；不存在时返回 null
     */
    @DS(DataSourceName.MASTER)
    @CachePut(
            cacheNames = PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
            key = "#p0",
            unless = "#result == null"
    )
    public MerchantRuntimeProfile refresh(String merchantId) {
        return load(merchantId);
    }

    /**
     * 写入已经由主库事务确认的完整商户资料。
     *
     * @param profile 完整商户资料
     * @return 原商户资料
     */
    @CachePut(
            cacheNames = PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
            key = "#p0.merchantId.trim()",
            condition = "#p0 != null and T(org.springframework.util.StringUtils).hasText(#p0.merchantId)"
    )
    public MerchantRuntimeProfile put(MerchantRuntimeProfile profile) {
        return profile;
    }

    private MerchantRuntimeProfile load(String merchantId) {
        if (!databaseLoadPermits.tryAcquire()) {
            throw new ServiceException(
                    ApiResultEnum.NETWORK_BUSY.getCode(),
                    "merchant runtime profile database fallback is saturated"
            );
        }
        try {
            BaseMerchantInfoDO row = merchantInfoMapper.selectOne(
                    Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                            .select(
                                    BaseMerchantInfoDO::getId,
                                    BaseMerchantInfoDO::getMerchantId,
                                    BaseMerchantInfoDO::getMerchantName,
                                    BaseMerchantInfoDO::getBillingDescriptor,
                                    BaseMerchantInfoDO::getMerchantShortName,
                                    BaseMerchantInfoDO::getMerchantStatus,
                                    BaseMerchantInfoDO::getMerchantCategoryCode,
                                    BaseMerchantInfoDO::getCountryCode,
                                    BaseMerchantInfoDO::getRegionCode,
                                    BaseMerchantInfoDO::getCity,
                                    BaseMerchantInfoDO::getAddressLine,
                                    BaseMerchantInfoDO::getPostalCode,
                                    BaseMerchantInfoDO::getContactName,
                                    BaseMerchantInfoDO::getContactEmail,
                                    BaseMerchantInfoDO::getContactPhone,
                                    BaseMerchantInfoDO::getSettlementCurrency,
                                    BaseMerchantInfoDO::getTimezone,
                                    BaseMerchantInfoDO::getRiskLevel,
                                    BaseMerchantInfoDO::getGmtCreate,
                                    BaseMerchantInfoDO::getGmtModified
                            )
                            .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                            .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED)
                            .last("LIMIT 1")
            );
            return toRuntimeProfile(row);
        } finally {
            databaseLoadPermits.release();
        }
    }

    /**
     * 将商户主表实体转换为缓存资料，禁止把密钥、密码或其他安全材料带入缓存。
     *
     * @param row 商户基础资料最小字段集；允许为空
     * @return 运行时资料；数据库未找到记录时返回 null
     */
    private MerchantRuntimeProfile toRuntimeProfile(BaseMerchantInfoDO row) {
        if (row == null) {
            return null;
        }
        MerchantRuntimeProfile profile = new MerchantRuntimeProfile();
        profile.setId(row.getId());
        profile.setMerchantId(row.getMerchantId());
        profile.setMerchantName(row.getMerchantName());
        profile.setBillingDescriptor(row.getBillingDescriptor());
        profile.setMerchantShortName(row.getMerchantShortName());
        profile.setMerchantStatus(row.getMerchantStatus());
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
}
