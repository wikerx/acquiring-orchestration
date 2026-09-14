package com.scott.payment.admin.service.monitor;

import com.scott.payment.admin.config.AdminRocketMqMonitorProperties;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RocketMqAdminMonitorProviderTest
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : RocketMQ Admin 延迟连接、集群摘要、ACL 和异常降级行为测试。
 * @status : create
 */
class RocketMqAdminMonitorProviderTest {

    @Test
    void shouldSelectProductionConstructorWhenCreatedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RocketMQProperties.class);
            context.registerBean(AdminRocketMqMonitorProperties.class);
            context.registerBean(RocketMqAdminMonitorProvider.class);

            context.refresh();

            assertThat(context.getBean(RocketMqAdminMonitorProvider.class)).isNotNull();
        }
    }

    @Test
    void shouldNotCreateClientWhenMonitorIsDisabled() {
        RocketMqAdminMonitorProvider.AdminClientFactory factory = mock(
                RocketMqAdminMonitorProvider.AdminClientFactory.class);
        RocketMqAdminMonitorProvider provider = provider(new RocketMQProperties(), properties(false), factory);

        RocketMqAdminMonitorProvider.Snapshot snapshot = provider.snapshot();

        assertThat(snapshot.status()).isEqualTo(RocketMqAdminMonitorProvider.STATUS_NOT_CONFIGURED);
        assertThat(snapshot.reason()).contains("未启用");
        verifyNoInteractions(factory);
    }

    @Test
    void shouldReportUnavailableWhenNameServerIsMissing() {
        RocketMqAdminMonitorProvider.AdminClientFactory factory = mock(
                RocketMqAdminMonitorProvider.AdminClientFactory.class);
        RocketMqAdminMonitorProvider provider = provider(new RocketMQProperties(), properties(true), factory);

        RocketMqAdminMonitorProvider.Snapshot snapshot = provider.snapshot();

        assertThat(snapshot.status()).isEqualTo(RocketMqAdminMonitorProvider.STATUS_UNAVAILABLE);
        assertThat(snapshot.reason()).contains("rocketmq.name-server");
        verifyNoInteractions(factory);
    }

    @Test
    void shouldExposeClusterBrokerNodeAndTopicCounts() throws Exception {
        DefaultMQAdminExt client = healthyClient();
        RocketMqAdminMonitorProvider.AdminClientFactory factory = mock(
                RocketMqAdminMonitorProvider.AdminClientFactory.class);
        when(factory.create(any(), isNull(), isNull())).thenReturn(client);
        RocketMQProperties rocketMqProperties = rocketMqProperties("127.0.0.1:9876");
        AdminRocketMqMonitorProperties monitorProperties = properties(true);
        RocketMqAdminMonitorProvider provider = provider(rocketMqProperties, monitorProperties, factory);

        RocketMqAdminMonitorProvider.Snapshot snapshot = provider.snapshot();

        assertThat(snapshot.status()).isEqualTo(RocketMqAdminMonitorProvider.STATUS_AVAILABLE);
        assertThat(snapshot.clusterCount()).isEqualTo(1);
        assertThat(snapshot.brokerCount()).isEqualTo(1);
        assertThat(snapshot.brokerAddressCount()).isEqualTo(2);
        assertThat(snapshot.topicCount()).isEqualTo(3);
        assertThat(snapshot.summary()).isEqualTo("1 cluster · 1 broker · 3 topic");
        verify(client).setNamesrvAddr("127.0.0.1:9876");
        verify(client).setVipChannelEnabled(false);
        verify(client).setMqClientApiTimeout(3000);
        verify(client).setDetectTimeout(3000);
        verify(client).start();
    }

    @Test
    void shouldUseProducerAclCredentialsWhenDedicatedCredentialsAreAbsent() throws Exception {
        DefaultMQAdminExt client = healthyClient();
        RocketMqAdminMonitorProvider.AdminClientFactory factory = mock(
                RocketMqAdminMonitorProvider.AdminClientFactory.class);
        when(factory.create(any(), any(), any())).thenReturn(client);
        RocketMQProperties rocketMqProperties = rocketMqProperties("127.0.0.1:9876");
        RocketMQProperties.Producer producer = new RocketMQProperties.Producer();
        producer.setAccessKey("producer-access");
        producer.setSecretKey("producer-secret");
        rocketMqProperties.setProducer(producer);
        RocketMqAdminMonitorProvider provider = provider(rocketMqProperties, properties(true), factory);

        provider.snapshot();

        verify(factory).create(any(), org.mockito.ArgumentMatchers.eq("producer-access"),
                org.mockito.ArgumentMatchers.eq("producer-secret"));
    }

    @Test
    void shouldSanitizeFailureAndReconnectOnNextProbe() throws Exception {
        DefaultMQAdminExt failedClient = mock(DefaultMQAdminExt.class);
        when(failedClient.examineBrokerClusterInfo())
                .thenThrow(new IllegalStateException("secretKey=must-not-leak"));
        DefaultMQAdminExt healthyClient = healthyClient();
        RocketMqAdminMonitorProvider.AdminClientFactory factory = mock(
                RocketMqAdminMonitorProvider.AdminClientFactory.class);
        when(factory.create(any(), isNull(), isNull())).thenReturn(failedClient, healthyClient);
        RocketMqAdminMonitorProvider provider = provider(
                rocketMqProperties("127.0.0.1:9876"), properties(true), factory);

        RocketMqAdminMonitorProvider.Snapshot failed = provider.snapshot();
        RocketMqAdminMonitorProvider.Snapshot recovered = provider.snapshot();

        assertThat(failed.status()).isEqualTo(RocketMqAdminMonitorProvider.STATUS_UNAVAILABLE);
        assertThat(failed.reason()).contains("IllegalStateException").doesNotContain("must-not-leak");
        assertThat(recovered.status()).isEqualTo(RocketMqAdminMonitorProvider.STATUS_AVAILABLE);
        verify(failedClient).shutdown();
        verify(factory, org.mockito.Mockito.times(2)).create(any(), isNull(), isNull());
    }

    @Test
    void shouldShutdownStartedClientWhenProviderIsDestroyed() throws Exception {
        DefaultMQAdminExt client = healthyClient();
        RocketMqAdminMonitorProvider.AdminClientFactory factory = mock(
                RocketMqAdminMonitorProvider.AdminClientFactory.class);
        when(factory.create(any(), isNull(), isNull())).thenReturn(client);
        RocketMqAdminMonitorProvider provider = provider(
                rocketMqProperties("127.0.0.1:9876"), properties(true), factory);

        provider.snapshot();
        provider.destroy();

        verify(client).shutdown();
    }

    @Test
    void shouldNotShutdownClientThatWasNeverStarted() {
        DefaultMQAdminExt client = mock(DefaultMQAdminExt.class);
        RocketMqAdminMonitorProvider.AdminClientFactory factory = mock(
                RocketMqAdminMonitorProvider.AdminClientFactory.class);
        when(factory.create(any(), isNull(), isNull())).thenReturn(client);
        RocketMqAdminMonitorProvider provider = provider(
                rocketMqProperties("127.0.0.1:9876"), properties(false), factory);

        provider.destroy();

        verify(client, never()).shutdown();
    }

    private DefaultMQAdminExt healthyClient() throws Exception {
        DefaultMQAdminExt client = mock(DefaultMQAdminExt.class);
        ClusterInfo clusterInfo = new ClusterInfo();
        HashMap<Long, String> brokerAddresses = new HashMap<>();
        brokerAddresses.put(0L, "127.0.0.1:10911");
        brokerAddresses.put(1L, "127.0.0.1:20911");
        BrokerData brokerData = new BrokerData("DefaultCluster", "broker-a", brokerAddresses);
        clusterInfo.setBrokerAddrTable(Map.of("broker-a", brokerData));
        clusterInfo.setClusterAddrTable(Map.of("DefaultCluster", Set.of("broker-a")));
        TopicList topicList = new TopicList();
        topicList.setTopicList(new LinkedHashSet<>(Set.of("topic-a", "topic-b", "topic-c")));
        when(client.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(client.fetchTopicsByCLuster("DefaultCluster")).thenReturn(topicList);
        return client;
    }

    private RocketMqAdminMonitorProvider provider(
            RocketMQProperties rocketMqProperties,
            AdminRocketMqMonitorProperties monitorProperties,
            RocketMqAdminMonitorProvider.AdminClientFactory factory) {
        return new RocketMqAdminMonitorProvider(rocketMqProperties, monitorProperties, factory);
    }

    private RocketMQProperties rocketMqProperties(String nameServer) {
        RocketMQProperties properties = new RocketMQProperties();
        properties.setNameServer(nameServer);
        return properties;
    }

    private AdminRocketMqMonitorProperties properties(boolean enabled) {
        AdminRocketMqMonitorProperties properties = new AdminRocketMqMonitorProperties();
        properties.setEnabled(enabled);
        properties.setClusterName("DefaultCluster");
        return properties;
    }
}
