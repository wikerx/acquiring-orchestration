package com.scott.payment.admin.application.system;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.CreateRequest;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Query;
import com.scott.payment.admin.entity.system.FileTemplateEntities.FileTemplateDO;
import com.scott.payment.admin.infrastructure.storage.ObjectStorageProperties;
import com.scott.payment.admin.infrastructure.storage.TemplateObjectStorageService;
import com.scott.payment.admin.mapper.SystemFileTemplateAssetMapper;
import com.scott.payment.admin.mapper.SystemFileTemplateMapper;
import com.scott.payment.admin.mapper.SystemFileTemplateVersionMapper;
import com.scott.payment.admin.support.template.TemplateFileSecurityValidator;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 模板中心查询条件的回归测试。 */
@ExtendWith(MockitoExtension.class)
class AdminFileTemplateApplicationServiceTest {

    @Mock
    private SystemFileTemplateMapper templateMapper;
    @Mock
    private SystemFileTemplateVersionMapper versionMapper;
    @Mock
    private SystemFileTemplateAssetMapper assetMapper;
    @Mock
    private TemplateFileSecurityValidator fileValidator;
    @Mock
    private ObjectProvider<TemplateObjectStorageService> storageProvider;
    @Mock
    private GlobalIdGenerator globalIdGenerator;
    @Mock
    private SysAppMapper appMapper;
    @Mock
    private SysPermissionMapper permissionMapper;

    private AdminFileTemplateApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AdminFileTemplateApplicationService(templateMapper, versionMapper, assetMapper,
                fileValidator, storageProvider, new ObjectStorageProperties(), globalIdGenerator,
                appMapper, permissionMapper);
    }

    /** 未选择隶属系统表示查询全部，不应进入必填校验。 */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldSearchAllOwnerSystemsWhenOwnerSystemIsEmpty() {
        Page<FileTemplateDO> source = new Page<>(1, 20, 0);
        source.setRecords(List.of());
        when(templateMapper.selectPage(any(Page.class), any())).thenReturn(source);
        Query query = new Query();
        query.setOwnerSystem("");

        var result = service.page(query);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        verify(templateMapper).selectPage(any(Page.class), any());
    }

    /** 权限模型允许 lowerCamelCase 分段，模板中心不得把既有合法权限误判为格式错误。 */
    @Test
    void shouldAcceptCamelCasePermissionSegmentsBeforeCheckingPermissionExistence() {
        SysAppDO adminApp = new SysAppDO();
        adminApp.setId(1L);
        when(appMapper.selectOne(any())).thenReturn(adminApp);

        CreateRequest request = new CreateRequest();
        request.setOwnerSystem("ADMIN");
        request.setModuleCode("RISK");
        request.setNameZh("卡BIN黑名单导入模板");
        request.setNameEn("Card BIN Blacklist Import Template");
        request.setFileExtension("xlsx");
        request.setAdminPermissionCode("risk:blacklist:cardBin:template");

        assertThatThrownBy(() -> service.create(request))
                .hasMessageContaining("下载权限不存在或已停用")
                .hasMessageNotContaining("格式不正确");
    }
}
