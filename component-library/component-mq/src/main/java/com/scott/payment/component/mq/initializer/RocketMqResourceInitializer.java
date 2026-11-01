package com.scott.payment.component.mq.initializer;

import com.scott.payment.component.mq.admin.MqResourceCheckResult;
import com.scott.payment.component.mq.admin.RocketMqAdminFacade;
import com.scott.payment.component.mq.properties.MqResourceInitializerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 声明式 RocketMQ 资源初始化器
 */
@Slf4j
public class RocketMqResourceInitializer {

    /**
     * RocketMQ 官方 Admin 门面。
     */
    private final RocketMqAdminFacade adminFacade;

    /**
     * 初始化器配置。
     */
    private final MqResourceInitializerProperties properties;

    /**
     * 创建声明式资源初始化器。
     *
     * @param adminFacade RocketMQ 官方 Admin 门面
     * @param properties 初始化器配置
     */
    public RocketMqResourceInitializer(RocketMqAdminFacade adminFacade,
                                       MqResourceInitializerProperties properties) {
        this.adminFacade = adminFacade;
        this.properties = properties;
    }

    /**
     * 执行声明式资源检查与初始化。
     */
    public void initialize() {
        if (!properties.isEnabled() || !properties.isScanOnStartup()) {
            log.info("RocketMQ 资源初始化器跳过启动扫描, event: ROCKET_MQ_RESOURCE_SCAN_SKIPPED, "
                            + "enabled: {}, scanOnStartup: {}",
                    properties.isEnabled(),
                    properties.isScanOnStartup());
            return;
        }
        if (CollectionUtils.isEmpty(properties.getResources())) {
            log.info("RocketMQ 资源初始化器未发现资源声明, event: ROCKET_MQ_RESOURCE_LIST_EMPTY");
            return;
        }
        List<MqResourceCheckResult> results = adminFacade.checkAndInitialize(properties.getResources());
        for (MqResourceCheckResult result : results) {
            log.info("RocketMQ 资源检查完成, event: ROCKET_MQ_RESOURCE_CHECKED, type: {}, name: {}, "
                            + "exists: {}, created: {}, updated: {}, message: {}",
                    result.getResourceType(),
                    result.getResourceName(),
                    result.isExists(),
                    result.isCreated(),
                    result.isUpdated(),
                    result.getMessage());
        }
    }
}
