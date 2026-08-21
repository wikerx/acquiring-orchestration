package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantSettlementCurrencyService
 * @date : 2026-08-19 00:00
 * @email : scott_x@163.com
 * @description : 商户费率审核中的结算币种生效服务，统一维护商户资料、单币种资金账户和跨服务共享缓存一致性。
 * @status : create
 */
@Service
public class AdminMerchantSettlementCurrencyService {

    private final BaseMerchantInfoMapper merchantInfoMapper;
    private final IsoCurrencyMapper currencyMapper;
    private final AdminMerchantFundAccountProvisioningService fundAccountProvisioningService;
    private final ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;
    private final MerchantRuntimeProfileCacheService runtimeProfileCacheService;

    /**
     * 构造商户结算币种生效服务。
     *
     * @param merchantInfoMapper 商户资料数据访问组件
     * @param currencyMapper ISO 4217 币种数据访问组件
     * @param fundAccountProvisioningService 单币种资金账户同步服务
     * @param cacheInvalidationCoordinator 事务缓存可靠失效协调器
     * @param runtimeProfileCacheService 商户运行时资料缓存服务
     */
    public AdminMerchantSettlementCurrencyService(
            BaseMerchantInfoMapper merchantInfoMapper,
            IsoCurrencyMapper currencyMapper,
            AdminMerchantFundAccountProvisioningService fundAccountProvisioningService,
            ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator,
            MerchantRuntimeProfileCacheService runtimeProfileCacheService) {
        this.merchantInfoMapper = merchantInfoMapper;
        this.currencyMapper = currencyMapper;
        this.fundAccountProvisioningService = fundAccountProvisioningService;
        this.cacheInvalidationCoordinator = cacheInvalidationCoordinator;
        this.runtimeProfileCacheService = runtimeProfileCacheService;
    }

    /**
     * 在费用版本审核事务中同步商户待生效结算币种。
     *
     * <p>已有余额、账户流水、保证金或成功资金动作时，资金账户服务会拒绝直接换币种。
     * 该异常会回滚整个费用版本审核，避免费率版本和资金账户使用不同币种。</p>
     *
     * @param merchantId 商户号
     * @param settlementCurrency 待生效 ISO 4217 三位币种代码
     * @param reviewerName 审核人名称，用于账户审计字段
     */
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void synchronizeApprovedCurrency(String merchantId,
                                            String settlementCurrency,
                                            String reviewerName) {
        String currency = validateConfiguredCurrency(settlementCurrency);
        BaseMerchantInfoDO merchant = merchantInfoMapper.selectOne(
                Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                        .eq(BaseMerchantInfoDO::getDeleted, 0)
                        .last("LIMIT 1 FOR UPDATE"));
        if (merchant == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户不存在，结算币种无法生效");
        }
        if (currency.equalsIgnoreCase(merchant.getSettlementCurrency())) {
            return;
        }
        fundAccountProvisioningService.synchronizeSettlementCurrency(merchantId, currency, reviewerName);
        cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE, merchantId);
        merchant.setSettlementCurrency(currency);
        merchant.setGmtModified(LocalDateTime.now());
        merchantInfoMapper.updateById(merchant);
        runtimeProfileCacheService.refreshRuntimeProfile(merchantId);
    }

    /**
     * 校验并规范化费用版本准备保存的商户结算币种。
     *
     * @param settlementCurrency ISO 4217 三位币种代码
     * @return 大写三位币种代码
     */
    @DS(DataSourceName.MASTER)
    public String validateConfiguredCurrency(String settlementCurrency) {
        String currency = normalizeCurrency(settlementCurrency);
        requireEnabledCurrency(currency);
        return currency;
    }

    /**
     * 将结算币种规范为 ISO 4217 三位大写代码。
     *
     * @param settlementCurrency 页面提交的结算币种
     * @return 三位大写币种代码
     */
    private String normalizeCurrency(String settlementCurrency) {
        if (!StringUtils.hasText(settlementCurrency)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户结算币种不能为空");
        }
        String currency = settlementCurrency.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户结算币种必须是三位字母代码");
        }
        return currency;
    }

    /**
     * 确认结算币种存在于系统币种表且处于启用状态。
     *
     * @param currency 已规范化的三位币种代码
     */
    private void requireEnabledCurrency(String currency) {
        Long count = currencyMapper.selectCount(Wrappers.<IsoCurrencyDO>lambdaQuery()
                .eq(IsoCurrencyDO::getAlpha3Code, currency)
                .eq(IsoCurrencyDO::getStatus, 1)
                .eq(IsoCurrencyDO::getDeleted, 0));
        if (count == null || count == 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户结算币种未启用或不存在");
        }
    }
}
