package com.scott.payment.admin.support.template;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 文件模板所属业务模块目录。
 *
 * <p>页面只展示本地化名称，数据库保存稳定模块编码。模块目录由后端统一维护，
 * 禁止前端和操作人员分别维护自由文本编码。</p>
 */
public enum FileTemplateModuleCatalog {

    SYSTEM("SYSTEM", "系统管理", "System Management", Set.of("ADMIN", "MERCHANT", "SHARED")),
    MERCHANT("MERCHANT", "商户管理", "Merchant Management", Set.of("ADMIN", "MERCHANT", "SHARED")),
    TRANSACTION("TRANSACTION", "交易管理", "Transaction Management", Set.of("ADMIN", "MERCHANT", "SHARED")),
    RISK("RISK", "风险管理", "Risk Management", Set.of("ADMIN")),
    CHANNEL("CHANNEL", "通道管理", "Channel Management", Set.of("ADMIN")),
    FINANCE("FINANCE", "财务管理", "Finance Management", Set.of("ADMIN", "MERCHANT", "SHARED")),
    SETTLEMENT("SETTLEMENT", "结算管理", "Settlement Management", Set.of("ADMIN", "MERCHANT", "SHARED")),
    OPERATIONS("OPERATIONS", "运营管理", "Operations Management", Set.of("ADMIN", "MERCHANT", "SHARED")),
    PAYMENT_ACCESS("PAYMENT_ACCESS", "支付接入管理", "Payment Access Management", Set.of("ADMIN", "MERCHANT", "SHARED")),
    BASE("BASE", "基础数据", "Base Data", Set.of("ADMIN", "SHARED"));

    private final String code;
    private final String nameZh;
    private final String nameEn;
    private final Set<String> ownerSystems;

    FileTemplateModuleCatalog(String code, String nameZh, String nameEn, Set<String> ownerSystems) {
        this.code = code;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.ownerSystems = Set.copyOf(ownerSystems);
    }

    /** 返回全部受控模块。 */
    public static List<FileTemplateModuleCatalog> all() {
        return Arrays.asList(values());
    }

    /** 校验模块编码存在且适用于当前隶属系统。 */
    public static FileTemplateModuleCatalog require(String ownerSystem, String moduleCode) {
        String normalizedOwner = ownerSystem == null ? "" : ownerSystem.trim().toUpperCase(Locale.ROOT);
        String normalizedModule = moduleCode == null ? "" : moduleCode.trim().toUpperCase(Locale.ROOT);
        return all().stream()
                .filter(item -> item.code.equals(normalizedModule) && item.ownerSystems.contains(normalizedOwner))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "所属模块不适用于当前系统"));
    }

    public String getCode() {
        return code;
    }

    public String getNameZh() {
        return nameZh;
    }

    public String getNameEn() {
        return nameEn;
    }

    public Set<String> getOwnerSystems() {
        return ownerSystems;
    }
}
