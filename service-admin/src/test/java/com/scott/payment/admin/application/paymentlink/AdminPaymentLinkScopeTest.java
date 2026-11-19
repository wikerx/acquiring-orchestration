package com.scott.payment.admin.application.paymentlink;
import com.scott.payment.admin.client.payment.PaymentLinkInternalClient;
import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
/** 验证登录主体和商户范围来自服务端上下文，不接受页面的越权选择。 */
class AdminPaymentLinkScopeTest {
 private final com.scott.payment.link.service.PaymentLinkManagementService management=mock(com.scott.payment.link.service.PaymentLinkManagementService.class);
 private final com.scott.payment.link.service.PaymentLinkManagementPolicy policy=mock(com.scott.payment.link.service.PaymentLinkManagementPolicy.class);
 private final PaymentLinkInternalClient payment=mock(PaymentLinkInternalClient.class);
 private final AdminMerchantDataScopeResolver scopes=mock(AdminMerchantDataScopeResolver.class);
 private final BaseMerchantInfoMapper merchants=mock(BaseMerchantInfoMapper.class);
 private final com.scott.payment.admin.service.AdminChannelService channels=mock(com.scott.payment.admin.service.AdminChannelService.class);
 private final AdminPaymentLinkQueryService queries=mock(AdminPaymentLinkQueryService.class);
 private final AdminPaymentLinkApplicationService service=new AdminPaymentLinkApplicationService(management, policy,scopes,merchants,channels,mock(AdminPaymentLinkOperatorProjection.class),queries);
 private InternalAuthAccount account;
 @BeforeEach void setup() {account=new InternalAuthAccount();account.setAppCode("ADMIN");account.setAccountId(42L);when(scopes.resolve(account)).thenReturn(AdminMerchantDataScope.limited(Set.of("m1"))); InternalAuthContextHolder.set(account);}
 @AfterEach void cleanup(){InternalAuthContextHolder.clear();}
 @Test void callerIdentityAndScopeAreFrozenInLocalQuery() {
  service.search(new Query());var actor=ArgumentCaptor.forClass(Actor.class);
  verify(queries).search(actor.capture(),any());
  assertThat(actor.getValue().getActorId()).isEqualTo("42");
  assertThat(actor.getValue().getAppCode()).isEqualTo("ADMIN");
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
 @Test void emptyScopeReturnsNoMerchantChoicesWithoutQueryingAll() {when(scopes.resolve(account)).thenReturn(AdminMerchantDataScope.limited(Set.of()));assertThat(service.merchantChoices("")).isEmpty();verifyNoInteractions(merchants);}
 @Test void selectedMerchantMustBeWithinDataScope() {assertThatThrownBy(()->service.options("m2")).isInstanceOf(ServiceException.class);verifyNoInteractions(payment);}


 @Test void filterChoicesUseLocalDictionaryAndOnlyExposeChannelIdentity() {
  when(queries.filterCurrencies(any())).thenReturn(java.util.List.of("USD"));
  var channel=new com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelOption();channel.setChannelCode("QA");channel.setChannelName("QA Channel");
  when(channels.listChannelOptions()).thenReturn(java.util.List.of(channel));
  var result=service.filterChoices();assertThat(result.currencies()).containsExactly("USD");
  assertThat(result.channels()).singleElement().satisfies(c -> assertThat(c.channelCode()).isEqualTo("QA"));
  assertThat(com.scott.payment.component.core.json.JsonUtils.toJsonString(result)).doesNotContain("supportPayout","channelStatus");verifyNoInteractions(payment);
 }
}
