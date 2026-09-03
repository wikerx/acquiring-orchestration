package com.scott.payment.component.nacos.encryption;

import com.alibaba.nacos.plugin.datasource.constants.ContextConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NacosConfigInfoEncryptedKeyCompatibilityTest
 * @date : 2026-09-02 00:00
 * @email : scott_x@163.com
 * @description : 验证 Nacos 配置正文查询会同步读取 encrypted_data_key，且非正文查询和已兼容 SQL 不变。
 * @status : create
 */
class NacosConfigInfoEncryptedKeyCompatibilityTest {

    /** 缺少 encrypted_data_key 的正文查询必须补齐该列。 */
    @Test
    void shouldIncludeEncryptedDataKeyWhenContentIsSelected() {
        String sql = "SELECT id,data_id,content,md5 FROM config_info WHERE id > ?";

        assertThat(NacosConfigInfoEncryptedKeyCompatibility.includeEncryptedDataKey(sql))
                .isEqualTo("SELECT id,data_id,content,md5,encrypted_data_key FROM config_info WHERE id > ?");
    }

    /** 带表别名和分页子查询的正文查询也必须补齐该列。 */
    @Test
    void shouldIncludeEncryptedDataKeyForAliasedQuery() {
        String sql = " SELECT t.id,data_id,content,md5 FROM (SELECT id FROM config_info) g, config_info t";

        assertThat(NacosConfigInfoEncryptedKeyCompatibility.includeEncryptedDataKey(sql))
                .isEqualTo(" SELECT t.id,data_id,content,md5,encrypted_data_key FROM (SELECT id FROM config_info) "
                        + "g, config_info t");
    }

    /** 不读取正文或已包含加密数据密钥的查询不得被重复改写。 */
    @Test
    void shouldKeepUnrelatedAndCompatibleQueriesUnchanged() {
        String metadataSql = "SELECT id,data_id,md5 FROM config_info";
        String compatibleSql = "SELECT id,data_id,content,encrypted_data_key FROM config_info";

        assertThat(NacosConfigInfoEncryptedKeyCompatibility.includeEncryptedDataKey(metadataSql))
                .isEqualTo(metadataSql);
        assertThat(NacosConfigInfoEncryptedKeyCompatibility.includeEncryptedDataKey(compatibleSql))
                .isEqualTo(compatibleSql);
    }

    /** Derby 与 MySQL 的启动全量 Dump 查询必须同时包含加密数据密钥。 */
    @Test
    void startupDumpMappersShouldIncludeEncryptedDataKey() {
        MapperContext context = new MapperContext();
        context.putContextParameter(ContextConstant.NEED_CONTENT, Boolean.TRUE.toString());
        context.putWhereParameter(FieldConstant.ID, 0L);
        context.setStartRow(0);
        context.setPageSize(100);

        assertThat(new AcquiringConfigInfoMapperByDerby().findAllConfigInfoFragment(context).getSql())
                .contains("content", "encrypted_data_key");
        assertThat(new AcquiringConfigInfoMapperByMySql().findAllConfigInfoFragment(context).getSql())
                .contains("content", "encrypted_data_key");
    }
}
