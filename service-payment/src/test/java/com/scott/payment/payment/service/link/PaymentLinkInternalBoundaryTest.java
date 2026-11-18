package com.scott.payment.payment.service.link;
import com.scott.payment.payment.api.internal.PaymentLinkInternalController;
import com.scott.payment.payment.api.internal.dto.PaymentLinkDTOs.*;
import com.scott.payment.payment.application.PaymentLinkApplicationService;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
/** HMAC 验证之后仍按调用方限定匿名动作和管理动作，避免 OpenAPI 伪造操作员。 */
class PaymentLinkInternalBoundaryTest {
 private final PaymentLinkApplicationService application=mock(PaymentLinkApplicationService.class);
 private final PaymentLinkInternalController controller=new PaymentLinkInternalController(application);
 private MockHttpServletRequest request(String caller){var r=new MockHttpServletRequest();r.addHeader(InternalServiceSignature.HEADER_CALLER,caller);return r;}
 private Command command(String app){var c=new Command();var a=new Actor();a.setAppCode(app);a.setActorId("1");c.setActor(a);return c;}
 @Test void publicServiceCannotCreateEvenWithAnAdminActor(){assertThatThrownBy(()->controller.execute("create",command("ADMIN"),request("service-openapi"))).isInstanceOf(ServiceException.class);verifyNoInteractions(application);}
 @Test void merchantServiceCannotForgeAdminIdentity(){assertThatThrownBy(()->controller.execute("create",command("ADMIN"),request("service-merchant"))).isInstanceOf(ServiceException.class);verifyNoInteractions(application);}
 @Test void dataAndUnknownCallersCannotReadLinkFacts(){assertThatThrownBy(()->controller.execute("detail",command("ADMIN"),request("service-data"))).isInstanceOf(ServiceException.class);verifyNoInteractions(application);}
 @Test void authenticatedAdminCannotUseAnonymousTokenExchange(){assertThatThrownBy(()->controller.execute("start",command("ADMIN"),request("service-admin"))).isInstanceOf(ServiceException.class);verifyNoInteractions(application);}
 @Test void trustedOpenApiMayResolveMinimalPublicProjection(){var c=new Command();controller.execute("resolve",c,request("service-openapi"));verify(application).execute("resolve",c);}
}
