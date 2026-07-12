package com.scott.payment.component.mq.admin;

import com.scott.payment.component.mq.properties.MqResourceDefinitionProperties;
import com.scott.payment.component.mq.properties.MqResourceInitializerProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.topic.TopicValidator;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.body.TopicConfigSerializeWrapper;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RocketMqAdminFacade
 * @date : 2026-06-20 22:49
 * @email : scott_x@163.com
 * @description : 基于 RocketMQ 官方 Admin API 的资源检查与创建门面
 * @status : create
 *
 * <p>该门面只封装业务接入所需的 Topic / Consumer Group 检查与创建能力，
 * 不扩展为通用 MQ 管理框架。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RocketMqAdminFacade
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Rocket Mq Admin Facade，位于 component-library/component-mq 的消息消费层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Slf4j
public class RocketMqAdminFacade {

    /**
     * 管理端 group 前缀，避免与业务生产者/消费者 group 混用。
     */
    private static final String ADMIN_GROUP_PREFIX = "acquiring-mq-initializer-";

    /**
     * RocketMQ Starter 配置。
     */
    private final RocketMQProperties rocketMQProperties;

    /**
     * 初始化器配置。
     */
    private final MqResourceInitializerProperties initializerProperties;

    /**
     * 创建 RocketMQ Admin 门面。
     *
     * @param rocketMQProperties RocketMQ Starter 配置
     * @param initializerProperties 初始化器配置
     */
    public RocketMqAdminFacade(RocketMQProperties rocketMQProperties,
                               MqResourceInitializerProperties initializerProperties) {
        this.rocketMQProperties = rocketMQProperties;
        this.initializerProperties = initializerProperties;
    }

    /**
     * 检查并按需创建声明式资源。
     *
     * @param resources 资源声明列表
     * @return 结果列表
     */
    /**
     * 校验收单支付业务规则，发现不符合要求的数据时抛出业务异常。
     * @param resources 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public List<MqResourceCheckResult> checkAndInitialize(List<MqResourceDefinitionProperties> resources) {
        if (CollectionUtils.isEmpty(resources)) {
            return List.of();
        }
        DefaultMQAdminExt admin = createAdmin();
        try {
            admin.start();
            List<String> allBrokerAddresses = resolveBrokerAddresses(admin, null);
            List<MqResourceCheckResult> results = new ArrayList<>(resources.size());
            for (MqResourceDefinitionProperties resource : resources) {
                results.add(checkAndInitializeResource(admin, resource, allBrokerAddresses));
            }
            return results;
        } catch (Exception exception) {
            throw new IllegalStateException("rocketmq admin initialize failed", exception);
        } finally {
            admin.shutdown();
        }
    }

    /**
     * 检查并处理单个资源。
     *
     * @param admin RocketMQ Admin
     * @param resource 资源声明
     * @param allBrokerAddresses 全部 Broker 地址
     * @return 检查结果
     */
    private MqResourceCheckResult checkAndInitializeResource(DefaultMQAdminExt admin,
                                                             MqResourceDefinitionProperties resource,
                                                             List<String> allBrokerAddresses) {
        validateResource(resource);
        List<String> brokerAddresses = resolveBrokerAddresses(admin, resource.getClusterName(), allBrokerAddresses);
        return switch (resource.getType()) {
            case TOPIC -> handleTopic(admin, resource, brokerAddresses);
            case CONSUMER_GROUP -> handleConsumerGroup(admin, resource, brokerAddresses);
        };
    }

