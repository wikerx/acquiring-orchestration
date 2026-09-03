package com.scott.payment.component.nacos.encryption;

import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NacosConfigInfoMapperSqlSupport
 * @date : 2026-09-02 00:00
 * @email : scott_x@163.com
 * @description : Nacos Config Server 专用 MapperResult 改写适配器；隔离 datasource 服务端类型，避免普通业务
 * 客户端加载 AES-GCM SPI 时依赖 Nacos 服务端数据源包。
 * @status : create
 */
final class NacosConfigInfoMapperSqlSupport {

    private NacosConfigInfoMapperSqlSupport() {
    }

    /**
     * 就地补齐 MapperResult 的 encrypted_data_key 选择列。
     *
     * @param result Nacos 原始 Mapper 查询定义
     * @return 已补齐加密数据密钥列的同一查询定义
     */
    static MapperResult includeEncryptedDataKey(MapperResult result) {
        if (result == null) {
            return null;
        }
        result.setSql(NacosConfigInfoEncryptedKeyCompatibility.includeEncryptedDataKey(result.getSql()));
        return result;
    }
}
