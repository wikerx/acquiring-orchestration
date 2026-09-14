package com.scott.payment.admin.service.monitor;

import com.scott.payment.admin.config.AdminRocketMqMonitorProperties;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.AccessChannel;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RocketMqAdminMonitorProvider
 * @date : 2026-09-14 11:24
 * @email : scott_x@163.com
 * @description : service-admin RocketMQ 只读监控提供器，负责 Admin 客户端按需连接、脱敏探测和关闭。
 * @status : create
 */
@Component
public class RocketMqAdminMonitorProvider implements DisposableBean {

    /** 未启用 RocketMQ Admin 监控或缺少必要配置。 */
    public static final String STATUS_NOT_CONFIGURED = "NOT_CONFIGURED";
    /** RocketMQ Admin 连接和只读查询可用。 */
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    /** 已启用但连接、鉴权或查询失败。 */
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    /** RocketMQ Starter 配置；NameServer 和客户端基础选项从此读取。 */
    private final RocketMQProperties rocketMqProperties;
    /** 管理端 RocketMQ Admin 专用监控配置。 */
    private final AdminRocketMqMonitorProperties monitorProperties;
    /** Admin 客户端构造工厂，生产环境使用 SDK 构造器，测试可替换。 */
    private final AdminClientFactory clientFactory;
    /** Admin 客户端创建、查询和关闭的进程内互斥锁。 */
    private final Object clientMonitor = new Object();

    /** 当前已启动的 RocketMQ Admin 客户端；未连接时为空。 */
    private DefaultMQAdminExt adminClient;
    /** 当前客户端绑定的 NameServer 地址；未连接时为空且不得写入响应。 */
    private String connectedNameServer;

    /**
     * 创建 RocketMQ 监控提供器；RocketMQ Starter 未注册属性 Bean 时仍允许 Admin 正常启动。
     *
     * @param rocketMqPropertiesProvider RocketMQ Starter 配置提供器
     * @param monitorProperties 管理端监控配置
     */
    @Autowired
    public RocketMqAdminMonitorProvider(ObjectProvider<RocketMQProperties> rocketMqPropertiesProvider,
                                        AdminRocketMqMonitorProperties monitorProperties) {
        this(rocketMqPropertiesProvider.getIfAvailable(RocketMQProperties::new),
                monitorProperties, RocketMqAdminMonitorProvider::newAdminClient);
    }

    RocketMqAdminMonitorProvider(RocketMQProperties rocketMqProperties,
                                 AdminRocketMqMonitorProperties monitorProperties,
                                 AdminClientFactory clientFactory) {
        this.rocketMqProperties = rocketMqProperties;
        this.monitorProperties = monitorProperties;
        this.clientFactory = clientFactory;
    }

    /**
     * 查询 RocketMQ 集群只读摘要。连接或查询失败不会中断管理端请求，也不会返回底层异常消息。
     *
     * @return 当前 RocketMQ Admin 监控快照
     */
    public Snapshot snapshot() {
        synchronized (clientMonitor) {
            if (!monitorProperties.isEnabled()) {
                shutdownClient();
                return Snapshot.notConfigured("RocketMQ Admin 监控未启用；外部控制台入口仍可使用");
            }
            String nameServer = trimToNull(rocketMqProperties.getNameServer());
            if (nameServer == null) {
                shutdownClient();
                return Snapshot.unavailable("已启用 RocketMQ Admin 监控，但 rocketmq.name-server 未配置");
            }
            try {
                DefaultMQAdminExt client = ensureClient(nameServer);
                ClusterInfo clusterInfo = client.examineBrokerClusterInfo();
                ClusterSummary clusterSummary = summarizeCluster(clusterInfo, trimToNull(monitorProperties.getClusterName()));
                TopicList topicList = clusterSummary.clusterName() == null
                        ? client.fetchAllTopicList()
                        : client.fetchTopicsByCLuster(clusterSummary.clusterName());
                int topicCount = topicList == null || topicList.getTopicList() == null
                        ? 0 : topicList.getTopicList().size();
                return Snapshot.available(clusterSummary.clusterCount(), clusterSummary.brokerCount(),
                        clusterSummary.brokerAddressCount(), topicCount);
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                shutdownClient();
                return Snapshot.unavailable("RocketMQ Admin 连接或查询失败（" + rootCauseType(exception) + "）");
            }
        }
    }

