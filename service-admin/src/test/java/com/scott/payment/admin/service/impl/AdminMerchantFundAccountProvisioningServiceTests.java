package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundAccountDO;
import com.scott.payment.admin.mapper.MerchantFundAccountMapper;
import com.scott.payment.admin.service.AdminTransactionFundQueryService;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantFundAccountProvisioningServiceTests
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户资金账户开户测试，验证单结算币种和零余额初始状态。
 * @status : create
 */
class AdminMerchantFundAccountProvisioningServiceTests {

    /** 新商户只创建一个零余额、正常状态的结算币种账户。 */
    @Test
    void shouldProvisionZeroBalanceSettlementAccount() {
        System.out.println("资金账户开户：验证按商户结算币种创建零余额单一账户");
        MerchantFundAccountMapper mapper = mock(MerchantFundAccountMapper.class);
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setMerchantId("M10001");
        merchant.setSettlementCurrency("usd");

        new AdminMerchantFundAccountProvisioningService(
                mapper, mock(AdminTransactionFundQueryService.class)).provision(merchant);

        verify(mapper).insertIfAbsent(argThat((MerchantFundAccountDO account) -> {
            assertThat(account.getAccountNo()).startsWith("FA");
            assertThat(account.getMerchantId()).isEqualTo("M10001");
            assertThat(account.getSettlementCurrency()).isEqualTo("USD");
            assertThat(account.getAvailableBalance()).isZero();
            assertThat(account.getAccountStatus()).isEqualTo("NORMAL");
            return true;
        }));
    }

    /** 未产生余额或任何资金明细时，商户结算币种可以同步到账户。 */
    @Test
    void shouldSynchronizeCurrencyForUnusedAccount() {
        System.out.println("结算币种同步：验证零余额且无资金明细时允许 USD 修改为 EUR");
        MerchantFundAccountMapper mapper = mock(MerchantFundAccountMapper.class);
        AdminTransactionFundQueryService transactionQueryService = mock(AdminTransactionFundQueryService.class);
        MerchantFundAccountDO account = account("USD", BigDecimal.ZERO);
        when(mapper.selectList(any())).thenReturn(List.of(account));
        when(mapper.countAccountRecords(1L, "M10001")).thenReturn(0L);
        when(transactionQueryService.hasSuccessfulFundTransaction("M10001")).thenReturn(false);

        new AdminMerchantFundAccountProvisioningService(mapper, transactionQueryService)
                .synchronizeSettlementCurrency("M10001", "eur");

        verify(mapper).updateById(argThat(
                (MerchantFundAccountDO updated) -> "EUR".equals(updated.getSettlementCurrency())));
    }

    /** 已有资金余额时禁止修改结算币种，避免账户明细币种与商户资料失配。 */
    @Test
    void shouldRejectCurrencyChangeWhenAccountHasBalance() {
        System.out.println("结算币种同步：验证可用余额非零时禁止修改结算币种");
        MerchantFundAccountMapper mapper = mock(MerchantFundAccountMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(account("USD", BigDecimal.ONE)));

        assertThatThrownBy(() -> new AdminMerchantFundAccountProvisioningService(
                mapper, mock(AdminTransactionFundQueryService.class))
                .synchronizeSettlementCurrency("M10001", "EUR"))
                .isInstanceOf(com.scott.payment.component.core.exception.ServiceException.class)
                .hasMessageContaining("禁止直接修改");
    }

    /** 成功资金动作已经存在时，即使账户尚未入账也禁止变更结算币种。 */
    @Test
    void shouldRejectCurrencyChangeWhenTransactionActivityExists() {
        MerchantFundAccountMapper mapper = mock(MerchantFundAccountMapper.class);
        AdminTransactionFundQueryService transactionQueryService = mock(AdminTransactionFundQueryService.class);
        when(mapper.selectList(any())).thenReturn(List.of(account("USD", BigDecimal.ZERO)));
        when(mapper.countAccountRecords(1L, "M10001")).thenReturn(0L);
        when(transactionQueryService.hasSuccessfulFundTransaction("M10001")).thenReturn(true);

        assertThatThrownBy(() -> new AdminMerchantFundAccountProvisioningService(mapper, transactionQueryService)
                .synchronizeSettlementCurrency("M10001", "EUR"))
                .isInstanceOf(com.scott.payment.component.core.exception.ServiceException.class)
                .hasMessageContaining("禁止直接修改");

        verify(transactionQueryService).hasSuccessfulFundTransaction("M10001");
    }

    private MerchantFundAccountDO account(String currency, BigDecimal available) {
        MerchantFundAccountDO account = new MerchantFundAccountDO();
        account.setId(1L);
        account.setMerchantId("M10001");
        account.setSettlementCurrency(currency);
        account.setAvailableBalance(available);
        account.setDeleted(0L);
        return account;
    }
}
