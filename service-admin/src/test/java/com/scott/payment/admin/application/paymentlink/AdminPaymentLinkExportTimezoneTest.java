package com.scott.payment.admin.application.paymentlink;

import com.scott.payment.admin.client.data.DataExportTaskInternalClient;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.CreateRequest;
import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** 验证任务冻结页面显示时区，并保持旧调用缺省契约。 */
class AdminPaymentLinkExportTimezoneTest {
    private final DataExportTaskInternalClient data=mock(DataExportTaskInternalClient.class);
    private final AdminPaymentLinkApplicationService links=mock(AdminPaymentLinkApplicationService.class);
    private final AdminPaymentLinkExportApplicationService service=new AdminPaymentLinkExportApplicationService(data,links);

    @BeforeEach void setup() {
        InternalAuthAccount account=new InternalAuthAccount();account.setAccountId(42L);account.setLoginAccount("qa");InternalAuthContextHolder.set(account);
        Actor actor=new Actor();actor.setAppCode("ADMIN");actor.setActorId("42");actor.setMerchantIds(Set.of("m1"));when(links.actor()).thenReturn(actor);
    }
    @AfterEach void cleanup() {InternalAuthContextHolder.clear();}
    @Test void freezesSelectedTimezone() {
        service.submit("qa-request",new Query(),"UTC");
        var command=ArgumentCaptor.forClass(CreateRequest.class);verify(data).submit(command.capture());
        assertThat(command.getValue().getTimezone()).isEqualTo("UTC");
    }
    @Test void oldRequestDefaultsToSourceTimezone() {
        service.submit("qa-request",new Query(),null);
        var command=ArgumentCaptor.forClass(CreateRequest.class);verify(data).submit(command.capture());
        assertThat(command.getValue().getTimezone()).isEqualTo("Asia/Shanghai");
    }
    @Test void rejectsInvalidTimezoneBeforeSubmitting() {
        assertThatThrownBy(()->service.submit("qa-request",new Query(),"Invalid/Timezone")).isInstanceOf(ServiceException.class);
        verifyNoInteractions(data);
    }

    @Test void freezesSeparateExactFiltersWithoutLosingScopeOrUsingKeyword() {
        Query query = new Query(); query.setLinkId("L2026111810000000000001"); query.setMerchantOrderNo("ORDER-1");
        service.submit("exact-request", query, "UTC");
        var captured = ArgumentCaptor.forClass(CreateRequest.class); verify(data).submit(captured.capture());
        assertThat(captured.getValue().getPaymentLinkQuery().getLinkId()).isEqualTo(query.getLinkId());
        assertThat(captured.getValue().getPaymentLinkQuery().getMerchantOrderNo()).isEqualTo("ORDER-1");
        assertThat(captured.getValue().getPaymentLinkQuery().getKeyword()).isNull();
    }
    @Test void legacyKeywordExportIsRejectedInsteadOfSilentlyExportingAllRows() {
        Query query = new Query(); query.setKeyword("Invoice");
        assertThatThrownBy(() -> service.submit("legacy-request", query, "UTC")).isInstanceOf(ServiceException.class);
        verifyNoInteractions(data);
    }
}
