package com.scott.payment.merchant.application.paymentlink;
import com.scott.payment.merchant.client.payment.PaymentLinkInternalClient;
import com.scott.payment.merchant.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
/** 验证登录主体和商户范围来自服务端上下文，不接受页面的越权选择。 */
class MerchantPaymentLinkScopeTest {
 private final com.scott.payment.link.service.PaymentLinkManagementService management=mock(com.scott.payment.link.service.PaymentLinkManagementService.class);
 private final com.scott.payment.link.service.PaymentLinkManagementPolicy policy=mock(com.scott.payment.link.service.PaymentLinkManagementPolicy.class);
 private final PaymentLinkInternalClient payment=mock(PaymentLinkInternalClient.class);

 private final MerchantPaymentLinkQueryService queries=mock(MerchantPaymentLinkQueryService.class);
 private final MerchantPaymentLinkApplicationService service=new MerchantPaymentLinkApplicationService(management, policy,mock(MerchantPaymentLinkOperatorProjection.class),queries);
 private InternalAuthAccount account;
 @BeforeEach void setup() {account=new InternalAuthAccount();account.setAppCode("MERCHANT");account.setAccountId(42L);account.setMerchantId("m1"); InternalAuthContextHolder.set(account);}
 @AfterEach void cleanup(){InternalAuthContextHolder.clear();}
 @Test void callerIdentityAndScopeAreFrozenInLocalQuery() {
  service.search(new Query());var actor=ArgumentCaptor.forClass(Actor.class);
  verify(queries).search(actor.capture(),any());
  assertThat(actor.getValue().getActorId()).isEqualTo("42");
  assertThat(actor.getValue().getAppCode()).isEqualTo("MERCHANT");
  assertThat(actor.getValue().getMerchantIds()).containsExactly("m1");
  assertThat(actor.getValue().isAllMerchants()).isFalse();verifyNoInteractions(payment);
 }
 @Test void writesExecuteLocallyWithTrustedScope() {
  when(management.create(any(),eq("m1"),any())).thenReturn("link");
  service.create("m1",new Create());var actor=ArgumentCaptor.forClass(com.scott.payment.link.model.PaymentLinkManagementModels.Actor.class);
  verify(management).create(actor.capture(),eq("m1"),any());
  assertThat(actor.getValue().getMerchantIds()).containsExactly("m1");
  verify(queries).detail(any(),eq("link"));verifyNoInteractions(payment);
 }

 @Test void missingAuthenticationRejectsBeforeOwnerCall() {InternalAuthContextHolder.clear();assertThatThrownBy(()->service.search(new Query())).isInstanceOf(ServiceException.class);verifyNoInteractions(payment);}
 @Test void wrongApplicationCannotUseFacade() {account.setAppCode("OTHER");assertThatThrownBy(()->service.search(new Query())).isInstanceOf(ServiceException.class);verifyNoInteractions(payment);}
 @Test void requestCannotSelectAnotherMerchant() {assertThatThrownBy(()->service.options("m2")).isInstanceOf(ServiceException.class);verifyNoInteractions(payment);}

}