    /**
     * 关闭 Provider 持有的 Admin 客户端和网络资源。
     */
    @Override
    public void destroy() {
        synchronized (clientMonitor) {
            shutdownClient();
        }
    }

    /**
     * 复用已连接同一 NameServer 的客户端，否则关闭旧连接并按当前配置重新创建。
     *
     * @param nameServer 已去除首尾空白的 NameServer 地址，不允许为空
     * @return 已完成启动的 RocketMQ Admin 客户端
     * @throws Exception 客户端创建、启动或配置应用失败时抛出，由快照入口转换为脱敏能力状态
     */
    private DefaultMQAdminExt ensureClient(String nameServer) throws Exception {
        if (adminClient != null && nameServer.equals(connectedNameServer)) {
            return adminClient;
        }
        shutdownClient();
        Credentials credentials = resolveCredentials();
        DefaultMQAdminExt candidate = clientFactory.create(
                monitorProperties, credentials.accessKey(), credentials.secretKey());
        candidate.setNamesrvAddr(nameServer);
        candidate.setVipChannelEnabled(false);
        candidate.setMqClientApiTimeout(monitorProperties.getRequestTimeoutMillis());
        candidate.setDetectTimeout(monitorProperties.getRequestTimeoutMillis());
        candidate.setInstanceName("service-admin-" + ProcessHandle.current().pid());
        applyStarterClientOptions(candidate);
        try {
            candidate.start();
            adminClient = candidate;
            connectedNameServer = nameServer;
            return candidate;
        } catch (Exception exception) {
            candidate.shutdown();
            throw exception;
        }
    }

    /**
     * 将 RocketMQ Starter 中的 TLS、Namespace 和访问通道选项同步到只读 Admin 客户端。
     *
     * @param client 尚未启动的 Admin 客户端，不允许为空
     * @throws IllegalArgumentException access-channel 不是 SDK 支持值时抛出，由快照入口统一降级
     */
    private void applyStarterClientOptions(DefaultMQAdminExt client) {
        RocketMQProperties.Producer producer = rocketMqProperties.getProducer();
        if (producer != null) {
            client.setUseTLS(producer.isTlsEnable());
            if (StringUtils.hasText(producer.getNamespace())) {
                client.setNamespace(producer.getNamespace().trim());
            } else if (StringUtils.hasText(producer.getNamespaceV2())) {
                client.setNamespaceV2(producer.getNamespaceV2().trim());
            }
        }
        String accessChannel = trimToNull(rocketMqProperties.getAccessChannel());
        if (accessChannel != null) {
            client.setAccessChannel(AccessChannel.valueOf(accessChannel.toUpperCase(Locale.ROOT)));
        }
    }

    /**
     * 优先读取 Admin 专用 ACL 凭据；未配置时复用 RocketMQ Producer 凭据。
     *
     * @return 仅在客户端创建期间使用的 ACL 凭据对，两个字段均允许为空
     */
    private Credentials resolveCredentials() {
        String dedicatedAccessKey = trimToNull(monitorProperties.getAccessKey());
        String dedicatedSecretKey = trimToNull(monitorProperties.getSecretKey());
        if (dedicatedAccessKey != null || dedicatedSecretKey != null) {
            return new Credentials(dedicatedAccessKey, dedicatedSecretKey);
        }
        RocketMQProperties.Producer producer = rocketMqProperties.getProducer();
        return producer == null
                ? new Credentials(null, null)
                : new Credentials(trimToNull(producer.getAccessKey()), trimToNull(producer.getSecretKey()));
    }

    /** 关闭当前 Admin 客户端并清除连接身份；方法允许重复调用。 */
    private void shutdownClient() {
        if (adminClient != null) {
            adminClient.shutdown();
        }
        adminClient = null;
        connectedNameServer = null;
    }

