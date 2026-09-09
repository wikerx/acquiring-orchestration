package com.scott.payment.component.nacos.encryption;

import com.alibaba.nacos.plugin.datasource.impl.mysql.ConfigInfoMapperByMySql;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AcquiringConfigInfoMapperByMySql
 * @date : 2026-09-02 00:00
 * @email : scott_x@163.com
 * @description : Nacos 2.3.2 MySQL config_info Mapper 兼容实现；统一修复测试、UAT、生产环境启动
 * 全量 Dump 遗漏 encrypted_data_key 的问题，与本地 Derby 保持相同密文恢复口径。
 * @status : create
 */
public final class AcquiringConfigInfoMapperByMySql extends ConfigInfoMapperByMySql {

    /**
     * 构造启动全量配置查询，并补齐加密数据密钥列。
     *
     * @param context Nacos 分页及正文读取上下文
     * @return 包含 encrypted_data_key 的 MySQL 查询定义
     */
    @Override
    public MapperResult findAllConfigInfoFragment(MapperContext context) {
        return NacosConfigInfoMapperSqlSupport.includeEncryptedDataKey(
                super.findAllConfigInfoFragment(context));
    }
}
