package com.scott.payment.channel.payout.executor;

import com.scott.payment.channel.payout.api.PayoutChannelClient;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutQueryRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutRequest;
import com.scott.payment.channel.payout.dto.response.ChannelPayoutResponse;
import com.scott.payment.channel.payout.enums.PayoutChannelCapability;
import com.scott.payment.channel.payout.exception.PayoutChannelUnsupportedOperationException;
import com.scott.payment.channel.payout.registry.PayoutChannelRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelExecutorTests
 * @date : 2026-08-12 00:00
 * @email : scott_x@163.com
 * @description : 代付执行器测试，验证提交/查询委托和 Provider 能力边界。
 * @status : create
 */
class PayoutChannelExecutorTests {

    @Test
    void shouldDelegateSubmitAndQueryToSelectedProvider() {
        StubPayoutChannelClient client = new StubPayoutChannelClient(
                Set.of(PayoutChannelCapability.SUBMIT, PayoutChannelCapability.QUERY));
        PayoutChannelExecutor executor = executor(client);
        ChannelPayoutRequest submitRequest = new ChannelPayoutRequest();
        submitRequest.setChannelCode("THUNES");
        submitRequest.setPayoutOrderNo("PO-001");
        ChannelPayoutQueryRequest queryRequest = new ChannelPayoutQueryRequest();
        queryRequest.setChannelCode("thunes");
        queryRequest.setPayoutOrderNo("PO-001");

        assertThat(executor.submit(submitRequest).getPayoutOrderNo()).isEqualTo("PO-001");
        assertThat(executor.query(queryRequest).getPayoutOrderNo()).isEqualTo("PO-001");
        assertThat(client.submitCalled).isTrue();
        assertThat(client.queryCalled).isTrue();
    }

    @Test
    void shouldRejectCapabilityNotDeclaredByProvider() {
        PayoutChannelExecutor executor = executor(new StubPayoutChannelClient(
                Set.of(PayoutChannelCapability.SUBMIT)));
        ChannelPayoutQueryRequest request = new ChannelPayoutQueryRequest();
        request.setChannelCode("THUNES");

        assertThatThrownBy(() -> executor.query(request))
                .isInstanceOf(PayoutChannelUnsupportedOperationException.class)
                .hasMessageContaining("不支持能力[QUERY]");
    }

    private PayoutChannelExecutor executor(PayoutChannelClient client) {
        return new PayoutChannelExecutor(new PayoutChannelRegistry(Optional.of(List.of(client))));
    }

    private static final class StubPayoutChannelClient implements PayoutChannelClient {

        private final Set<PayoutChannelCapability> capabilities;
        private boolean submitCalled;
        private boolean queryCalled;

        private StubPayoutChannelClient(Set<PayoutChannelCapability> capabilities) {
            this.capabilities = capabilities;
        }

        @Override
        public String channelCode() {
            return "THUNES";
        }

        @Override
        public Set<PayoutChannelCapability> capabilities() {
            return capabilities;
        }

        @Override
        public ChannelPayoutResponse submit(ChannelPayoutRequest request) {
            submitCalled = true;
            ChannelPayoutResponse response = new ChannelPayoutResponse();
            response.setPayoutOrderNo(request.getPayoutOrderNo());
            return response;
        }

        @Override
        public ChannelPayoutResponse query(ChannelPayoutQueryRequest request) {
            queryCalled = true;
            ChannelPayoutResponse response = new ChannelPayoutResponse();
            response.setPayoutOrderNo(request.getPayoutOrderNo());
            return response;
        }
    }
}
