package com.scott.payment.admin.api.base;

import com.scott.payment.admin.application.base.AdminBaseCurrencyApplicationService;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 管理端币种展示字典路由契约测试。
 */
class AdminBaseCurrencyControllerContractTests {

    @Test
    void presentationRouteShouldBeAvailableToAuthenticatedAdminPages() throws Exception {
        RequestMapping root = AdminBaseCurrencyController.class.getAnnotation(RequestMapping.class);
        Method method = AdminBaseCurrencyController.class.getMethod("presentations");

        assertThat(root.value()).containsExactly("/admin/base/currencies");
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/presentations");
        assertThat(method.getAnnotation(RequiresPermission.class)).isNull();

        IsoDictionaryService dictionaryService = mock(IsoDictionaryService.class);
        AdminBaseCurrencyController controller = new AdminBaseCurrencyController(
                mock(AdminBaseCurrencyApplicationService.class), dictionaryService);
        controller.presentations();
        verify(dictionaryService).listCurrencyPresentations();
    }
}
