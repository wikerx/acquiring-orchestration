package com.scott.payment.admin.support.template;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.admin.support.risk.RiskFunctionDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 系统内置文件模板目录。
 *
 * <p>内置编码属于业务代码与模板资产之间的稳定契约，只能由该目录声明并通过初始化流程创建。
 * 普通页面新增模板不得自行指定固定编码。</p>
 */
public final class BuiltinFileTemplateCatalog {

    private static final String OWNER_ADMIN = "ADMIN";
    private static final String MODULE_RISK = "RISK";
    private static final String EXTENSION_XLSX = "xlsx";
    private static final List<Spec> SPECS = buildSpecs();

    private BuiltinFileTemplateCatalog() {
    }

    /** 返回全部系统内置模板定义。 */
    public static List<Spec> all() {
        return SPECS;
    }

    /** 按固定编码取得受控定义，拒绝任意自定义编码。 */
    public static Spec require(String templateCode) {
        String normalized = templateCode == null ? "" : templateCode.trim().toUpperCase(Locale.ROOT);
        return SPECS.stream()
                .filter(spec -> spec.templateCode().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "系统内置模板编码未登记"));
    }

    private static List<Spec> buildSpecs() {
        List<Spec> values = new ArrayList<>();
        for (RiskFunctionDefinition definition : RiskFunctionDefinition.all()) {
            values.add(new Spec(
                    "RISK_" + definition.getModuleType() + "_"
                            + definition.getFunctionCode().toUpperCase(Locale.ROOT) + "_IMPORT",
                    OWNER_ADMIN,
                    MODULE_RISK,
                    safeChineseBaseName(definition) + "导入模板",
                    englishName(definition) + " Import Template",
                    EXTENSION_XLSX,
                    definition.getPermissionPrefix() + ":template",
                    null,
                    definition.getModuleType(),
                    definition.getFunctionCode()
            ));
        }
        values.add(new Spec(
                "RISK_TRADE_BLACK_IMPORT",
                OWNER_ADMIN,
                MODULE_RISK,
                "系统交易加黑导入模板",
                "Transaction Blacklist Import Template",
                EXTENSION_XLSX,
                "risk:tradeBlack:system:batchAdd",
                null,
                null,
                null
        ));
        return List.copyOf(values);
    }

    /**
     * 风控功能名称用于页面标题时可以包含“/”，但模板基础文件名不能包含路径分隔符。
     * 这里仅转换文件名语义中的并列分隔符，不放宽统一的安全文件名校验。
     */
    private static String safeChineseBaseName(RiskFunctionDefinition definition) {
        return definition.getFunctionName().replaceAll("[\\\\/]", "与");
    }

    private static String englishName(RiskFunctionDefinition definition) {
        String code = definition.getFunctionCode().replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        String[] words = code.split(" ");
        StringBuilder result = new StringBuilder(definition.getModuleType());
        for (String word : words) {
            if (!word.isBlank()) {
                result.append(' ')
                        .append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));
            }
        }
        return result.toString();
    }

    /** 单个系统内置模板的稳定定义。 */
    public record Spec(String templateCode,
                       String ownerSystem,
                       String moduleCode,
                       String nameZh,
                       String nameEn,
                       String fileExtension,
                       String adminPermissionCode,
                       String merchantPermissionCode,
                       String sourceModuleType,
                       String sourceFunctionCode) {

        /** 是否使用独立的系统交易加黑模板生成器。 */
        public boolean tradeBlackTemplate() {
            return "RISK_TRADE_BLACK_IMPORT".equals(templateCode);
        }
    }
}
