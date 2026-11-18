package com.scott.payment.merchant.application.paymentlink;
import com.scott.payment.merchant.config.MerchantPaymentLinkMailProperties;
import com.scott.payment.merchant.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.merchant.entity.paymentlink.PaymentLinkDeliveryDO;
import com.scott.payment.merchant.mapper.PaymentLinkDeliveryMapper;
import com.scott.payment.merchant.service.MerchantTemplateEmailService;
import com.scott.payment.merchant.service.MerchantTemplateEmailService.MerchantEmailSendResult;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.exception.RateLimitExceededException;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

/** 持久请求与邮件独立提交边界的行为回归，不连接 SMTP。 */
class MerchantPaymentLinkMailApplicationServiceTest {
 private final MerchantPaymentLinkApplicationService links=mock(MerchantPaymentLinkApplicationService.class);
 private final PaymentLinkDeliveryMapper requests=mock(PaymentLinkDeliveryMapper.class);
 private final MerchantTemplateEmailService emails=mock(MerchantTemplateEmailService.class);
 private final RedisSlidingWindowRateLimiter limiter=mock(RedisSlidingWindowRateLimiter.class);
 private final MerchantPaymentLinkDeliveryClaimService claims=mock(MerchantPaymentLinkDeliveryClaimService.class);
 private final MerchantPaymentLinkMailApplicationService service=new MerchantPaymentLinkMailApplicationService(links,requests,emails,limiter,new MerchantPaymentLinkMailProperties(),Validation.buildDefaultValidatorFactory().getValidator(),claims);
 private final SendEmail input=new SendEmail("payer@example.invalid","en-US","request-1");
 private PaymentLinkDeliveryDO row;
 @BeforeEach void setup() {
  Actor actor=new Actor(); actor.setAppCode("MERCHANT"); actor.setActorId("42"); when(links.actor()).thenReturn(actor);
  View view=new View(); view.setMerchantId("m1"); view.setMerchantDisplayName("Merchant"); view.setLinkName("Invoice"); view.setAmount("10.00"); view.setCurrency("USD"); view.setMerchantOrderNo("ORDER-1"); view.setExpireTime(LocalDateTime.now().plusHours(1)); when(links.detail("link-1")).thenReturn(view);
  row=new PaymentLinkDeliveryDO(); row.setMerchantId("m1"); row.setLinkId("link-1"); row.setRequestId("request-1"); row.setBizNo("stable"); row.setRecipientMasked("p***@example.invalid"); row.setLocale("en-US"); row.setCreatedBy("42");
  doAnswer(invocation->{ row.setFingerprint(((PaymentLinkDeliveryDO)invocation.getArgument(0)).getFingerprint());return null; }).when(claims).claim(any());
  when(requests.lock("m1","request-1")).thenReturn(row);
  when(claims.findCommittedEmail(anyString(),anyString())).thenReturn(null);
  when(limiter.tryAcquire(anyString(),anyString(),anyInt(),any())).thenReturn(SlidingWindowRateLimitResult.permitted());
  Share share=new Share();share.setUrl("https://pay.example.invalid/l/credential");when(links.share("link-1")).thenReturn(share);
  MerchantEmailSendResult result=new MerchantEmailSendResult(71L,0);
  when(emails.sendByTemplate(any())).thenReturn(result);when(requests.status(71L)).thenReturn(0);
 }
 @Test void sendsOnceAndReturnsOnlyMaskedProjection() { Delivery d=service.send("link-1",input);assertThat(d.getEmailRecordId()).isEqualTo(71L);assertThat(d.getRecipientMasked()).doesNotContain("payer");verify(claims).claim(any());verify(emails).sendByTemplate(any());verify(requests).updateById(row); }
 @Test void committedRequestReplayDoesNotSendAgainOrConsumeQuota() {row.setEmailRecordId(71L);service.send("link-1",input);verifyNoInteractions(emails,limiter);}
 @Test void responseLossRecoversPreviouslyCommittedEmailWithoutSending() {when(claims.findCommittedEmail("m1","stable")).thenReturn(71L);service.send("link-1",input);verifyNoInteractions(emails,limiter);assertThat(row.getEmailRecordId()).isEqualTo(71L);}
 @Test void sameRequestWithChangedContentRejects() {doNothing().when(claims).claim(any());row.setFingerprint("different");assertThatThrownBy(()->service.send("link-1",input)).isInstanceOf(ServiceException.class);verifyNoInteractions(emails,limiter);}
 @Test void rejectsRateLimitBeforeLinkDisclosureOrEmailPreparation() {when(limiter.tryAcquire(anyString(),anyString(),anyInt(),any())).thenReturn(SlidingWindowRateLimitResult.rejected(60));assertThatThrownBy(()->service.send("link-1",input)).isInstanceOf(RateLimitExceededException.class);verify(links,never()).share(anyString());verifyNoInteractions(emails);}
 @Test void scopeFailureOccursBeforeDurableClaim() {when(links.detail("link-1")).thenThrow(new ServiceException("T403","forbidden"));assertThatThrownBy(()->service.send("link-1",input)).isInstanceOf(ServiceException.class);verifyNoInteractions(claims,requests,emails,limiter);}
 @Test void invalidRecipientRejectsBeforeAnySideEffect() {assertThatThrownBy(()->service.send("link-1",new SendEmail("invalid","en-US","bad"))).isInstanceOf(ServiceException.class);verifyNoInteractions(claims,requests,emails,limiter);}
}
