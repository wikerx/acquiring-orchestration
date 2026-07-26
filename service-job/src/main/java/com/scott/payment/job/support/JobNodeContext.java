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

    /**
     * app Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String appName;
    /**
     * configured Host，表示远程服务主机、商户域名或渠道访问域名。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String configuredHost;
    /**
     * port，用于保存 Job Node Context 中与 port 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final int port;
    /**
     * registration，用于保存 Job Node Context 中与 registration 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final Registration registration;
    /**
     * job Scheduler Properties，用于保存 Job Node Context 中与 jobschedulerproperties 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final JobSchedulerProperties jobSchedulerProperties;
    /**
     * running Count，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
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
