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
 private final PaymentLinkInternalClient payment=mock(PaymentLinkInternalClient.class);

 private final MerchantPaymentLinkApplicationService service=new MerchantPaymentLinkApplicationService(payment);
 private InternalAuthAccount account;
 @BeforeEach void setup() {account=new InternalAuthAccount();account.setAppCode("MERCHANT");account.setAccountId(42L);account.setMerchantId("m1"); InternalAuthContextHolder.set(account);}
 @AfterEach void cleanup(){InternalAuthContextHolder.clear();}
 @Test void callerIdentityAndScopeAreFrozenInOwnerCommand() {service.search(new Query());var command=ArgumentCaptor.forClass(Command.class);verify(payment).executePaymentLink(eq("search"),command.capture(),any());assertThat(command.getValue().getActor().getActorId()).isEqualTo("42");assertThat(command.getValue().getActor().getAppCode()).isEqualTo("MERCHANT");assertThat(command.getValue().getActor().getMerchantIds()).containsExactly("m1");assertThat(command.getValue().getActor().isAllMerchants()).isFalse();}
 @Test void missingAuthenticationRejectsBeforeOwnerCall() {InternalAuthContextHolder.clear();assertThatThrownBy(()->service.search(new Query())).isInstanceOf(ServiceException.class);verifyNoInteractions(payment);}
 @Test void wrongApplicationCannotUseFacade() {account.setAppCode("OTHER");assertThatThrownBy(()->service.search(new Query())).isInstanceOf(ServiceException.class);verifyNoInteractions(payment);}
 @Test void requestCannotSelectAnotherMerchant() {assertThatThrownBy(()->service.options("m2")).isInstanceOf(ServiceException.class);verifyNoInteractions(payment);}

}
