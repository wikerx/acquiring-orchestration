package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeCreateRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDeductionCreateRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDetailQuery;
import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundAccountDO;
import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundDeductionDO;
import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundLedgerDO;
import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundRechargeDO;
import com.scott.payment.admin.entity.fund.FundAccountEntities.PendingBalanceAggregate;
import com.scott.payment.admin.mapper.MerchantFundAccountMapper;
import com.scott.payment.admin.mapper.MerchantFundDeductionMapper;
import com.scott.payment.admin.mapper.MerchantFundLedgerMapper;
import com.scott.payment.admin.mapper.MerchantFundRechargeMapper;
import com.scott.payment.admin.mapper.MerchantReserveItemMapper;
import com.scott.payment.admin.service.AdminTransactionFundQueryService;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFundAccountServiceImplTests
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 资金账户测试，验证在途聚合、充值扣减人员隔离、账户锁和重复入账保护。
 * @status : create
 */
class AdminFundAccountServiceImplTests {

    /** 初始化 MyBatis-Plus Lambda 查询测试所需的表元数据。 */
    @BeforeEach
    void setUpTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, MerchantFundAccountDO.class);
        TableInfoHelper.initTableInfo(assistant, MerchantFundDeductionDO.class);
        TableInfoHelper.initTableInfo(assistant, MerchantFundLedgerDO.class);
        TableInfoHelper.initTableInfo(assistant, MerchantFundRechargeDO.class);
        TableInfoHelper.initTableInfo(assistant, BaseMerchantInfoDO.class);
    }

    /** 账户列表不汇总在途和保证金，避免对每一行执行实时资金统计。 */
    @Test
    void shouldNotAggregateDerivedBalancesOnAccountList() {
        Fixture fixture = new Fixture();
        MerchantFundAccountDO account = account();
        Page<MerchantFundAccountDO> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(account));
        when(fixture.accountMapper.selectPage(any(), any())).thenReturn(page);
        when(fixture.merchantInfoMapper.selectList(any())).thenReturn(List.of(merchant()));

        FundAccountResponse response = fixture.service.pageAccounts(new FundAccountQuery()).getRecords().get(0);

        System.out.println("资金账户列表：验证只返回可用余额，详情型在途和保证金统计不会在列表执行");
        assertThat(response.getMerchantName()).isEqualTo("示例商户");
        assertThat(response.getPendingBalances()).isEmpty();
        assertThat(response.getReserveBalance()).isNull();
        verify(fixture.transactionFundQueryService, never()).sumPendingBalances(any());
        verify(fixture.reserveMapper, never()).sumHeldBalance(any(), any());
    }

    /** 账户详情按标签币种汇总在途，并从保证金明细实时计算留存余额。 */
    @Test
    void shouldAggregateDerivedBalancesOnAccountDetail() {
        Fixture fixture = new Fixture();
        MerchantFundAccountDO account = account();
        PendingBalanceAggregate usd = pending("M10001", "USD", "80");
        PendingBalanceAggregate eur = pending("M10001", "EUR", "50");
        when(fixture.accountMapper.selectOne(any())).thenReturn(account);
        when(fixture.merchantInfoMapper.selectList(any())).thenReturn(List.of(merchant()));
        when(fixture.transactionFundQueryService.sumPendingBalances("M10001")).thenReturn(List.of(eur, usd));
        when(fixture.reserveMapper.sumHeldBalance(100L, "M10001")).thenReturn(new BigDecimal("18.75"));

        FundAccountResponse response = fixture.service.getAccount(100L);

        System.out.println("资金账户详情：验证在途按币种分组、保证金从留存明细实时汇总");
        assertThat(response.getPendingBalances()).extracting("currency", "amount")
                .containsExactly(tuple("EUR", new BigDecimal("50")), tuple("USD", new BigDecimal("80")));
        assertThat(response.getReserveBalance()).isEqualByComparingTo("18.75");
        verify(fixture.transactionFundQueryService).sumPendingBalances("M10001");
        verify(fixture.reserveMapper).sumHeldBalance(100L, "M10001");
    }

    /** 人工账户状态只允许按已确认矩阵流转，并在每次成功变更后递增版本。 */
    @Test
    void shouldAllowConfiguredAccountStatusTransitions() {
        assertStatusTransition("NORMAL", "FROZEN");
        assertStatusTransition("NORMAL", "CLOSED");
        assertStatusTransition("FROZEN", "NORMAL");
        assertStatusTransition("FROZEN", "CLOSED");
        assertStatusTransition("CLOSED", "NORMAL");
        System.out.println("账户状态机：验证 NORMAL、FROZEN、CLOSED 的五条合法人工流转及版本递增");
    }

    /** 关闭账户不能直接转为冻结，同状态重复操作也必须拒绝。 */
    @Test
    void shouldRejectIllegalOrRepeatedAccountStatusTransition() {
        Fixture closedFixture = statusFixture("CLOSED");
        assertThatThrownBy(() -> closedFixture.service.changeAccountStatus(
                100L, 3L, "FROZEN", "错误流转", 8L, "操作人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不允许");
        verify(closedFixture.accountMapper, never()).updateById(any(MerchantFundAccountDO.class));

        Fixture normalFixture = statusFixture("NORMAL");
        assertThatThrownBy(() -> normalFixture.service.changeAccountStatus(
                100L, 3L, "NORMAL", "重复操作", 8L, "操作人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已经处于目标状态");
        verify(normalFixture.accountMapper, never()).updateById(any(MerchantFundAccountDO.class));
        System.out.println("账户状态机：验证 CLOSED 不能直接冻结且同状态操作不会覆盖账户");
    }

    /** 页面提交的旧版本号不能覆盖已经发生的账户状态变更。 */
    @Test
    void shouldRejectStaleAccountVersion() {
        Fixture fixture = statusFixture("NORMAL");

        assertThatThrownBy(() -> fixture.service.changeAccountStatus(
                100L, 2L, "FROZEN", "并发操作", 8L, "操作人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请刷新后重试");

        verify(fixture.accountMapper, never()).updateById(any(MerchantFundAccountDO.class));
        System.out.println("账户并发保护：验证页面旧版本 v2 不能覆盖数据库当前版本 v3");
    }

    /** 负余额限制与人工状态共同决定主动逆向、提现和结算能力。 */
    @Test
    void shouldDeriveAccountCapabilitiesFromStatusAndNegativeBalanceRestriction() {
        Fixture fixture = new Fixture();
        MerchantFundAccountDO account = account();
        account.setAvailableBalance(new BigDecimal("-1.00"));
        when(fixture.accountMapper.selectOne(any())).thenReturn(account);
        when(fixture.merchantInfoMapper.selectList(any())).thenReturn(List.of(merchant()));
        when(fixture.transactionFundQueryService.sumPendingBalances("M10001")).thenReturn(List.of());
        when(fixture.reserveMapper.sumHeldBalance(100L, "M10001")).thenReturn(BigDecimal.ZERO);

        FundAccountResponse response = fixture.service.getAccount(100L);

        assertThat(response.getCreditAllowed()).isTrue();
        assertThat(response.getDebitAllowed()).isTrue();
        assertThat(response.getWithdrawalAllowed()).isTrue();
        assertThat(response.getSettlementAllowed()).isTrue();
        assertThat(response.getReverseTransactionAllowed()).isFalse();
        System.out.println("账户能力：验证 NORMAL 负余额账户仍可入账、提现和结算，但禁止主动逆向交易");
    }

    /** 冻结账户只允许被动入账，不得继续发起结算、提现、扣减或主动逆向。 */
    @Test
    void shouldDisableSettlementCapabilityForFrozenAccount() {
        Fixture fixture = new Fixture();
        MerchantFundAccountDO account = account();
        account.setAccountStatus("FROZEN");
        when(fixture.accountMapper.selectOne(any())).thenReturn(account);
        when(fixture.merchantInfoMapper.selectList(any())).thenReturn(List.of(merchant()));
        when(fixture.transactionFundQueryService.sumPendingBalances("M10001")).thenReturn(List.of());
        when(fixture.reserveMapper.sumHeldBalance(100L, "M10001")).thenReturn(BigDecimal.ZERO);

        FundAccountResponse response = fixture.service.getAccount(100L);

        assertThat(response.getCreditAllowed()).isTrue();
        assertThat(response.getDebitAllowed()).isFalse();
        assertThat(response.getWithdrawalAllowed()).isFalse();
        assertThat(response.getSettlementAllowed()).isFalse();
        assertThat(response.getReverseTransactionAllowed()).isFalse();
    }

    /** 全局余额明细必须组合商户、账户、业务类型、方向、币种和入账闭区间条件。 */
    @Test
    void shouldFilterAllLedgersByConfirmedManagementDimensions() {
        Fixture fixture = new Fixture();
        MerchantFundAccountDO account = account();
        AtomicReference<Wrapper<MerchantFundAccountDO>> accountQuery = new AtomicReference<>();
        AtomicReference<Wrapper<MerchantFundLedgerDO>> ledgerQuery = new AtomicReference<>();
        when(fixture.accountMapper.selectList(any())).thenAnswer(invocation -> {
            accountQuery.set(invocation.getArgument(0));
            return List.of(account);
        });
        when(fixture.ledgerMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            ledgerQuery.set(invocation.getArgument(1));
            return new Page<MerchantFundLedgerDO>(1, 10).setRecords(List.of());
        });
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 18, 23, 59, 59);
        var query = new com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDetailQuery();
        query.setMerchantId("M10001");
        query.setAccountNo("FA10001");
        query.setCurrency("usd");
        query.setBalanceType("available");
        query.setBusinessType("recharge");
        query.setDirection("credit");
        query.setPostedStartTime(start);
        query.setPostedEndTime(end);

        fixture.service.pageAllLedgers(query);

        assertThat(hasParams(accountQuery.get(), "FA10001", 0L)).isTrue();
        assertThat(hasParams(ledgerQuery.get(), "M10001", 100L, "USD", "RECHARGE",
                "CREDIT", start, end)).isTrue();
        System.out.println("全局余额明细：验证商户、账户、币种、业务类型、方向和入账闭区间组合筛选");
    }

    /** 全局余额明细也必须拒绝结束时间早于开始时间。 */
    @Test
    void shouldRejectReversedPostedTimeRangeForAllLedgers() {
        Fixture fixture = new Fixture();
        var query = new com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDetailQuery();
        query.setPostedStartTime(LocalDateTime.of(2026, 8, 18, 0, 0));
        query.setPostedEndTime(LocalDateTime.of(2026, 8, 1, 0, 0));

        assertThatThrownBy(() -> fixture.service.pageAllLedgers(query))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("入账结束时间不能早于开始时间");
        verify(fixture.ledgerMapper, never()).selectPage(any(), any());
        System.out.println("全局余额明细：验证反向入账时间范围在查询数据库前被拒绝");
    }

    /** 充值流水分页结果必须附带独立的提交、审核和复核快照，供详情页准确展示。 */
    @Test
    void shouldAttachRechargeReviewSnapshotToLedgerPage() {
        Fixture fixture = new Fixture();
        MerchantFundAccountDO account = account();
        MerchantFundLedgerDO ledger = rechargeLedger();
        MerchantFundRechargeDO recharge = recharge("POSTED", 10L, 20L);
        recharge.setRecheckById(30L);
        recharge.setRecheckByName("复核人");
        recharge.setRecheckComment("复核通过");
        recharge.setRecheckTime(LocalDateTime.of(2026, 8, 18, 10, 0));
        when(fixture.accountMapper.selectOne(any())).thenReturn(account);
        when(fixture.ledgerMapper.selectPage(any(), any())).thenReturn(
                new Page<MerchantFundLedgerDO>(1, 10).setRecords(List.of(ledger)).setTotal(1));
        when(fixture.rechargeMapper.selectList(any())).thenReturn(List.of(recharge));

        var response = fixture.service.pageLedgers(100L, new FundDetailQuery()).getRecords().get(0);

        assertThat(response.getRechargeDetail()).isNotNull();
        assertThat(response.getRechargeDetail().getRechargeNo()).isEqualTo("RC10001");
        assertThat(response.getRechargeDetail().getAuditByName()).isEqualTo("审核人");
        assertThat(response.getRechargeDetail().getRecheckByName()).isEqualTo("复核人");
        verify(fixture.rechargeMapper, times(1)).selectList(any());
        System.out.println("充值流水详情：验证当前页批量关联完整审核复核快照且不逐行查询");
    }

    /** 并发提交相同请求号、账户和金额时返回已存在申请，不暴露数据库唯一键异常。 */
    @Test
    void shouldReturnExistingRechargeAfterConcurrentDuplicateInsert() {
        Fixture fixture = new Fixture();
        MerchantFundAccountDO account = account();
        MerchantFundRechargeDO existing = recharge("PENDING_AUDIT", 10L, null);
        when(fixture.accountMapper.selectOne(any())).thenReturn(account);
        when(fixture.rechargeMapper.selectOne(any())).thenReturn(null);
        when(fixture.rechargeMapper.selectByRequestIdForUpdate("REQ-10001")).thenReturn(existing);
        when(fixture.merchantInfoMapper.selectList(any())).thenReturn(List.of(merchant()));
        doThrow(new DuplicateKeyException("duplicate request id"))
                .when(fixture.rechargeMapper).insert(any(MerchantFundRechargeDO.class));

        var response = fixture.service.createRecharge(rechargeRequest("120.00"), 10L,
                "提交人", "maker");

        System.out.println("充值幂等：验证并发重复请求号、账户和金额返回已有申请");
        assertThat(response.getRechargeNo()).isEqualTo("RC10001");
        assertThat(response.getAmount()).isEqualByComparingTo("120.00");
        verify(fixture.rechargeMapper).selectByRequestIdForUpdate("REQ-10001");
    }

    /** 并发占用相同请求号但金额不一致时返回明确业务冲突。 */
    @Test
    void shouldRejectConcurrentRequestIdWithDifferentAmount() {
        Fixture fixture = new Fixture();
        MerchantFundAccountDO account = account();
        MerchantFundRechargeDO existing = recharge("PENDING_AUDIT", 10L, null);
        when(fixture.accountMapper.selectOne(any())).thenReturn(account);
        when(fixture.rechargeMapper.selectOne(any())).thenReturn(null);
        when(fixture.rechargeMapper.selectByRequestIdForUpdate("REQ-10001")).thenReturn(existing);
        doThrow(new DuplicateKeyException("duplicate request id"))
                .when(fixture.rechargeMapper).insert(any(MerchantFundRechargeDO.class));

        assertThatThrownBy(() -> fixture.service.createRecharge(rechargeRequest("130.00"), 10L,
                "提交人", "maker"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请求号已被其他充值申请使用");
        System.out.println("充值幂等：验证相同请求号被不同金额占用时拒绝复用");
    }

    /** 充值申请提交人不能审核自己提交的申请。 */
    @Test
    void shouldRejectSubmitterAudit() {
        Fixture fixture = new Fixture();
        MerchantFundRechargeDO recharge = recharge("PENDING_AUDIT", 10L, null);
        when(fixture.rechargeMapper.selectByIdForUpdate(1L)).thenReturn(recharge);

        assertThatThrownBy(() -> fixture.service.auditRecharge(1L, "同意", 10L, "提交人", "maker"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("提交人不能审核");
        verify(fixture.rechargeMapper, never()).updateById(any(MerchantFundRechargeDO.class));
        System.out.println("充值人员隔离：验证提交人不能审核自己的申请");
    }

    /** 充值申请提交人不能复核自己提交的申请。 */
    @Test
    void shouldRejectSubmitterRecheck() {
        Fixture fixture = new Fixture();
        MerchantFundRechargeDO recharge = recharge("PENDING_RECHECK", 10L, 20L);
        when(fixture.rechargeMapper.selectByIdForUpdate(1L)).thenReturn(recharge);

        assertThatThrownBy(() -> fixture.service.recheckRecharge(1L, "同意", 10L, "提交人", "maker"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("提交人不能复核");
        verify(fixture.accountMapper, never()).selectByIdForUpdate(any());
        System.out.println("充值人员隔离：验证提交人不能复核自己的申请");
    }

    /** 普通账号的审核人与复核人不能相同。 */
    @Test
    void shouldRequireThreeDifferentOperatorsForNormalAccount() {
        Fixture fixture = new Fixture();
        MerchantFundRechargeDO recharge = recharge("PENDING_RECHECK", 10L, 20L);
        when(fixture.rechargeMapper.selectByIdForUpdate(1L)).thenReturn(recharge);

        assertThatThrownBy(() -> fixture.service.recheckRecharge(1L, "同意", 20L, "审核人", "reviewer"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("三个人");
        verify(fixture.accountMapper, never()).selectByIdForUpdate(any());
        System.out.println("充值人员隔离：验证普通账号提交、审核、复核必须由三人完成");
    }

    /** 仅内置 admin 账号可以独立完成提交后的审核和复核，并完整写入余额流水。 */
    @Test
    void shouldAllowBuiltinAdminToSubmitAuditAndRecheck() {
        Fixture fixture = new Fixture();
        MerchantFundRechargeDO recharge = recharge("PENDING_AUDIT", 10L, null);
        MerchantFundAccountDO account = account();
        when(fixture.rechargeMapper.selectByIdForUpdate(1L)).thenReturn(recharge);
        when(fixture.accountMapper.selectOne(any())).thenReturn(account);
        when(fixture.accountMapper.selectByIdForUpdate(100L)).thenReturn(account);
        when(fixture.ledgerMapper.selectMaxAccountSequence(100L)).thenReturn(7L);
        when(fixture.merchantInfoMapper.selectList(any())).thenReturn(List.of(merchant()));

        fixture.service.auditRecharge(1L, "审核通过", 10L, "管理员", "admin");
        fixture.service.recheckRecharge(1L, "复核通过", 10L, "管理员", "admin");

        System.out.println("充值管理员边界：验证仅 admin 可对自己提交的申请完成审核复核并完整记录两次操作");
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("1120.00");
        assertThat(account.getAccountVersion()).isEqualTo(4L);
        ArgumentCaptor<MerchantFundLedgerDO> captor = ArgumentCaptor.forClass(MerchantFundLedgerDO.class);
        verify(fixture.accountMapper).selectByIdForUpdate(100L);
        verify(fixture.accountMapper).updateById(account);
        verify(fixture.ledgerMapper).insert(captor.capture());
        MerchantFundLedgerDO ledger = captor.getValue();
        assertThat(ledger.getBalanceBefore()).isEqualByComparingTo("1000.00");
        assertThat(ledger.getBalanceAfter()).isEqualByComparingTo("1120.00");
        assertThat(ledger.getAccountSequence()).isEqualTo(8L);
        assertThat(ledger.getIdempotencyKey()).isEqualTo("FUND_RECHARGE:RC10001");
        assertThat(ledger.getReviewerId()).isEqualTo(10L);
        assertThat(recharge.getAuditById()).isEqualTo(10L);
        assertThat(recharge.getRecheckById()).isEqualTo(10L);
        assertThat(recharge.getRechargeStatus()).isEqualTo("POSTED");
        verify(fixture.rechargeMapper, times(2)).updateById(recharge);
    }

    /** 已完成复核的充值不能再次入账。 */
    @Test
    void shouldRejectDuplicateRecheckWithoutPostingAgain() {
        Fixture fixture = new Fixture();
        MerchantFundRechargeDO recharge = recharge("PENDING_RECHECK", 10L, 20L);
        MerchantFundAccountDO account = account();
        when(fixture.rechargeMapper.selectByIdForUpdate(1L)).thenReturn(recharge);
        when(fixture.accountMapper.selectByIdForUpdate(100L)).thenReturn(account);
        when(fixture.ledgerMapper.selectMaxAccountSequence(100L)).thenReturn(7L);
        when(fixture.merchantInfoMapper.selectList(any())).thenReturn(List.of(merchant()));

        fixture.service.recheckRecharge(1L, "首次复核", 30L, "复核人", "checker");
        assertThatThrownBy(() -> fixture.service.recheckRecharge(1L, "重复复核", 30L, "复核人", "checker"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("状态已变化");

        verify(fixture.accountMapper, times(1)).updateById(any(MerchantFundAccountDO.class));
        verify(fixture.ledgerMapper, times(1)).insert(any(MerchantFundLedgerDO.class));
        System.out.println("充值重复复核：验证已完成申请不会再次增加余额或写入流水");
    }

    /** 内置 admin 可完成扣减审核复核，余额允许扣成负数且流水使用正金额借方语义。 */
    @Test
    void shouldAllowBuiltinAdminToPostDeductionIntoNegativeBalance() {
        Fixture fixture = new Fixture();
        MerchantFundDeductionDO deduction = deduction("PENDING_AUDIT", 10L, null);
        MerchantFundAccountDO account = account();
        when(fixture.deductionMapper.selectByIdForUpdate(1L)).thenReturn(deduction);
        when(fixture.accountMapper.selectOne(any())).thenReturn(account);
        when(fixture.accountMapper.selectByIdForUpdate(100L)).thenReturn(account);
        when(fixture.ledgerMapper.selectMaxAccountSequence(100L)).thenReturn(7L);
        when(fixture.merchantInfoMapper.selectList(any())).thenReturn(List.of(merchant()));

        fixture.service.auditDeduction(1L, "审核通过", 10L, "管理员", "admin");
        fixture.service.recheckDeduction(1L, "复核通过", 10L, "管理员", "admin");

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("-200.00");
        assertThat(account.getAccountVersion()).isEqualTo(4L);
        ArgumentCaptor<MerchantFundLedgerDO> captor = ArgumentCaptor.forClass(MerchantFundLedgerDO.class);
        verify(fixture.ledgerMapper).insert(captor.capture());
        MerchantFundLedgerDO ledger = captor.getValue();
        assertThat(ledger.getBusinessType()).isEqualTo("BALANCE_DEDUCTION");
        assertThat(ledger.getDirection()).isEqualTo("DEBIT");
        assertThat(ledger.getAmount()).isEqualByComparingTo("1200.00");
        assertThat(ledger.getBalanceBefore()).isEqualByComparingTo("1000.00");
        assertThat(ledger.getBalanceAfter()).isEqualByComparingTo("-200.00");
        assertThat(ledger.getOperationReason()).isEqualTo("商户违规罚金");
        assertThat(ledger.getIdempotencyKey()).isEqualTo("FUND_DEDUCTION:FD10001");
        assertThat(deduction.getDeductionStatus()).isEqualTo("POSTED");
        verify(fixture.deductionMapper, times(2)).updateById(deduction);
        System.out.println("账户扣减：admin 完成审核复核后原子扣减，允许负余额并写入 DEBIT 不可变流水");
    }

    /** 账户关闭后，即使申请此前已审核也必须阻止复核扣减。 */
    @Test
    void shouldRejectDeductionRecheckWhenAccountIsClosed() {
        Fixture fixture = new Fixture();
        MerchantFundDeductionDO deduction = deduction("PENDING_RECHECK", 10L, 20L);
        MerchantFundAccountDO account = account();
        account.setAccountStatus("CLOSED");
        when(fixture.deductionMapper.selectByIdForUpdate(1L)).thenReturn(deduction);
        when(fixture.accountMapper.selectByIdForUpdate(100L)).thenReturn(account);

        assertThatThrownBy(() -> fixture.service.recheckDeduction(
                1L, "复核通过", 30L, "复核人", "checker"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已关闭");

        verify(fixture.accountMapper, never()).updateById(any(MerchantFundAccountDO.class));
        verify(fixture.ledgerMapper, never()).insert(any(MerchantFundLedgerDO.class));
        System.out.println("账户扣减：复核时二次检查账户状态，关闭账户不产生余额变动");
    }

    private static void assertStatusTransition(String currentStatus, String targetStatus) {
        Fixture fixture = statusFixture(currentStatus);

        FundAccountResponse response = fixture.service.changeAccountStatus(
                100L, 3L, targetStatus, "状态调整", 8L, "操作人");

        assertThat(response.getAccountStatus()).isEqualTo(targetStatus);
        assertThat(response.getAccountVersion()).isEqualTo(4L);
        verify(fixture.accountMapper).updateById(any(MerchantFundAccountDO.class));
    }

    private static Fixture statusFixture(String status) {
        Fixture fixture = new Fixture();
        MerchantFundAccountDO account = account();
        account.setAccountStatus(status);
        when(fixture.accountMapper.selectByIdForUpdate(100L)).thenReturn(account);
        when(fixture.merchantInfoMapper.selectList(any())).thenReturn(List.of(merchant()));
        when(fixture.reserveMapper.sumHeldBalance(100L, "M10001")).thenReturn(BigDecimal.ZERO);
        return fixture;
    }

    private static boolean hasParams(Wrapper<?> wrapper, Object... expectedValues) {
        if (!(wrapper instanceof AbstractWrapper<?, ?, ?> queryWrapper)) {
            return false;
        }
        queryWrapper.getSqlSegment();
        return java.util.Arrays.stream(expectedValues)
                .allMatch(queryWrapper.getParamNameValuePairs().values()::contains);
    }

    private static MerchantFundAccountDO account() {
        MerchantFundAccountDO account = new MerchantFundAccountDO();
        account.setId(100L);
        account.setAccountNo("FA10001");
        account.setMerchantId("M10001");
        account.setSettlementCurrency("USD");
        account.setAvailableBalance(new BigDecimal("1000.00"));
        account.setAccountStatus("NORMAL");
        account.setAccountVersion(3L);
        account.setUpdateTime(LocalDateTime.of(2026, 8, 18, 10, 0));
        account.setDeleted(0L);
        return account;
    }

    private static MerchantFundRechargeDO recharge(String status, Long submitById, Long auditById) {
        MerchantFundRechargeDO recharge = new MerchantFundRechargeDO();
        recharge.setId(1L);
        recharge.setRechargeNo("RC10001");
        recharge.setAccountId(100L);
        recharge.setMerchantId("M10001");
        recharge.setCurrency("USD");
        recharge.setAmount(new BigDecimal("120.00"));
        recharge.setRechargeStatus(status);
        recharge.setRemark("银行到账充值");
        recharge.setSubmitById(submitById);
        recharge.setSubmitByName("提交人");
        recharge.setSubmitTime(LocalDateTime.of(2026, 8, 18, 9, 0));
        recharge.setAuditById(auditById);
        recharge.setAuditByName(auditById == null ? null : "审核人");
        recharge.setAuditComment(auditById == null ? null : "审核通过");
        recharge.setRequestId("REQ-10001");
        recharge.setDeleted(0L);
        return recharge;
    }

    private static MerchantFundDeductionDO deduction(String status, Long submitById, Long auditById) {
        MerchantFundDeductionDO deduction = new MerchantFundDeductionDO();
        deduction.setId(1L);
        deduction.setDeductionNo("FD10001");
        deduction.setAccountId(100L);
        deduction.setMerchantId("M10001");
        deduction.setCurrency("USD");
        deduction.setAmount(new BigDecimal("1200.00"));
        deduction.setDeductionCategory("PENALTY");
        deduction.setDeductionStatus(status);
        deduction.setReason("商户违规罚金");
        deduction.setSubmitById(submitById);
        deduction.setSubmitByName("提交人");
        deduction.setSubmitTime(LocalDateTime.of(2026, 8, 20, 9, 0));
        deduction.setAuditById(auditById);
        deduction.setAuditByName(auditById == null ? null : "审核人");
        deduction.setAuditComment(auditById == null ? null : "审核通过");
        deduction.setRequestId("DED-REQ-10001");
        deduction.setDeleted(0L);
        return deduction;
    }

    private static MerchantFundLedgerDO rechargeLedger() {
        MerchantFundLedgerDO ledger = new MerchantFundLedgerDO();
        ledger.setId(200L);
        ledger.setLedgerNo("FL10001");
        ledger.setLedgerGroupNo("RC10001");
        ledger.setAccountId(100L);
        ledger.setMerchantId("M10001");
        ledger.setBusinessType("RECHARGE");
        ledger.setSummary("管理端充值入账");
        ledger.setBusinessNo("RC10001");
        ledger.setCurrency("USD");
        ledger.setDirection("CREDIT");
        ledger.setAmount(new BigDecimal("120.00"));
        ledger.setBalanceBefore(new BigDecimal("1000.00"));
        ledger.setBalanceAfter(new BigDecimal("1120.00"));
        ledger.setAccountSequence(8L);
        ledger.setOperationMode("MANUAL");
        ledger.setOperatorName("提交人");
        ledger.setReviewerName("复核人");
        ledger.setBusinessTime(LocalDateTime.of(2026, 8, 18, 9, 0));
        ledger.setPostedTime(LocalDateTime.of(2026, 8, 18, 10, 0));
        ledger.setIdempotencyKey("FUND_RECHARGE:RC10001");
        return ledger;
    }

    private static FundRechargeCreateRequest rechargeRequest(String amount) {
        FundRechargeCreateRequest request = new FundRechargeCreateRequest();
        request.setAccountId(100L);
        request.setAmount(new BigDecimal(amount));
        request.setRequestId("REQ-10001");
        request.setRemark("银行到账充值");
        return request;
    }

    private static FundDeductionCreateRequest deductionRequest(String amount) {
        FundDeductionCreateRequest request = new FundDeductionCreateRequest();
        request.setAccountId(100L);
        request.setDeductionCategory("PENALTY");
        request.setAmount(new BigDecimal(amount));
        request.setRequestId("DED-REQ-10001");
        request.setReason("商户违规罚金");
        return request;
    }

    private static PendingBalanceAggregate pending(String merchantId, String currency, String amount) {
        PendingBalanceAggregate aggregate = new PendingBalanceAggregate();
        aggregate.setMerchantId(merchantId);
        aggregate.setCurrency(currency);
        aggregate.setAmount(new BigDecimal(amount));
        return aggregate;
    }

    private static BaseMerchantInfoDO merchant() {
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setMerchantId("M10001");
        merchant.setMerchantName("示例商户");
        merchant.setDeleted(0);
        return merchant;
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }

    private static final class Fixture {
        private final MerchantFundAccountMapper accountMapper = mock(MerchantFundAccountMapper.class);
        private final MerchantFundLedgerMapper ledgerMapper = mock(MerchantFundLedgerMapper.class);
        private final MerchantFundRechargeMapper rechargeMapper = mock(MerchantFundRechargeMapper.class);
        private final MerchantFundDeductionMapper deductionMapper = mock(MerchantFundDeductionMapper.class);
        private final AdminTransactionFundQueryService transactionFundQueryService =
                mock(AdminTransactionFundQueryService.class);
        private final MerchantReserveItemMapper reserveMapper = mock(MerchantReserveItemMapper.class);
        private final BaseMerchantInfoMapper merchantInfoMapper = mock(BaseMerchantInfoMapper.class);
        private final AdminFundAccountServiceImpl service = new AdminFundAccountServiceImpl(
                accountMapper, ledgerMapper, rechargeMapper, deductionMapper, transactionFundQueryService,
                reserveMapper, merchantInfoMapper);
    }
}
