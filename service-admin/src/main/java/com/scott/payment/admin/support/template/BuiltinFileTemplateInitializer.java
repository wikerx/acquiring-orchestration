package com.scott.payment.admin.support.template;

import com.scott.payment.admin.application.risk.AdminRiskManagementApplicationService;
import com.scott.payment.admin.application.risk.AdminTradeBlackWorkbookApplicationService;
import com.scott.payment.admin.application.system.AdminFileTemplateApplicationService;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.CreateRequest;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Detail;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.PublishRequest;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Version;
import com.scott.payment.admin.support.template.BuiltinFileTemplateCatalog.Spec;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 幂等初始化系统内置模板定义及首个已发布版本。
 *
 * <p>初始化只补缺失定义或尚未发布首版的模板，绝不覆盖运营人员已维护的名称、权限、
 * 状态或后续版本。生成的正文仍走标准文件安全校验、私有对象存储、不可变版本和发布 CAS。</p>
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class BuiltinFileTemplateInitializer implements ApplicationRunner {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final AdminFileTemplateApplicationService templateService;
    private final AdminRiskManagementApplicationService riskService;
    private final AdminTradeBlackWorkbookApplicationService tradeBlackWorkbookService;

    public BuiltinFileTemplateInitializer(AdminFileTemplateApplicationService templateService,
                                          AdminRiskManagementApplicationService riskService,
                                          AdminTradeBlackWorkbookApplicationService tradeBlackWorkbookService) {
        this.templateService = templateService;
        this.riskService = riskService;
        this.tradeBlackWorkbookService = tradeBlackWorkbookService;
    }

    @Override
    public void run(ApplicationArguments args) {
        InternalAuthAccount previous = InternalAuthContextHolder.get();
        InternalAuthContextHolder.set(systemAccount());
        int initialized = 0;
        int skipped = 0;
        int failed = 0;
        try {
            for (Spec spec : BuiltinFileTemplateCatalog.all()) {
                try {
                    Detail detail = templateService.findDetailByTemplateCode(spec.templateCode())
                            .orElseGet(() -> templateService.create(createRequest(spec)));
                    if (detail.getCurrentVersionId() != null) {
                        skipped++;
                        continue;
                    }
                    Version version = templateService.uploadVersion(
                            detail.getId(),
                            1,
                            "1.0",
                            "BUILTIN_" + spec.templateCode() + "_V1",
                            "系统内置模板首版",
                            file("zhFile", spec.nameZh(), templateContent(spec, Locale.SIMPLIFIED_CHINESE)),
                            file("enFile", spec.nameEn(), templateContent(spec, Locale.US))
                    );
                    PublishRequest publish = new PublishRequest();
                    publish.setExpectedTemplateVersion(detail.getVersion());
                    publish.setRequestId("PUBLISH_" + spec.templateCode() + "_V1");
                    publish.setReason("系统内置模板首版初始化");
                    templateService.publish(detail.getId(), version.getId(), publish);
                    initialized++;
                } catch (RuntimeException exception) {
                    failed++;
                    log.error("系统内置模板初始化失败, event: BUILTIN_FILE_TEMPLATE_INIT_FAILED, templateCode: {}, exceptionType: {}, message: {}",
                            spec.templateCode(), exception.getClass().getSimpleName(), exception.getMessage(), exception);
                }
            }
        } finally {
            if (previous == null) {
                InternalAuthContextHolder.clear();
            } else {
                InternalAuthContextHolder.set(previous);
            }
        }
        log.info("系统内置模板初始化完成, event: BUILTIN_FILE_TEMPLATE_INIT_COMPLETED, initialized: {}, skipped: {}, failed: {}",
                initialized, skipped, failed);
    }

    private CreateRequest createRequest(Spec spec) {
        CreateRequest request = new CreateRequest();
        request.setTemplateCode(spec.templateCode());
        request.setOwnerSystem(spec.ownerSystem());
        request.setModuleCode(spec.moduleCode());
        request.setNameZh(spec.nameZh());
        request.setNameEn(spec.nameEn());
        request.setFileExtension(spec.fileExtension());
        request.setAdminPermissionCode(spec.adminPermissionCode());
        request.setMerchantPermissionCode(spec.merchantPermissionCode());
        request.setRemark("系统内置模板，由模板中心统一维护");
        return request;
    }

    private byte[] templateContent(Spec spec, Locale locale) {
        if (spec.tradeBlackTemplate()) {
            return tradeBlackWorkbookService.builtinTemplateContent(locale);
        }
        return riskService.builtinTemplateContent(spec.sourceModuleType(), spec.sourceFunctionCode(), locale);
    }

    private InMemoryTemplateMultipartFile file(String name, String baseName, byte[] content) {
        return new InMemoryTemplateMultipartFile(
                name, baseName + ".xlsx", XLSX_CONTENT_TYPE, content);
    }

    private InternalAuthAccount systemAccount() {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("ADMIN");
        account.setAppId(0L);
        account.setAccountId(0L);
        account.setUserId(0L);
        account.setLoginAccount("SYSTEM");
        account.setRealName("SYSTEM");
        account.setRoles(List.of("SYSTEM"));
        account.setPermissions(List.of("*:*:*"));
        return account;
    }
}
