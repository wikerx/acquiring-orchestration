package com.scott.payment.merchant.api.system;

import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.merchant.service.MerchantDictService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 商户端币种展示字典路由契约测试。
 */
class MerchantDictControllerContractTests {

    @Test
    void presentationRouteShouldBeAvailableToAuthenticatedMerchantPages() throws Exception {
        RequestMapping root = MerchantDictController.class.getAnnotation(RequestMapping.class);
        Method method = MerchantDictController.class.getMethod("currencyPresentations");

        assertThat(root.value()).containsExactly("/merchant/system/dicts");
        assertThat(method.getAnnotation(GetMapping.class).value())
                .containsExactly("/currency-presentations");
        assertThat(method.getAnnotation(RequiresPermission.class)).isNull();

        IsoDictionaryService dictionaryService = mock(IsoDictionaryService.class);
        MerchantDictController controller = new MerchantDictController(
                mock(MerchantDictService.class), dictionaryService);
        controller.currencyPresentations();
        verify(dictionaryService).listCurrencyPresentations();
    }
}
