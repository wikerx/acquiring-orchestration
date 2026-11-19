package com.scott.payment.link.service;

import com.scott.payment.link.entity.*;
import com.scott.payment.link.mapper.*;
import com.scott.payment.link.model.PaymentLinkManagementModels.*;
import com.scott.payment.link.config.PaymentLinkSettings;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PaymentLinkManagementServiceTest {
    final PaymentLinkManagementMapper links = mock(PaymentLinkManagementMapper.class);
    final PaymentLinkManagementAccessMapper access = mock(PaymentLinkManagementAccessMapper.class);
    final PaymentLinkManagementEventMapper events = mock(PaymentLinkManagementEventMapper.class);
    final PaymentLinkManagementPolicy policy = mock(PaymentLinkManagementPolicy.class);
    final PaymentLinkTokenService tokens = mock(PaymentLinkTokenService.class);
    final PaymentLinkSettings properties = new PaymentLinkSettings();
    final GlobalIdGenerator ids = mock(GlobalIdGenerator.class);
    final PaymentLinkManagementService service = new PaymentLinkManagementService(links, access, events, policy, tokens, properties, ids);
    final Actor actor = new Actor();
    final String raw = "11111111111111111111111111111111." + "a".repeat(64);
    PaymentLinkDO row;
    @BeforeAll static void initializeMapping() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), PaymentLinkDO.class);
    }
    @BeforeEach void setup() {
        actor.setAppCode("ADMIN"); actor.setActorId("operator"); actor.setMerchantIds(Set.of("m1"));
        row = new PaymentLinkDO(); row.setLinkId(raw.substring(0,32)); row.setMerchantId("m1");
        row.setMerchantOrderNo("ORDER-1"); row.setAmount(new BigDecimal("100")); row.setCurrency("USD");
        row.setCurrencyExponent(2); row.setAllowedBrandsJson("[\"VISA\"]"); row.setLocale("en-US");
        row.setLinkName("Invoice"); row.setMerchantDisplayName("Merchant"); row.setStatus("ACTIVE");
        row.setVersion(0); row.setExpireTime(LocalDateTime.now().plusHours(1));
        var profile = new MerchantRuntimeProfile(); profile.setMerchantName("Merchant");
        when(policy.merchant("m1")).thenReturn(profile); when(policy.validate(any())).thenReturn(new BigDecimal("100.00"));
        when(policy.exponent("USD")).thenReturn(2);
        var method = new AllowedMethod(); method.setPaymentMethod("BANK_CARD"); method.setBrands(List.of("VISA"));
        when(policy.allowedMethods(eq("m1"), eq("USD"), any())).thenReturn(List.of(method));
        when(ids.nextId()).thenReturn("unique-id"); properties.setActiveKeyVersion("v1");
        when(tokens.token(any(), any())).thenReturn(raw); when(links.lock(row.getLinkId())).thenReturn(row);
    }
    private Create input() {
        var c = new Create(); c.setRequestId("create-1"); c.setLinkName("Invoice"); c.setMerchantOrderNo("ORDER-1");
        c.setCurrency("USD"); c.setAmount("100.00"); c.setCardBrands(List.of("VISA")); return c;
    }
    @Test void createsOnlyLinkFactsAndRecordsRealAdmin() {
        service.create(actor,"m1",input());
        var capture=ArgumentCaptor.forClass(PaymentLinkDO.class); verify(links).insert(capture.capture());
        assertThat(capture.getValue().getCreatedAppCode()).isEqualTo("ADMIN");
        assertThat(capture.getValue().getMerchantId()).isEqualTo("m1");
        assertThat(capture.getValue().getAmount()).isEqualByComparingTo("100.00");
        assertThat(capture.getValue().getExpireTime()).isBeforeOrEqualTo(LocalDateTime.now().plusHours(24));

        var cred=ArgumentCaptor.forClass(PaymentLinkAccessDO.class); verify(access).insert(cred.capture());
        assertThat(cred.getValue().getTokenHash()).isEqualTo(PaymentLinkTokenService.hash(raw)).doesNotContain(raw);
    }
    @Test void replayReturnsOriginalWithoutCreatingAnotherLink() {
        service.create(actor,"m1",input()); var c=ArgumentCaptor.forClass(PaymentLinkDO.class); verify(links).insert(c.capture());
        when(links.selectOne(any())).thenReturn(c.getValue()); service.create(actor,"m1",input());
        verify(links,times(1)).insert(any(PaymentLinkDO.class));
        Create changed=input(); changed.setDescription("different");
        assertThatThrownBy(()->service.create(actor,"m1",changed)).isInstanceOf(ServiceException.class)
                .extracting("code").isEqualTo(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT.getCode());
    }
    @Test void existingSuccessfulCoreOrderCannotCreateSecondPaymentEntry() {
        when(links.countOrderFlow("M1:ORDER-1")).thenReturn(1L);
        assertThatThrownBy(()->service.create(actor,"m1",input())).isInstanceOf(ServiceException.class);
        verify(links,never()).insert(any(PaymentLinkDO.class));
    }
    @Test void anExistingCheckoutOrderCannotReceiveAnotherIndependentLink() {
        when(links.countSession("m1", "ORDER-1")).thenReturn(1L);
        assertThatThrownBy(()->service.create(actor,"m1",input())).isInstanceOf(ServiceException.class);
        verify(links,never()).insert(any(PaymentLinkDO.class));
    }
    @Test void disableWritesOnlyExpectedVersionAndRecordsTrustedOperator() {
        when(links.update(isNull(),any())).thenReturn(1);
        service.disable(actor,row.getLinkId(),0,"Operator request","disable-1");
        var event=ArgumentCaptor.forClass(PaymentLinkEventDO.class);verify(events).insert(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("DISABLED");
        assertThat(event.getValue().getActorId()).isEqualTo("operator");
        assertThat(event.getValue().getActorAppCode()).isEqualTo("ADMIN");

    }
    @Test void disablingWithStaleVersionCannotChangeLink() {
        assertThatThrownBy(()->service.disable(actor,row.getLinkId(),1,"Reason","disable-1")).isInstanceOf(ServiceException.class);
        verify(links,never()).update(isNull(),any());verifyNoInteractions(events);
    }
    @Test void disableReplayKeepsOriginalStateAndCannotChangeReason() {
        row.setStatus("DISABLED");row.setDisableRequestId("disable-1");row.setDisabledBy("ADMIN:operator");row.setDisableReason("Reason");
        service.disable(actor,row.getLinkId(),0,"Reason","disable-1");
        assertThatThrownBy(()->service.disable(actor,row.getLinkId(),0,"Different reason","disable-1")).isInstanceOf(ServiceException.class);
        verify(links,never()).update(isNull(),any());verifyNoInteractions(events);
    }
    @Test void completedOrExpiredLinkCannotBeDisabledAgain() {
        when(links.paymentStatus(row.getLinkId())).thenReturn("SUCCESS");
        assertThatThrownBy(()->service.disable(actor,row.getLinkId(),0,"Reason","disable-1")).isInstanceOf(ServiceException.class);
        verify(links,never()).update(isNull(),any());verifyNoInteractions(events);
    }
    @Test void optionalNameAndGeneratedBusinessIdsUseExistingSystemGenerator() {
        Create command = input(); command.setLinkName(null); command.setMerchantOrderNo(null);
        when(ids.nextId()).thenReturn("2026111810000000000001", "2026111810000000000002", "2026111810000000000003");
        service.create(actor, "m1", command);
        var captured = ArgumentCaptor.forClass(PaymentLinkDO.class); verify(links).insert(captured.capture());
        assertThat(captured.getValue().getMerchantOrderNo()).isEqualTo("2026111810000000000001");
        assertThat(captured.getValue().getLinkId()).isEqualTo("L2026111810000000000002");
        assertThat(captured.getValue().getLinkName()).isEmpty();
    }
    @Test void customDeadlineIsFrozenAndReplayDoesNotRevalidateAnExpiredOriginalDeadline() {
        Create command = input(); command.setExpireTime(LocalDateTime.now().plusDays(3).withNano(0));
        service.create(actor, "m1", command);
        var captured = ArgumentCaptor.forClass(PaymentLinkDO.class); verify(links).insert(captured.capture());
        assertThat(captured.getValue().getExpireTime()).isEqualTo(command.getExpireTime());
        captured.getValue().setExpireTime(LocalDateTime.now().minusDays(1));
        when(links.selectOne(any())).thenReturn(captured.getValue());
        service.create(actor, "m1", command);
        verify(links, times(1)).insert(any(PaymentLinkDO.class));
    }
}
