package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminTransactionApplicationService;
import com.scott.payment.component.core.auth.InternalAuthChecker;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.web.auth.InternalAuthInterceptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminTransactionSearchAuthorizationTests {

    @ParameterizedTest
    @CsvSource({
            "/admin/transactions/operations/summary, transaction:operation:list",
            "/admin/transactions/orders/summary, transaction:order:list"
    })
    void splitEndpointsRejectRequestsWithoutExistingListPermission(String path, String permission) throws Exception {
        String authorization = "Bearer denied-token";
        var applicationService = mock(AdminTransactionApplicationService.class);
        var checker = mock(InternalAuthChecker.class);
        when(checker.check("ADMIN", authorization, "POST", path, permission))
                .thenThrow(new ServiceException(ApiResultEnum.FORBIDDEN));

        var mvc = standaloneSetup(new AdminTransactionOperationController(applicationService),
                new AdminTransactionOrderController(applicationService))
                .addInterceptors(new InternalAuthInterceptor("ADMIN", checker, List.of()))
                .build();
        mvc.perform(post(path).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verify(checker).check("ADMIN", authorization, "POST", path, permission);
        verifyNoInteractions(applicationService);
    }
}
