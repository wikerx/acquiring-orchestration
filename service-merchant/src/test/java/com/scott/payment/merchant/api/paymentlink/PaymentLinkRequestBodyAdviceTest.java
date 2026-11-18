package com.scott.payment.merchant.api.paymentlink;
import com.scott.payment.merchant.application.paymentlink.MerchantPaymentLinkApplicationService;
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
  var converters=new java.util.ArrayList<HttpMessageConverter<?>>();new FastJsonWebMvcConfig().extendMessageConverters(converters);
  var mvc=MockMvcBuilders.standaloneSetup(new MerchantPaymentLinkController(app)).setControllerAdvice(new PaymentLinkRequestBodyAdvice())
      .setMessageConverters(converters.toArray(HttpMessageConverter<?>[]::new)).build();
  for(String field:java.util.List.of("allowMultipleSuccess","amountMode","actor")) {
   mvc.perform(post("/merchant/transactions/payment-links").param("merchantId","QA")
     .contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"qa\",\""+field+"\":true}"))
     .andExpect(status().isBadRequest());
  }
  verifyNoInteractions(app);
 }
}