    /**
     * 根据 ACL 配置创建未启动的 RocketMQ Admin 客户端，禁止接受半套凭据。
     *
     * @param monitorProperties Admin 客户端组名和超时配置
     * @param accessKey ACL AccessKey；无 ACL 时为空
     * @param secretKey ACL SecretKey；无 ACL 时为空
     * @return 尚未启动且未设置 NameServer 的 Admin 客户端
     * @throws IllegalStateException ACL 凭据不完整或客户端组名为空时抛出
     */
    private static DefaultMQAdminExt newAdminClient(AdminRocketMqMonitorProperties monitorProperties,
                                                    String accessKey,
                                                    String secretKey) {
        boolean hasAccessKey = accessKey != null;
        boolean hasSecretKey = secretKey != null;
        if (hasAccessKey != hasSecretKey) {
            throw new IllegalStateException("RocketMQ Admin ACL credentials are incomplete");
        }
        DefaultMQAdminExt client;
        if (hasAccessKey) {
            RPCHook rpcHook = new AclClientRPCHook(new SessionCredentials(accessKey, secretKey));
            client = new DefaultMQAdminExt(rpcHook, monitorProperties.getRequestTimeoutMillis());
            client.setAdminExtGroup(requireAdminGroup(monitorProperties));
        } else {
            client = new DefaultMQAdminExt(requireAdminGroup(monitorProperties),
                    monitorProperties.getRequestTimeoutMillis());
        }
        return client;
    }

    /**
     * 读取并校验 Admin 客户端组名。
     *
     * @param properties Admin 监控配置
     * @return 已去除首尾空白的客户端组名
     * @throws IllegalStateException 客户端组名未配置时抛出
     */
    private static String requireAdminGroup(AdminRocketMqMonitorProperties properties) {
        String adminGroup = trimToNull(properties.getAdminGroup());
        if (adminGroup == null) {
            throw new IllegalStateException("RocketMQ Admin group is not configured");
        }
        return adminGroup;
    }

    /**
     * 从 NameServer 路由数据中汇总受控集群、Broker 和节点数量，不返回实际地址。
     *
     * @param clusterInfo NameServer 返回的集群路由信息
     * @param configuredClusterName 可选的目标集群名；为空时汇总全部集群
     * @return 不包含 Broker 地址的集群数量摘要
     * @throws IllegalStateException 路由为空、目标集群不存在或无可用 Broker 地址时抛出
     */
    private static ClusterSummary summarizeCluster(ClusterInfo clusterInfo, String configuredClusterName) {
        Map<String, BrokerData> brokerTable = clusterInfo == null || clusterInfo.getBrokerAddrTable() == null
                ? Map.of() : clusterInfo.getBrokerAddrTable();
        Map<String, Set<String>> clusterTable = clusterInfo == null || clusterInfo.getClusterAddrTable() == null
                ? Map.of() : clusterInfo.getClusterAddrTable();
        if (brokerTable.isEmpty()) {
            throw new IllegalStateException("RocketMQ broker table is empty");
        }
        Set<String> brokerNames;
        int clusterCount;
        if (configuredClusterName == null) {
            brokerNames = new LinkedHashSet<>(brokerTable.keySet());
            clusterCount = clusterTable.size();
        } else {
            Set<String> configuredBrokerNames = clusterTable.get(configuredClusterName);
            if (configuredBrokerNames == null || configuredBrokerNames.isEmpty()) {
                throw new IllegalStateException("Configured RocketMQ cluster is unavailable");
            }
            brokerNames = new LinkedHashSet<>(configuredBrokerNames);
            clusterCount = 1;
        }
        Set<String> resolvedBrokerNames = new LinkedHashSet<>();
        Set<String> brokerAddresses = new LinkedHashSet<>();
        for (String brokerName : brokerNames) {
            BrokerData brokerData = brokerTable.get(brokerName);
            if (brokerData != null && brokerData.getBrokerAddrs() != null) {
                resolvedBrokerNames.add(brokerName);
                brokerAddresses.addAll(brokerData.getBrokerAddrs().values());
            }
        }
        if (resolvedBrokerNames.isEmpty() || brokerAddresses.isEmpty()) {
            throw new IllegalStateException("RocketMQ broker addresses are unavailable");
        }
        return new ClusterSummary(configuredClusterName, clusterCount,
                resolvedBrokerNames.size(), brokerAddresses.size());
    }