    /**
     * 处理 Topic 资源。
     *
     * @param admin RocketMQ Admin
     * @param resource Topic 声明
     * @param brokerAddresses 命中的 Broker 地址
     * @return 检查结果
     */
    private MqResourceCheckResult handleTopic(DefaultMQAdminExt admin,
                                              MqResourceDefinitionProperties resource,
                                              List<String> brokerAddresses) {
        boolean exists = topicExists(admin, resource.getName(), brokerAddresses);
        if (!exists && !initializerProperties.isAutoCreate()) {
            return buildResult(resource, false, false, false, "topic missing and autoCreate disabled");
        }
        if (!exists) {
            TopicConfig topicConfig = buildTopicConfig(resource);
            for (String brokerAddress : brokerAddresses) {
                invokeTopicUpsert(admin, brokerAddress, topicConfig);
            }
            return buildResult(resource, false, true, false, "topic created");
        }
        if (!initializerProperties.isUpdateIfExists()) {
            return buildResult(resource, true, false, false, "topic already exists");
        }
        TopicConfig topicConfig = buildTopicConfig(resource);
        for (String brokerAddress : brokerAddresses) {
            invokeTopicUpsert(admin, brokerAddress, topicConfig);
        }
        return buildResult(resource, true, false, true, "topic updated");
    }

    /**
     * 处理 Consumer Group 资源。
     *
     * @param admin RocketMQ Admin
     * @param resource Group 声明
     * @param brokerAddresses 命中的 Broker 地址
     * @return 检查结果
     */
    private MqResourceCheckResult handleConsumerGroup(DefaultMQAdminExt admin,
                                                      MqResourceDefinitionProperties resource,
                                                      List<String> brokerAddresses) {
        boolean exists = consumerGroupExists(admin, resource.getName(), brokerAddresses);
        if (!exists && !initializerProperties.isAutoCreate()) {
            return buildResult(resource, false, false, false, "consumer group missing and autoCreate disabled");
        }
        if (!exists) {
            SubscriptionGroupConfig groupConfig = buildSubscriptionGroupConfig(resource);
            for (String brokerAddress : brokerAddresses) {
                invokeConsumerGroupUpsert(admin, brokerAddress, groupConfig);
            }
            return buildResult(resource, false, true, false, "consumer group created");
        }
        if (!initializerProperties.isUpdateIfExists()) {
            return buildResult(resource, true, false, false, "consumer group already exists");
        }
        SubscriptionGroupConfig groupConfig = buildSubscriptionGroupConfig(resource);
        for (String brokerAddress : brokerAddresses) {
            invokeConsumerGroupUpsert(admin, brokerAddress, groupConfig);
        }
        return buildResult(resource, true, false, true, "consumer group updated");
    }

    /**
     * 判断 Topic 是否存在。
     *
     * @param admin RocketMQ Admin
     * @param topicName Topic 名称
     * @param brokerAddresses Broker 地址
     * @return 是否存在
     */
    private boolean topicExists(DefaultMQAdminExt admin, String topicName, List<String> brokerAddresses) {
        for (String brokerAddress : brokerAddresses) {
            try {
                TopicConfigSerializeWrapper wrapper = admin.getUserTopicConfig(brokerAddress, false, 3_000L);
                if (wrapper != null
                        && wrapper.getTopicConfigTable() != null
                        && wrapper.getTopicConfigTable().containsKey(topicName)) {
                    return true;
                }
            } catch (Exception exception) {
                log.warn("检查 RocketMQ Topic 失败，topic：{}，brokerAddress：{}，原因：{}",
                        topicName,
                        brokerAddress,
                        exception.getMessage());
            }
        }
        return false;
    }

    /**
     * 判断 Consumer Group 是否存在。
     *
     * @param admin RocketMQ Admin
     * @param groupName Consumer Group 名称
     * @param brokerAddresses Broker 地址
     * @return 是否存在
     */
    private boolean consumerGroupExists(DefaultMQAdminExt admin, String groupName, List<String> brokerAddresses) {
        for (String brokerAddress : brokerAddresses) {
            try {
                SubscriptionGroupWrapper wrapper = admin.getUserSubscriptionGroup(brokerAddress, 3_000L);
                if (wrapper != null
                        && wrapper.getSubscriptionGroupTable() != null
                        && wrapper.getSubscriptionGroupTable().containsKey(groupName)) {
                    return true;
                }
            } catch (Exception exception) {
                log.warn("检查 RocketMQ Consumer Group 失败，group：{}，brokerAddress：{}，原因：{}",
                        groupName,
                        brokerAddress,
                        exception.getMessage());
            }
        }
        return false;
    }

