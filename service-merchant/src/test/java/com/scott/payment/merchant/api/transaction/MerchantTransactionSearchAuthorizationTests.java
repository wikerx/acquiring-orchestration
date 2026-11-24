package com.scott.payment.merchant.api.transaction;

import com.scott.payment.merchant.application.transaction.MerchantTransactionApplicationService;
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

class MerchantTransactionSearchAuthorizationTests {

    @ParameterizedTest
    @CsvSource({
            "/merchant/transactions/orders/operations/page, merchant:transaction:order:list",
            "/merchant/transactions/orders/operations/summary, merchant:transaction:order:list"
    })
    void splitEndpointsRejectRequestsWithoutExistingListPermission(String path, String permission) throws Exception {
        String authorization = "Bearer denied-token";
        var applicationService = mock(MerchantTransactionApplicationService.class);
        var checker = mock(InternalAuthChecker.class);
        when(checker.check("MERCHANT", authorization, "POST", path, permission))
                .thenThrow(new ServiceException(ApiResultEnum.FORBIDDEN));

        var mvc = standaloneSetup(new MerchantTransactionOrderController(applicationService))
                .addInterceptors(new InternalAuthInterceptor("MERCHANT", checker, List.of()))
                .build();
        mvc.perform(post(path).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verify(checker).check("MERCHANT", authorization, "POST", path, permission);
        verifyNoInteractions(applicationService);
    }
}