    /**
     * 提取根因异常类型供监控状态展示，避免泄露地址、凭据或服务端响应正文。
     *
     * @param throwable RocketMQ 客户端调用异常
     * @return 根因异常的简单类名
     */
    private static String rootCauseType(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String simpleName = current.getClass().getSimpleName();
        return StringUtils.hasText(simpleName) ? simpleName : "RocketMqAdminException";
    }

    /**
     * 去除配置文本首尾空白并统一空值。
     *
     * @param value 原始配置文本
     * @return 非空文本或 {@code null}
     */
    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 创建已配置但尚未启动的 RocketMQ Admin 客户端，便于隔离 SDK 构造和单元测试。 */
    @FunctionalInterface
    interface AdminClientFactory {

        /**
         * 根据监控配置和可选 ACL 凭据创建尚未启动的 Admin 客户端。
         *
         * @param monitorProperties Admin 客户端组名和查询超时配置
         * @param accessKey ACL AccessKey；未启用 ACL 时为空
         * @param secretKey ACL SecretKey；未启用 ACL 时为空
         * @return 尚未连接 NameServer 的 RocketMQ Admin 客户端
         */
        DefaultMQAdminExt create(AdminRocketMqMonitorProperties monitorProperties,
                                 String accessKey,
                                 String secretKey);
    }

    /** Admin ACL 凭据对，仅在客户端创建期间使用。 */
    private record Credentials(String accessKey, String secretKey) {
    }

    /** RocketMQ 集群和 Broker 路由表摘要。 */
    private record ClusterSummary(String clusterName,
                                  int clusterCount,
                                  int brokerCount,
                                  int brokerAddressCount) {
    }

    /**
     * RocketMQ Admin 监控快照。状态供能力接口和依赖健康接口统一映射。
     *
     * @param status 能力状态
     * @param reason 脱敏后的状态原因
     * @param clusterCount 集群数量
     * @param brokerCount Broker 数量
     * @param brokerAddressCount Broker 节点地址数量
     * @param topicCount Topic 数量
     */
    public record Snapshot(String status,
                           String reason,
                           int clusterCount,
                           int brokerCount,
                           int brokerAddressCount,
                           int topicCount) {

        /**
         * 创建未配置快照。
         *
         * @param reason 未配置原因
         * @return 未配置快照
         */
        public static Snapshot notConfigured(String reason) {
            return new Snapshot(STATUS_NOT_CONFIGURED, reason, 0, 0, 0, 0);
        }

        /**
         * 创建不可用快照。
         *
         * @param reason 脱敏后的不可用原因
         * @return 不可用快照
         */
        public static Snapshot unavailable(String reason) {
            return new Snapshot(STATUS_UNAVAILABLE, reason, 0, 0, 0, 0);
        }

        /**
         * 创建可用快照。
         *
         * @param clusterCount 集群数量
         * @param brokerCount Broker 数量
         * @param brokerAddressCount Broker 节点地址数量
         * @param topicCount Topic 数量
         * @return 可用快照
         */
        public static Snapshot available(int clusterCount,
                                         int brokerCount,
                                         int brokerAddressCount,
                                         int topicCount) {
            String reason = "已连接 RocketMQ Admin：集群 " + clusterCount
                    + "，Broker " + brokerCount
                    + "，节点 " + brokerAddressCount
                    + "，Topic " + topicCount;
            return new Snapshot(STATUS_AVAILABLE, reason, clusterCount, brokerCount, brokerAddressCount, topicCount);
        }

        /**
         * 返回依赖监控使用的紧凑摘要。
         *
         * @return 不包含地址和凭据的依赖摘要
         */
        public String summary() {
            if (!STATUS_AVAILABLE.equals(status)) {
                return STATUS_NOT_CONFIGURED.equals(status) ? "Not configured" : "Unavailable";
            }
            return clusterCount + " cluster · " + brokerCount + " broker · " + topicCount + " topic";
        }
    }
}
