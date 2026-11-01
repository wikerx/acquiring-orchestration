package com.scott.payment.admin.dto.monitor;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Redis 连接和节点运行信息响应。
 *
 * <p>节点属性来自 Redis INFO，因此节点内部保留字符串键值；外围连接状态和拓扑摘要使用固定字段，
 * 避免 Redis 客户端返回结构直接扩展管理端接口。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CacheInfoResponse {

    /**
     * 当前 Redis INFO 是否完整可用。
     */
    private Boolean connected;

    /**
     * Redis 部署模式，例如 standalone 或 cluster。
     */
    private String deploymentMode;

    /**
     * 当前拓扑中的 Master 节点数量。
     */
    private Integer masterCount;

    /**
     * 首个可用 Master 的 Redis INFO 摘要。
     */
    private Map<String, String> info;

    /**
     * 按稳定节点标识分组的 Redis INFO 属性。
     */
    private Map<String, Map<String, String>> nodes;

    /**
     * INFO 读取失败的节点标识列表。
     */
    private List<String> failedNodes;

    /**
     * Redis INFO 不完整或不可用时的安全错误摘要。
     */
    private String message;
}
