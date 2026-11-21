package com.scott.payment.component.db.merchant.support;

import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商户资料多选目录的存储编码。保证草稿长度校验和审核写入使用相同口径。
 */
public final class MerchantProfileDirectoryCsv {

    /** 商户主档目标市场列的字符上限。 */
    public static final int TARGET_MARKETS_LIMIT = 512;

    /** 商户主档预计交易币种列的字符上限。 */
    public static final int TRANSACTION_CURRENCIES_LIMIT = 256;

    private MerchantProfileDirectoryCsv() {
    }

    /**
     * 使用逗号连接有效条目。保留原目录已有值的写法，新值使用大写目录编码。
     *
     * @param current 当前正式资料中的目录条目
     * @param proposed 拟审核的有序目录条目
     * @return 写入主档的字符串，空选项集合对应空值
     */
    public static String encode(List<String> current, List<String> proposed) {
        if (proposed == null || proposed.isEmpty()) {
            return null;
        }
        Set<String> retained = current == null ? Set.of() : new HashSet<>(current);
        String csv = proposed.stream()
                .filter(StringUtils::hasText)
                .map(value -> retained.contains(value) ? value : value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.joining(","));
        return csv.isEmpty() ? null : csv;
    }

    /**
     * 按 MySQL 字符列的字符数判断编码是否超限。
     *
     * @param csv 持久化编码
     * @param maxLength 列允许的字符数
     * @return 是否超出存储长度
     */
    public static boolean exceedsLimit(String csv, int maxLength) {
        return csv != null && csv.codePointCount(0, csv.length()) > maxLength;
    }
}
