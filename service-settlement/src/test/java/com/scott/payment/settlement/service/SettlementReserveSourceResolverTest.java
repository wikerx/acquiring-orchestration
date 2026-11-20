package com.scott.payment.settlement.service;

import com.scott.payment.settlement.domain.model.SettlementFailureStage;
import com.scott.payment.settlement.entity.MerchantReserveItemDO;
import com.scott.payment.settlement.exception.SettlementProcessingException;
import com.scott.payment.settlement.mapper.SettlementReserveSourceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 证明来源迁移必须完整、唯一且金额身份不变，不能猜测 ACTIVE HOLD。 */
class SettlementReserveSourceResolverTest {
    private SettlementReserveSourceMapper mapper;
    private SettlementReserveSourceResolver resolver(ReserveHoldFixture f) {
        mapper=mock(SettlementReserveSourceMapper.class);
        when(mapper.selectMigrations(anyList())).thenReturn(List.of(f.proof));
        when(mapper.selectHoldSnapshots(anyList())).thenReturn(List.of(f.oldHold,f.newHold));
        when(mapper.selectStates(anyList())).thenReturn(List.of(f.state));
        when(mapper.selectFundedItems(anyString(),anyList())).thenReturn(List.of(f.item));
        return new SettlementReserveSourceResolver(mapper);
    }

    @Test
    void resolvesAuditedReplacementWithoutChangingReleaseIdentity() {
        var f=new ReserveHoldFixture();var service=resolver(f);
        var binding=service.resolve(f.batch,f.facts(),SettlementFailureStage.FACT_LOADING).get("RELEASE-DETAIL");
        assertThat(binding.holdDetailNo()).isEqualTo("NEW-HOLD");
        assertThat(binding.reserveItemId()).isEqualTo(40L);
        assertThat(binding.reserveItemVersion()).isZero();
        assertThat(f.release.getSourceReserveDetailNo()).isEqualTo("OLD-HOLD");
        assertThat(f.release.getReserveClearingDetailNo()).isEqualTo("RELEASE-DETAIL");
        assertThat(f.release.getReleasedAmount()).isEqualByComparingTo("5.26");
        assertThat(f.state.getVersion()).isEqualTo(2L);
        verify(mapper,times(1)).selectHoldSnapshots(anyList());
    }

    @Test
    void acceptsDifferentDecimalScalesWithIdenticalMoney() {
        var f=new ReserveHoldFixture();f.newHold.setReserveRate(new BigDecimal("0.05000000"));
        assertThat(resolver(f).resolve(f.batch,f.facts(),SettlementFailureStage.FACT_LOADING)).hasSize(1);
    }

    @Test
    void keepsNormalFundedOriginalHoldPath() {
        var f=new ReserveHoldFixture();var service=resolver(f);f.item.setSourceBusinessNo("OLD-HOLD");
        when(mapper.selectMigrations(anyList())).thenReturn(List.of());
        assertThat(service.resolve(f.batch,f.facts(),SettlementFailureStage.FACT_LOADING)).isEmpty();
        verify(mapper,never()).selectHoldSnapshots(anyList());verify(mapper,never()).selectStates(anyList());
    }

    @Test
    void rejectsAmbiguousMigrationAudits() {
        var f=new ReserveHoldFixture();var service=resolver(f);
        when(mapper.selectMigrations(anyList())).thenReturn(List.of(f.proof,f.proof));
        rejected(() -> service.resolve(f.batch,f.facts(),SettlementFailureStage.FACT_LOADING));
    }

    @Test
    void rejectsUnfundedSourceWithoutAudit() {
        var f=new ReserveHoldFixture();var service=resolver(f);
        when(mapper.selectMigrations(anyList())).thenReturn(List.of());
        rejected(() -> service.resolve(f.batch,f.facts(),SettlementFailureStage.FACT_LOADING));
    }

    @Test
    void rejectsWhenBothOldAndNewHoldsWereFunded() {
        var f=new ReserveHoldFixture();var service=resolver(f);var old=new MerchantReserveItemDO();
        BeanUtils.copyProperties(f.item,old);old.setId(41L);old.setSourceBusinessNo("OLD-HOLD");
        when(mapper.selectFundedItems(anyString(),anyList())).thenReturn(List.of(old,f.item));
        rejected(() -> service.resolve(f.batch,f.facts(),SettlementFailureStage.LEDGER_POSTING));
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("invalidProofs")
    void rejectsIncompleteOrChangedProof(String name,Consumer<ReserveHoldFixture> corrupt) {
        var f=new ReserveHoldFixture();corrupt.accept(f);var service=resolver(f);
        rejected(() -> service.resolve(f.batch,f.facts(),SettlementFailureStage.FACT_LOADING));
    }

    static Stream<Arguments> invalidProofs() {
        return Stream.of(
            bad("修复未执行",f->f.proof.setRepairStatus("PENDING_REVIEW")),
            bad("商户不符",f->f.proof.setMerchantId("OTHER-MERCHANT")),
            bad("原支付不符",f->f.proof.setTransactionId("OTHER-PAYMENT")),
            bad("原支付毫秒不符",f->f.proof.setTransactionDateTime(ReserveHoldFixture.ORIGINAL_TIME.plusNanos(1_000_000))),
            bad("新修订跨级",f->f.proof.setNewRevision(3)),
            bad("依赖审计不符",f->f.proof.setNewDependsOnCandidateId(99L)),
            bad("当前依赖已变",f->f.proof.setCurrentDependsOnCandidateId(99L)),
            bad("依赖类型不符",f->f.proof.setCurrentDependencyType("OTHER")),
            bad("释放候选不符",f->f.proof.setDependentCandidateId(99L)),
            bad("新HOLD未入账",f->f.proof.setNewCandidateStatus("READY")),
            bad("结算资料不符",f->f.proof.setNewCandidateProfileId(99L)),
            bad("新HOLD快照金额变更",f->f.newHold.setRetainedAmount(new BigDecimal("6.26"))),
            bad("新HOLD费率变更",f->f.newHold.setReserveRate(new BigDecimal("0.06"))),
            bad("新HOLD规则变更",f->f.newHold.setReserveSnapshotHash("changed")),
            bad("新HOLD失效",f->f.newHold.setRecordStatus("SUPERSEDED")),
            bad("清分状态指针变化",f->f.state.setOriginalHoldDetailNo("OTHER-HOLD")),
            bad("清分版本变化",f->f.state.setVersion(3L)),
            bad("清分释放金额变化",f->f.state.setReleasedAmount(new BigDecimal("6.26"))),
            bad("清分有其他返还",f->f.state.setReturnedAmount(new BigDecimal("1.00"))),
            bad("资金账户不符",f->f.item.setAccountId(99L)),
            bad("资金原币不符",f->f.item.setCurrency("EUR")),
            bad("责任余额不足",f->f.item.setRetainedAmount(new BigDecimal("4.26"))),
            bad("已有部分释放",f->f.item.setReleasedAmount(new BigDecimal("1.00"))),
            bad("资金责任多于冻结释放",f->f.item.setDebitAdjustmentAmount(new BigDecimal("1.00")))
        );
    }
    private static Arguments bad(String name,Consumer<ReserveHoldFixture> corrupt) {return Arguments.of(name,corrupt);}
    private void rejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(SettlementProcessingException.class,e->{
            assertThat(e.getFailureCode()).isEqualTo("SETTLEMENT_RESERVE_SOURCE_INVALID");assertThat(e.isRetryable()).isFalse();
        });
    }
}
