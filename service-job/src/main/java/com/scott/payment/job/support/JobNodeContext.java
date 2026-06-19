package com.scott.payment.job.support;

import com.scott.payment.job.config.JobSchedulerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobNodeContext
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 调度中心执行节点上下文
 * @status : create
 */

@Component
public class JobNodeContext {

    private final String appName;
    private final String configuredHost;
    private final int port;
    private final Registration registration;
    private final JobSchedulerProperties jobSchedulerProperties;
    private final AtomicInteger runningCount = new AtomicInteger();

    /**
     * 创建节点上下文。
     *
     * @param appName                服务名称
     * @param configuredHost         配置中的本机地址
     * @param port                   服务端口
     * @param registration           注册中心实例
     * @param jobSchedulerProperties 调度配置
     */
    public JobNodeContext(@Value("${spring.application.name}") String appName,
                          @Value("${spring.cloud.nacos.discovery.ip:}") String configuredHost,
                          @Value("${server.port}") int port,
                          Registration registration,
                          JobSchedulerProperties jobSchedulerProperties) {
        this.appName = appName;
        this.configuredHost = configuredHost;
        this.port = port;
        this.registration = registration;
        this.jobSchedulerProperties = jobSchedulerProperties;
    }

    /**
     * 返回节点唯一标识。
     *
     * @return 节点唯一标识
     */
    public String nodeId() {
        return appName + "@" + host() + ":" + port;
    }

    /**
     * 返回当前服务名称。
     *
     * @return 服务名称
     */
    public String appName() {
        return appName;
    }

    /**
     * 返回当前主机地址。
     *
     * @return 主机地址
     */
    public String host() {
        if (configuredHost != null && !configuredHost.isBlank()) {
            return configuredHost;
        }
        if (registration != null && registration.getHost() != null && !registration.getHost().isBlank()) {
            return registration.getHost();
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception exception) {
            return "127.0.0.1";
        }
    }

    /**
     * 返回当前端口。
     *
     * @return 端口
     */
    public int port() {
        return port;
    }

    /**
     * 返回注册中心实例 ID。
     *
     * @return 实例 ID
     */
    public String instanceId() {
        return registration == null ? nodeId() : registration.getInstanceId();
    }

    /**
     * 进入执行中的任务数量加一。
     */
    public void incrementRunningCount() {
        runningCount.incrementAndGet();
    }

    /**
     * 执行结束的任务数量减一。
     */
    public void decrementRunningCount() {
        runningCount.updateAndGet(value -> Math.max(value - 1, 0));
    }

    /**
     * 返回当前运行任务数。
     *
     * @return 当前运行任务数
     */
    public int runningCount() {
        return runningCount.get();
    }

    /**
     * 返回最大并发配置。
     *
     * @return 最大并发配置
     */
    public int maxConcurrentCount() {
        return 16;
    }

    /**
     * 返回节点离线判定秒数。
     *
     * @return 离线判定秒数
     */
    public int offlineSeconds() {
        return jobSchedulerProperties.getNodeOfflineSeconds();
    }
}
