package com.scott.payment.clearing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

/**
 * 清分服务容量、重试和内部调用参数；服务启动即处理全部合法交易，不提供业务启停或商户范围过滤。
 */
@ConfigurationProperties(prefix = "clearing")
public class ClearingProperties {

    /**
     * PROCESSING 租约超时秒数，必须大于零。
     */
    private int processingTimeoutSeconds = 120;

    /**
     * 业务延时重试最大次数，超过后进入人工复核。
     */
    private int maxRetryCount = 8;

    /**
     * 消费线程最小数量，必须大于零。
     */
    private int consumerMinThreads = 16;

    /**
     * 消费线程最大数量，必须不小于最小数量。
     */
    private int consumerMaxThreads = 64;

    /**
     * 允许调用清分内部接口的服务身份。
     */
    private List<String> internalAllowedCallers = List.of("service-admin", "service-job");

    /**
     * 读取 PROCESSING 租约超时秒数。
     *
     * @return PROCESSING 租约超时秒数
     */
    public int getProcessingTimeoutSeconds() {
        return processingTimeoutSeconds;
    }

    /**
     * 设置 PROCESSING 租约超时秒数，启动校验会拒绝非正数。
     *
     * @param processingTimeoutSeconds PROCESSING 租约超时秒数
     */
    public void setProcessingTimeoutSeconds(int processingTimeoutSeconds) {
        this.processingTimeoutSeconds = processingTimeoutSeconds;
    }

    /**
     * 读取业务延时重试最大次数。
     *
     * @return 业务延时重试最大次数
     */
    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    /**
     * 设置业务延时重试最大次数，启动校验会限制有效范围。
     *
     * @param maxRetryCount 业务延时重试最大次数
     */
    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    /**
     * 读取清分消费者最小线程数。
     *
     * @return 消费线程最小数量
     */
    public int getConsumerMinThreads() {
        return consumerMinThreads;
    }

    /**
     * 设置清分消费者最小线程数。
     *
     * @param consumerMinThreads 消费线程最小数量
     */
    public void setConsumerMinThreads(int consumerMinThreads) {
        this.consumerMinThreads = consumerMinThreads;
    }

    /**
     * 读取清分消费者最大线程数。
     *
     * @return 消费线程最大数量
     */
    public int getConsumerMaxThreads() {
        return consumerMaxThreads;
    }

    /**
     * 设置清分消费者最大线程数，启动校验会检查其不小于最小线程数。
     *
     * @param consumerMaxThreads 消费线程最大数量
     */
    public void setConsumerMaxThreads(int consumerMaxThreads) {
        this.consumerMaxThreads = consumerMaxThreads;
    }

    /**
     * 读取允许访问清分内部接口的服务身份列表。
     *
     * @return 清分内部接口允许的调用方
     */
    public List<String> getInternalAllowedCallers() {
        return internalAllowedCallers;
    }

    /**
     * 设置允许访问清分内部接口的服务身份列表。
     *
     * @param internalAllowedCallers 清分内部接口允许的调用方
     */
    public void setInternalAllowedCallers(List<String> internalAllowedCallers) {
        this.internalAllowedCallers = internalAllowedCallers;
    }

    /**
     * 校验服务启动所需的正式交易拓扑和运行参数。
     *
     * @param logicTableCount 当前已发布交易逻辑表数量
     * @throws IllegalStateException 交易拓扑或运行参数不满足自动清分要求时抛出
     */
    public void validateRuntime(int logicTableCount) {
        validateNumericBounds();
        if (logicTableCount != 28) {
            throw new IllegalStateException("automatic clearing requires the formal 28-table topology");
        }
    }

    /**
     * 启动前约束租约、重试和消费线程范围，防止错误配置导致无限重试或无界并发。
     */
    private void validateNumericBounds() {
        if (processingTimeoutSeconds < 1) {
            throw new IllegalStateException("clearing processing timeout must be positive");
        }
        if (maxRetryCount < 1) {
            throw new IllegalStateException("clearing max retry count must be positive");
        }
        if (consumerMinThreads < 1 || consumerMaxThreads < consumerMinThreads) {
            throw new IllegalStateException("clearing consumer thread bounds are invalid");
        }
        if (internalAllowedCallers == null || internalAllowedCallers.isEmpty()
                || internalAllowedCallers.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalStateException("clearing internal allowed callers must not be empty");
        }
    }
}