    /**
     * 构造 Topic 配置。
     *
     * @param resource Topic 声明
     * @return Topic 配置
     */
    private TopicConfig buildTopicConfig(MqResourceDefinitionProperties resource) {
        TopicConfig topicConfig = new TopicConfig();
        topicConfig.setTopicName(resource.getName());
        topicConfig.setReadQueueNums(defaultValue(resource.getReadQueueNums(), initializerProperties.getDefaultReadQueueNums()));
        topicConfig.setWriteQueueNums(defaultValue(resource.getWriteQueueNums(), initializerProperties.getDefaultWriteQueueNums()));
        topicConfig.setPerm(defaultValue(resource.getPerm(), initializerProperties.getDefaultTopicPerm()));
        return topicConfig;
    }

    /**
     * 构造 Consumer Group 配置。
     *
     * @param resource Group 声明
     * @return Group 配置
     */
    private SubscriptionGroupConfig buildSubscriptionGroupConfig(MqResourceDefinitionProperties resource) {
        SubscriptionGroupConfig groupConfig = new SubscriptionGroupConfig();
        groupConfig.setGroupName(resource.getName());
        groupConfig.setConsumeEnable(Boolean.TRUE.equals(resource.getConsumeEnable()));
        groupConfig.setConsumeBroadcastEnable(Boolean.TRUE.equals(resource.getConsumeBroadcastEnable()));
        groupConfig.setRetryQueueNums(defaultValue(resource.getRetryQueueNums(), 1));
        groupConfig.setRetryMaxTimes(defaultValue(resource.getRetryMaxTimes(), 16));
        groupConfig.setConsumeTimeoutMinute(defaultValue(resource.getConsumeTimeoutMinute(), 15));
        return groupConfig;
    }

    /**
     * 创建并配置 RocketMQ Admin 实例。
     *
     * @return Admin 实例
     */
    private DefaultMQAdminExt createAdmin() {
        if (!StringUtils.hasText(rocketMQProperties.getNameServer())) {
            throw new IllegalStateException("rocketmq name-server can not be blank when initializer enabled");
        }
        DefaultMQAdminExt admin = new DefaultMQAdminExt(ADMIN_GROUP_PREFIX + System.currentTimeMillis());
        admin.setNamesrvAddr(rocketMQProperties.getNameServer());
        admin.setVipChannelEnabled(false);
        return admin;
    }

    /**
     * 解析命中的 Broker 地址列表。
     *
     * @param admin RocketMQ Admin
     * @param clusterName 集群名称
     * @return Broker 地址列表
     */
    private List<String> resolveBrokerAddresses(DefaultMQAdminExt admin, String clusterName) {
        return resolveBrokerAddresses(admin, clusterName, null);
    }

