package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundAccountDO;
import com.scott.payment.admin.mapper.MerchantFundAccountMapper;
import com.scott.payment.admin.service.AdminTransactionFundQueryService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantFundAccountProvisioningService
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户资金账户开户与结算币种一致性服务；当前每个商户只创建一个账户，并为后续多币种账户保留扩展位。
 * @status : create
 */
@Service
public class AdminMerchantFundAccountProvisioningService {

    private final MerchantFundAccountMapper accountMapper;
    private final AdminTransactionFundQueryService transactionFundQueryService;

    /**
     * 构造商户资金账户开户与单结算币种同步服务。
     *
     * @param accountMapper 资金账户幂等开户、行锁和资金关联记录查询数据访问
     * @param transactionFundQueryService 交易副本资金动作存在性查询服务
     */
    public AdminMerchantFundAccountProvisioningService(
            MerchantFundAccountMapper accountMapper,
            AdminTransactionFundQueryService transactionFundQueryService) {
        this.accountMapper = accountMapper;
        this.transactionFundQueryService = transactionFundQueryService;
    }

    /**
     * 按商户当前单一结算币种创建零余额账户。
     *
     * @param merchant 已持久化商户资料
     */
    public void provision(BaseMerchantInfoDO merchant) {
        if (merchant == null || !StringUtils.hasText(merchant.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "merchant is required for fund account");
        }
        if (!StringUtils.hasText(merchant.getSettlementCurrency())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "settlement currency is required for fund account");
        }
        LocalDateTime now = LocalDateTime.now();
        MerchantFundAccountDO account = new MerchantFundAccountDO();
        account.setAccountNo("FA" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        account.setMerchantId(merchant.getMerchantId());
        account.setSettlementCurrency(merchant.getSettlementCurrency().trim().toUpperCase(Locale.ROOT));
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setAccountStatus("NORMAL");
        account.setAccountVersion(0L);
        account.setCreateBy("merchant-onboarding");
        account.setCreateTime(now);
        account.setUpdateBy("merchant-onboarding");
        account.setUpdateTime(now);
        account.setDeleted(0L);
        accountMapper.insertIfAbsent(account);
    }

    /**
     * 在商户资料修改事务中同步当前单一资金账户的结算币种。
     *
     * <p>只有可用余额为零，且从未产生余额流水、保证金或成功资金动作时才允许修改。
     * 账户尚未初始化时按新币种补建；发现多个活动账户时拒绝修改，避免在多币种能力上线前产生歧义。</p>
     *
     * @param merchantId 商户号，不允许为空
     * @param settlementCurrency 新结算币种，ISO 4217 三位代码
     * @throws ServiceException 账户已有资金或明细、存在多个活动账户或参数非法时抛出
     */
    public void synchronizeSettlementCurrency(String merchantId, String settlementCurrency) {
        synchronizeSettlementCurrency(merchantId, settlementCurrency, "merchant-profile");
    }

    /**
     * 在商户资料或费用审核事务中同步资金账户结算币种并记录实际操作人。
     *
     * @param merchantId 商户号，不允许为空
     * @param settlementCurrency 新结算币种，ISO 4217 三位代码
     * @param operatorName 实际操作人或受控业务来源
     * @throws ServiceException 账户已有资金或明细、存在多个活动账户或参数非法时抛出
     */
    public void synchronizeSettlementCurrency(String merchantId,
                                              String settlementCurrency,
                                              String operatorName) {
        if (!StringUtils.hasText(merchantId) || !StringUtils.hasText(settlementCurrency)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "merchant and settlement currency are required for fund account synchronization");
        }
        String normalizedCurrency = settlementCurrency.trim().toUpperCase(Locale.ROOT);
        List<MerchantFundAccountDO> accounts = accountMapper.selectList(
                Wrappers.<MerchantFundAccountDO>lambdaQuery()
                        .eq(MerchantFundAccountDO::getMerchantId, merchantId)
                        .eq(MerchantFundAccountDO::getDeleted, 0L)
                        .orderByAsc(MerchantFundAccountDO::getId));
        if (accounts.isEmpty()) {
            BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
            merchant.setMerchantId(merchantId);
            merchant.setSettlementCurrency(normalizedCurrency);
            provision(merchant);
            return;
        }
        if (accounts.size() != 1) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "当前仅支持单结算币种，商户存在多个活动资金账户，禁止修改结算币种");
        }
        MerchantFundAccountDO account = accounts.get(0);
        if (normalizedCurrency.equalsIgnoreCase(account.getSettlementCurrency())) {
            return;
        }
        if (!isZero(account.getAvailableBalance())
                || accountMapper.countAccountRecords(account.getId(), merchantId) > 0
                || transactionFundQueryService.hasSuccessfulFundTransaction(merchantId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "资金账户已有余额或资金明细，禁止直接修改商户结算币种");
        }
        account.setSettlementCurrency(normalizedCurrency);
        account.setUpdateBy(StringUtils.hasText(operatorName) ? operatorName.trim() : "merchant-profile");
        account.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(account);
    }

    private boolean isZero(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) == 0;
    }
}
