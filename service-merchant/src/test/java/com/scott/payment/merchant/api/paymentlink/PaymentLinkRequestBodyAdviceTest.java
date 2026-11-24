package com.scott.payment.merchant.api.paymentlink;
import com.scott.payment.merchant.application.paymentlink.MerchantPaymentLinkApplicationService;
import com.scott.payment.merchant.dto.paymentlink.PaymentLinkDTOs.Create;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import com.scott.payment.component.web.config.FastJsonWebMvcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * 使用生产 Fastjson 转换器验证未开放金额模式、重复成功和操作者字段不能进入创建用例。
 */
class PaymentLinkRequestBodyAdviceTest {
 @Test void rejectsUnapprovedFieldsBeforeCallingTheApplication() throws Exception {
  var app=mock(MerchantPaymentLinkApplicationService.class);
  var mvc = mvc(app);
  for(String field:java.util.List.of("allowMultipleSuccess","amountMode","actor")) {
   mvc.perform(post("/merchant/transactions/payment-links").param("merchantId","QA")
     .contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"qa\",\""+field+"\":true}"))
     .andExpect(status().isBadRequest());
  }
  verifyNoInteractions(app);
 }

 @Test void passesApprovedPaymentMethodKeysToTheApplication() throws Exception {
  var app = mock(MerchantPaymentLinkApplicationService.class);
  mvc(app).perform(post("/merchant/transactions/payment-links").param("merchantId", "200046")
      .contentType(MediaType.APPLICATION_JSON)
      .content("""
          {
            "requestId": "req001-keys",
            "currency": "USD",
            "amount": "7.00",
            "paymentMethodKeys": ["CARD_BRAND:VISA", "METHOD:APPLE_PAY"]
          }
          """))
      .andExpect(status().isOk());

  var request = ArgumentCaptor.forClass(Create.class);
  verify(app).create(eq("200046"), request.capture());
  assertThat(request.getValue().getPaymentMethodKeys())
      .containsExactly("CARD_BRAND:VISA", "METHOD:APPLE_PAY");
 }

 private MockMvc mvc(MerchantPaymentLinkApplicationService app) {
  var converters = new java.util.ArrayList<HttpMessageConverter<?>>();
  new FastJsonWebMvcConfig().extendMessageConverters(converters);
  return MockMvcBuilders.standaloneSetup(new MerchantPaymentLinkController(app))
      .setControllerAdvice(new PaymentLinkRequestBodyAdvice())
      .setMessageConverters(converters.toArray(HttpMessageConverter<?>[]::new))
      .build();
 }
}