    /**
     * 解析命中的 Broker 地址列表。
     *
     * @param admin RocketMQ Admin
     * @param clusterName 集群名称
     * @param fallbackAllAddresses 已解析的全部 Broker 地址
     * @return Broker 地址列表
     */
    private List<String> resolveBrokerAddresses(DefaultMQAdminExt admin,
                                                String clusterName,
                                                List<String> fallbackAllAddresses) {
        try {
            ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
            Map<String, BrokerData> brokerAddrTable = clusterInfo.getBrokerAddrTable();
            if (CollectionUtils.isEmpty(brokerAddrTable)) {
                throw new IllegalStateException("rocketmq broker address table is empty");
            }
            Set<String> brokerNames = StringUtils.hasText(clusterName)
                    ? clusterInfo.getClusterAddrTable().get(clusterName)
                    : brokerAddrTable.keySet();
            if (CollectionUtils.isEmpty(brokerNames)) {
                if (!CollectionUtils.isEmpty(fallbackAllAddresses) && !StringUtils.hasText(clusterName)) {
                    return fallbackAllAddresses;
                }
                throw new IllegalStateException("rocketmq broker cluster not found: " + clusterName);
            }
            Set<String> brokerAddresses = new LinkedHashSet<>();
            for (String brokerName : brokerNames) {
                BrokerData brokerData = brokerAddrTable.get(brokerName);
                if (brokerData == null || CollectionUtils.isEmpty(brokerData.getBrokerAddrs())) {
                    continue;
                }
                brokerAddresses.addAll(brokerData.getBrokerAddrs().values());
            }
            if (CollectionUtils.isEmpty(brokerAddresses)) {
                throw new IllegalStateException("rocketmq broker addresses resolved empty");
            }
            return List.copyOf(brokerAddresses);
        } catch (Exception exception) {
            throw new IllegalStateException("resolve rocketmq broker addresses failed", exception);
        }
    }

    /**
     * 调用官方 Admin API 更新 Topic。
     *
     * @param admin RocketMQ Admin
     * @param brokerAddress Broker 地址
     * @param topicConfig Topic 配置
     */
    private void invokeTopicUpsert(DefaultMQAdminExt admin,
                                   String brokerAddress,
                                   TopicConfig topicConfig) {
        try {
            admin.createAndUpdateTopicConfig(brokerAddress, topicConfig);
        } catch (RemotingException | MQBrokerException | InterruptedException | MQClientException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("create or update rocketmq topic failed: " + topicConfig.getTopicName(), exception);
        }
    }

    /**
     * 调用官方 Admin API 更新 Consumer Group。
     *
     * @param admin RocketMQ Admin
     * @param brokerAddress Broker 地址
     * @param groupConfig Group 配置
     */
    private void invokeConsumerGroupUpsert(DefaultMQAdminExt admin,
                                           String brokerAddress,
                                           SubscriptionGroupConfig groupConfig) {
        try {
            admin.createAndUpdateSubscriptionGroupConfig(brokerAddress, groupConfig);
        } catch (RemotingException | MQBrokerException | InterruptedException | MQClientException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("create or update rocketmq consumer group failed: " + groupConfig.getGroupName(), exception);
        }
    }

    /**
     * 校验资源声明是否合法。
     *
     * @param resource 资源声明
     */
    private void validateResource(MqResourceDefinitionProperties resource) {
        if (resource == null) {
            throw new IllegalArgumentException("mq resource definition can not be null");
        }
        if (!StringUtils.hasText(resource.getName())) {
            throw new IllegalArgumentException("mq resource name can not be blank");
        }
        if (resource.getType() == null) {
            throw new IllegalArgumentException("mq resource type can not be null");
        }
        if (TopicValidator.isTopicOrGroupIllegal(resource.getName())) {
            throw new IllegalArgumentException("mq resource name is illegal: " + resource.getName());
        }
    }

    /**
     * 构造资源检查结果。
     *
     * @param resource 资源声明
     * @param exists 是否存在
     * @param created 是否创建
     * @param updated 是否更新
     * @param message 结果摘要
     * @return 检查结果
     */
    private MqResourceCheckResult buildResult(MqResourceDefinitionProperties resource,
                                              boolean exists,
                                              boolean created,
                                              boolean updated,
                                              String message) {
        return MqResourceCheckResult.builder()
                .resourceName(resource.getName())
                .resourceType(resource.getType())
                .exists(exists)
                .created(created)
                .updated(updated)
                .message(message)
                .build();
    }

    /**
     * 处理整数默认值。
     *
     * @param candidate 候选值
     * @param defaultValue 默认值
     * @return 最终值
     */
    private int defaultValue(Integer candidate, int defaultValue) {
        return Objects.requireNonNullElse(candidate, defaultValue);
    }
}
