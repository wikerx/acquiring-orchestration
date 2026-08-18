package com.scott.payment.component.db.systemconfig.model;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SystemConfigSnapshot
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : Admin、Merchant、OpenAPI 与交易服务共享的系统参数永久缓存快照
 * @status : create
 *
 * @param id 数据库主键
 * @param configName 参数名称
 * @param configKey 全局唯一参数键名
 * @param configValue 数据库原始参数值；加密配置保持密文
 * @param valueType 值类型
 * @param configGroup 配置分组
 * @param systemBuiltin 是否系统内置
 * @param visible 是否前端可见
 * @param encrypted 是否加密存储
 * @param status 启停状态
 * @param remark 备注
 * @param createdBy 创建人
 * @param updatedBy 更新人
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record SystemConfigSnapshot(
        Long id,
        String configName,
        String configKey,
        String configValue,
        Integer valueType,
        String configGroup,
        Integer systemBuiltin,
        Integer visible,
        Integer encrypted,
        Integer status,
        String remark,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    private static final int ENABLED = 1;

    /**
     * 获取可供运行服务使用的配置值。
     *
     * @return 配置启用且值非空时返回去除首尾空白的值，否则返回空
     */
    public Optional<String> enabledValue() {
        if (!Integer.valueOf(ENABLED).equals(status) || configValue == null) {
            return Optional.empty();
        }
        String normalizedValue = configValue.trim();
        return normalizedValue.isEmpty() ? Optional.empty() : Optional.of(normalizedValue);
    }
}
