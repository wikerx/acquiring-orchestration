package com.scott.payment.component.nacos.encryption;

import com.alibaba.nacos.plugin.datasource.impl.derby.ConfigInfoMapperByDerby;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AcquiringConfigInfoMapperByDerby
 * @date : 2026-09-02 00:00
 * @email : scott_x@163.com
 * @description : Nacos 2.3.2 Derby config_info Mapper 兼容实现；在启动全量 Dump 阶段同步读取
 * encrypted_data_key，确保 cipher-* 配置经历容器重启后仍可解密。
 * @status : create
 */
public final class AcquiringConfigInfoMapperByDerby extends ConfigInfoMapperByDerby {

    /**
     * 构造启动全量配置查询，并补齐加密数据密钥列。
     *
     * @param context Nacos 分页及正文读取上下文
     * @return 包含 encrypted_data_key 的 Derby 查询定义
     */
    @Override
    public MapperResult findAllConfigInfoFragment(MapperContext context) {
        return NacosConfigInfoMapperSqlSupport.includeEncryptedDataKey(
                super.findAllConfigInfoFragment(context));
    }
}
