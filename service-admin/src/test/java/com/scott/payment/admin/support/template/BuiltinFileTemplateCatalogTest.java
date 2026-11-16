package com.scott.payment.admin.support.template;

import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 系统内置模板目录契约测试。 */
class BuiltinFileTemplateCatalogTest {

    /** 当前全部风控导入模板和交易加黑模板必须有唯一固定编码。 */
    @Test
    void shouldExposeFortySixUniqueBuiltinTemplateCodes() {
        var specs = BuiltinFileTemplateCatalog.all();

        assertThat(specs).hasSize(46);
        assertThat(new HashSet<>(specs.stream().map(BuiltinFileTemplateCatalog.Spec::templateCode).toList()))
                .hasSize(46);
        assertThat(specs).allSatisfy(spec -> {
            assertThat(spec.ownerSystem()).isEqualTo("ADMIN");
            assertThat(spec.moduleCode()).isEqualTo("RISK");
            assertThat(spec.fileExtension()).isEqualTo("xlsx");
            assertThat(spec.nameZh())
                    .isNotBlank()
                    .doesNotContain("/", "\\")
                    .doesNotEndWith(".xlsx");
            assertThat(spec.nameEn())
                    .isNotBlank()
                    .doesNotContain("/", "\\")
                    .doesNotEndWith(".xlsx");
            assertThat(spec.adminPermissionCode())
                    .isNotBlank()
                    .matches("[a-z][A-Za-z0-9:-]{2,127}");
            assertThat(spec.merchantPermissionCode()).isNull();
        });
    }

    /** 任意固定编码不得绕过后端内置目录。 */
    @Test
    void shouldRejectUnregisteredBuiltinTemplateCode() {
        assertThatThrownBy(() -> BuiltinFileTemplateCatalog.require("CUSTOM_FIXED_CODE"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("系统内置模板编码未登记");
    }
}
