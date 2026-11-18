package com.scott.payment.payment.service.link;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentLinkDTOs.*;
import com.scott.payment.payment.config.PaymentLinkProperties;
import com.scott.payment.payment.entity.*;
import com.scott.payment.payment.mapper.*;
import com.scott.payment.payment.service.PaymentCheckoutService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentLinkServiceTest {
    private final PaymentLinkMapper links = mock(PaymentLinkMapper.class);
    private final PaymentLinkAccessMapper access = mock(PaymentLinkAccessMapper.class);
    private final PaymentLinkCollectionMapper collections = mock(PaymentLinkCollectionMapper.class);
    private final PaymentLinkCheckoutBindingMapper bindings = mock(PaymentLinkCheckoutBindingMapper.class);
    private final PaymentLinkEventMapper events = mock(PaymentLinkEventMapper.class);
    private final PaymentCheckoutSessionMapper sessions = mock(PaymentCheckoutSessionMapper.class);
    private final PaymentCheckoutService checkout = mock(PaymentCheckoutService.class);
    private final TransactionIdempotencyService idem = mock(TransactionIdempotencyService.class);
    private final PaymentLinkPolicy policy = mock(PaymentLinkPolicy.class);
    private final PaymentLinkQueryService queries = mock(PaymentLinkQueryService.class);
    private final PaymentLinkTokenService tokens = mock(PaymentLinkTokenService.class);
    private final PaymentLinkProperties properties = new PaymentLinkProperties();
    private final GlobalIdGenerator ids = mock(GlobalIdGenerator.class);
    private final PaymentLinkService service = new PaymentLinkService(links, access, collections, bindings,
            events, sessions, checkout, idem, policy, queries, tokens, properties, ids);
    private final Actor actor = new Actor();
    private final String raw = "11111111111111111111111111111111." + "a".repeat(64);
    private PaymentLinkDO row;

    @BeforeEach void setup() {
        actor.setAppCode("ADMIN"); actor.setActorId("operator"); actor.setMerchantIds(Set.of("m1"));
        row = new PaymentLinkDO(); row.setLinkId(raw.substring(0,32)); row.setMerchantId("m1");
        row.setMerchantOrderNo("ORDER-1"); row.setAmount(new BigDecimal("100")); row.setCurrency("USD");
        row.setCurrencyExponent(2); row.setAllowedBrandsJson("[\"VISA\"]"); row.setLocale("en-US");
        row.setLinkName("Invoice"); row.setMerchantDisplayName("Merchant"); row.setStatus("ACTIVE");
        row.setVersion(0); row.setExpireTime(LocalDateTime.now().plusHours(1));
        var profile = new MerchantRuntimeProfile(); profile.setMerchantName("Merchant");
        when(policy.merchant("m1")).thenReturn(profile);
        when(policy.validate(any())).thenReturn(new BigDecimal("100.00")); when(policy.exponent("USD")).thenReturn(2);
        var method = new PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO();
        method.setPaymentMethod("BANK_CARD"); method.setBrands(List.of("VISA"));
        when(policy.methods(eq("m1"), eq("USD"), any())).thenReturn(List.of(method));
        when(policy.currentMethods(eq("m1"), eq("USD"), any())).thenReturn(List.of(method));
        when(ids.nextId()).thenReturn("unique-id"); properties.setActiveKeyVersion("v1");
        properties.setFrontendBaseUrl("https://pay.example.invalid");
        when(tokens.token(any(),any())).thenReturn(raw);
        var credential = new PaymentLinkAccessDO(); credential.setLinkId(row.getLinkId());
        credential.setTokenHash(PaymentLinkTokenService.hash(raw)); credential.setKeyVersion("v1");
        when(access.selectOne(any())).thenReturn(credential); when(tokens.matches(raw,credential.getTokenHash())).thenReturn(true);
        when(links.lock(row.getLinkId())).thenReturn(row);
        View view = new View(); view.setStatus("ACTIVE"); when(queries.detail(any(), any())).thenReturn(view);
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
        verifyNoInteractions(checkout, collections, bindings);
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
        var flow=new TransactionIdempotencyDO(); flow.setTransactionStatus("SUCCESS");
        when(idem.buildMerchantOrderFlowKey("m1","ORDER-1")).thenReturn("m1:ORDER-1");
        when(idem.find("MERCHANT_ORDER_FLOW","m1:ORDER-1")).thenReturn(java.util.Optional.of(flow));
        assertThatThrownBy(()->service.create(actor,"m1",input())).isInstanceOf(ServiceException.class);
        verify(links,never()).insert(any(PaymentLinkDO.class)); verifyNoInteractions(checkout);
    }
    @Test void publicResolveHasNoSideEffectsOrPriorPayerDetails() {
        when(links.selectOne(any())).thenReturn(row);
        PublicView result=service.resolve(raw);
        assertThat(result.isPayable()).isTrue();
        assertThat(JsonUtils.toJsonString(result)).doesNotContain("notifyUrl","redirectUrl","createdBy","cardNumber","email");
        verifyNoInteractions(checkout, collections, events);
    }
    @Test void tokenTamperingCannotReadOrStart() {
        when(tokens.matches(raw,PaymentLinkTokenService.hash(raw))).thenReturn(false);
        assertThatThrownBy(()->service.resolve(raw)).isInstanceOf(ServiceException.class);
        verifyNoInteractions(checkout, collections, bindings);
    }
    @Test void expiredLinkCannotCreateCheckout() {
        when(links.selectOne(any())).thenReturn(row); row.setExpireTime(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(()->service.start(raw,"start-1")).isInstanceOf(ServiceException.class);
        verifyNoInteractions(checkout, collections);
    }
    @Test void disabledLinkCannotCreateCheckout() {
        when(links.selectOne(any())).thenReturn(row); row.setStatus("DISABLED");
        assertThatThrownBy(()->service.start(raw,"start-1")).isInstanceOf(ServiceException.class);
        verifyNoInteractions(checkout, collections);
    }
    @Test void confirmedSuccessIsNeverOpenedAgain() {
        when(links.selectOne(any())).thenReturn(row);
        var b=new PaymentLinkCheckoutBindingDO(); b.setCheckoutSessionId("session"); when(bindings.selectOne(any())).thenReturn(b);
        var s=new PaymentCheckoutSessionDO(); s.setCheckoutStatus("SUCCESS"); when(sessions.selectByCheckoutSessionId("session")).thenReturn(s);
        assertThat(service.resolve(raw).getStatus()).isEqualTo("COMPLETED");
        assertThatThrownBy(()->service.start(raw,"start-2")).isInstanceOf(ServiceException.class);
        verifyNoInteractions(checkout,collections);
    }
    @Test void retryAndOtherDevicesReuseSameFrozenMerchantOrderAndSessionIdentity() {
        when(links.selectOne(any())).thenReturn(row);
        var b=new PaymentLinkCheckoutBindingDO(); b.setCheckoutSessionId("session"); when(bindings.selectOne(any())).thenReturn(b);
        var s=new PaymentCheckoutSessionDO(); s.setCheckoutStatus("FAILED"); s.setRetryAllowed(1); s.setLastSubmitTime(LocalDateTime.now()); s.setExpireTime(row.getExpireTime()); when(sessions.selectByCheckoutSessionId("session")).thenReturn(s);
        var result=new PaymentCheckoutSessionCreateResultDTO(); result.setCheckoutSessionId("session"); result.setCheckoutUrl("https://pay.example.invalid/checkout/safe");
        when(checkout.createSession(any())).thenReturn(result);
        service.start(raw,"device-1"); service.start(raw,"device-2");
        var commands=ArgumentCaptor.forClass(PaymentCheckoutSessionCreateCommandDTO.class); verify(checkout,times(2)).createSession(commands.capture());
        assertThat(commands.getAllValues()).allSatisfy(c->{
            assertThat(c.getMerchantOrderNo()).isEqualTo("ORDER-1"); assertThat(c.getPaymentAction()).isEqualTo("PAYMENT");
            assertThat(c.getMerchantRequestId()).isEqualTo("PAYMENT_LINK:"+row.getLinkId());
            assertThat(c.getExpireTime()).isEqualTo(row.getExpireTime());
        });
        verifyNoInteractions(collections); verify(bindings,never()).insert(any(PaymentLinkCheckoutBindingDO.class));
    }
    @Test void disabledLinkKeepsInFlightResultSeparate() {
        when(links.selectOne(any())).thenReturn(row); row.setStatus("DISABLED");
        var b=new PaymentLinkCheckoutBindingDO(); b.setCheckoutSessionId("session"); when(bindings.selectOne(any())).thenReturn(b);
        var s=new PaymentCheckoutSessionDO(); s.setCheckoutStatus("PROCESSING"); when(sessions.selectByCheckoutSessionId("session")).thenReturn(s);
        assertThat(service.resolve(raw).getStatus()).isEqualTo("DISABLED");
        assertThat(service.resolve(raw).isPayable()).isFalse(); verifyNoInteractions(checkout);
    }
    @Test void processingLinkProjectsWaitAndCannotIssueANewPayerToken() {
        when(links.selectOne(any())).thenReturn(row);
        var b=new PaymentLinkCheckoutBindingDO();b.setCheckoutSessionId("session");when(bindings.selectOne(any())).thenReturn(b);
        var session=new PaymentCheckoutSessionDO();session.setCheckoutStatus("PROCESSING");when(sessions.selectByCheckoutSessionId("session")).thenReturn(session);
        assertThat(service.resolve(raw).isProcessing()).isTrue();
        assertThat(service.resolve(raw).isPayable()).isFalse();
        assertThatThrownBy(()->service.start(raw,"another-device")).isInstanceOf(ServiceException.class);
        verifyNoInteractions(checkout,collections);
    }
    @Test void anExistingCheckoutOrderCannotReceiveAnotherIndependentLink() {
        when(sessions.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(()->service.create(actor,"m1",input())).isInstanceOf(ServiceException.class);
        verify(links,never()).insert(any(PaymentLinkDO.class));verifyNoInteractions(checkout,collections);
    }
    @Test void disableWritesOnlyExpectedVersionAndRecordsTrustedOperator() {
        when(links.update(isNull(),any())).thenReturn(1);
        service.disable(actor,row.getLinkId(),0,"Operator request","disable-1");
        var event=ArgumentCaptor.forClass(PaymentLinkEventDO.class);verify(events).insert(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("DISABLED");
        assertThat(event.getValue().getActorId()).isEqualTo("operator");
        assertThat(event.getValue().getActorAppCode()).isEqualTo("ADMIN");
        verifyNoInteractions(checkout);
    }
    @Test void disablingWithStaleVersionCannotChangeLink() {
        assertThatThrownBy(()->service.disable(actor,row.getLinkId(),1,"Reason","disable-1")).isInstanceOf(ServiceException.class);
        verify(links,never()).update(isNull(),any());verifyNoInteractions(events,checkout);
    }
    @Test void disableReplayKeepsOriginalStateAndCannotChangeReason() {
        row.setStatus("DISABLED");row.setDisableRequestId("disable-1");row.setDisabledBy("ADMIN:operator");row.setDisableReason("Reason");
        service.disable(actor,row.getLinkId(),0,"Reason","disable-1");
        assertThatThrownBy(()->service.disable(actor,row.getLinkId(),0,"Different reason","disable-1")).isInstanceOf(ServiceException.class);
        verify(links,never()).update(isNull(),any());verifyNoInteractions(events,checkout);
    }
    @Test void completedOrExpiredLinkCannotBeDisabledAgain() {
        View completed=new View();completed.setStatus("COMPLETED");when(queries.detail(actor,row.getLinkId())).thenReturn(completed);
        assertThatThrownBy(()->service.disable(actor,row.getLinkId(),0,"Reason","disable-1")).isInstanceOf(ServiceException.class);
        verify(links,never()).update(isNull(),any());verifyNoInteractions(events,checkout);
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
    @Test void expiredUnvisitedLinkRemainsReadOnlyWithoutCreatingAnOrder() {
        when(links.selectOne(any())).thenReturn(row); row.setExpireTime(LocalDateTime.now().minusDays(1));
        PublicView view = service.resolve(raw);
        assertThat(view.getStatus()).isEqualTo("EXPIRED"); assertThat(view.isPayable()).isFalse();
        assertThat(view.getLastFailedTime()).isNull(); verifyNoInteractions(checkout, collections, events);
        verify(links, never()).attempts(anyString(), anyLong(), anyInt());
    }
    @Test void expiredFailedLinkShowsOnlyLatestConfirmedFailureWhileProcessingAndSuccessTakePrecedence() {
        when(links.selectOne(any())).thenReturn(row); row.setExpireTime(LocalDateTime.now().minusDays(1));
        var binding = new PaymentLinkCheckoutBindingDO(); binding.setCheckoutSessionId("session");
        when(bindings.selectOne(any())).thenReturn(binding);
        var session = new PaymentCheckoutSessionDO(); session.setCheckoutStatus("FAILED");
        when(sessions.selectByCheckoutSessionId("session")).thenReturn(session);
        var latest = new Attempt(); latest.setStatus("FAILED"); latest.setCompleteTime(LocalDateTime.now().minusDays(2));
        when(links.attempts(row.getLinkId(), 0, 1)).thenReturn(List.of(latest));
        assertThat(service.resolve(raw).getLastFailedTime()).isEqualTo(latest.getCompleteTime());
        latest.setStatus("PROCESSING"); assertThat(service.resolve(raw).getLastFailedTime()).isNull();
        assertThat(service.resolve(raw).isProcessing()).isTrue();
        session.setCheckoutStatus("PROCESSING");
        assertThat(service.resolve(raw).isProcessing()).isTrue(); assertThat(service.resolve(raw).getLastFailedTime()).isNull();
        session.setCheckoutStatus("SUCCESS");
        assertThat(service.resolve(raw).getStatus()).isEqualTo("COMPLETED");
        assertThat(service.resolve(raw).getLastFailedTime()).isNull(); verifyNoInteractions(checkout, collections, events);
    }
    @Test void numericBusinessIdTokenStillRequiresItsFullHmacCredential() {
        String token = "2026111810000000000001." + "a".repeat(64);
        when(tokens.matches(eq(token), anyString())).thenReturn(true); when(links.selectOne(any())).thenReturn(row);
        assertThat(service.resolve(token).getMerchantDisplayName()).isEqualTo("Merchant");
        assertThatThrownBy(() -> service.resolve("2026111810000000000001")).isInstanceOf(ServiceException.class);
    }
}
