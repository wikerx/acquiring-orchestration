package com.scott.payment.merchant.application.access;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.IpWhitelistItem;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.IpWhitelistSubmitRequest;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlItem;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlSubmitRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 商户访问配置应用服务，编排当前认证商户的来源网址和 IP 白名单查询及待审提交。
 */
@Service
public class MerchantAccessConfigApplicationService {

    private static final int PENDING = 0;
    private static final int DISABLED = 0;
    private static final String MERCHANT = "MERCHANT";
    private static final String SOURCE_URL_SELECT = """
            SELECT id, merchant_id, source_url, source_host, status, approval_status,
                   approval_remark, submit_source, review_by, review_time, remark,
                   create_time, update_time
            FROM risk_rule_source_url
            WHERE merchant_id = :merchantId AND deleted = 0
            ORDER BY update_time DESC, id DESC
            """;
    private static final String IP_WHITELIST_SELECT = """
            SELECT id, merchant_id, ip_type, ip_value, status, approval_status,
                   approval_remark, submit_source, review_by, review_time, remark,
                   gmt_create, gmt_modified
            FROM merchant_ip_whitelist
            WHERE merchant_id = :merchantId AND deleted = 0
            ORDER BY gmt_modified DESC, id DESC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建商户访问配置应用服务。
     *
     * @param jdbcTemplate 本地管理数据源 JDBC 模板
     */
    public MerchantAccessConfigApplicationService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询当前认证商户的来源网址。
     *
     * @param merchantId 已认证商户号
     * @return 商户全部来源网址记录
     */
    @DS(DataSourceName.MASTER)
    public List<SourceUrlItem> listSourceUrls(String merchantId) {
        String normalizedMerchantId = requireMerchantId(merchantId);
        return jdbcTemplate.query(SOURCE_URL_SELECT,
                new MapSqlParameterSource("merchantId", normalizedMerchantId), sourceUrlMapper());
    }

    /**
     * 提交当前认证商户的来源网址。
     *
     * @param merchantId 已认证商户号
     * @param request    来源网址和提交说明
     * @return 新增待审核记录
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public List<SourceUrlItem> submitSourceUrls(String merchantId, SourceUrlSubmitRequest request) {
        String normalizedMerchantId = requireMerchantId(merchantId);
        if (request == null) {
            throw badRequest("来源网址请求不能为空");
        }
        String remark = normalizeRemark(request.getRemark(), 500);
        List<SourceUrlValue> values = normalizeSourceUrls(request.getSourceUrls());
        LocalDateTime now = LocalDateTime.now();
        String operator = MERCHANT + ":" + normalizedMerchantId;
        List<SourceUrlItem> created = new ArrayList<>(values.size());
        for (SourceUrlValue value : values) {
            assertSourceUrlAvailable(normalizedMerchantId, value.host());
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("merchantId", normalizedMerchantId)
                    .addValue("sourceUrl", value.url())
                    .addValue("sourceHost", value.host())
                    .addValue("status", DISABLED)
                    .addValue("approvalStatus", PENDING)
                    .addValue("submitSource", MERCHANT)
                    .addValue("remark", remark)
                    .addValue("operator", operator)
                    .addValue("now", now);
            try {
                jdbcTemplate.update("""
                        INSERT INTO risk_rule_source_url (
                            merchant_id, source_url, source_host, risk_level, decision_action,
                            status, approval_status, approval_remark, submit_source,
                            review_by, review_time, remark, create_by, update_by,
                            create_time, update_time, deleted
                        ) VALUES (
                            :merchantId, :sourceUrl, :sourceHost, 'MEDIUM', 'REVIEW',
                            :status, :approvalStatus, NULL, :submitSource,
                            NULL, NULL, :remark, :operator, :operator,
                            :now, :now, 0
                        )
                        """, params, keyHolder, new String[]{"id"});
            } catch (DuplicateKeyException exception) {
                throw badRequest("来源网址已存在");
            }
            created.add(sourceUrlItem(keyHolder.getKey(), normalizedMerchantId, value, remark, now));
        }
        return created;
    }

    /**
     * 查询当前认证商户的 IP 白名单。
     *
     * @param merchantId 已认证商户号
     * @return 商户全部 IP 白名单记录
     */
    @DS(DataSourceName.MASTER)
    public List<IpWhitelistItem> listIpWhitelists(String merchantId) {
        String normalizedMerchantId = requireMerchantId(merchantId);
        return jdbcTemplate.query(IP_WHITELIST_SELECT,
                new MapSqlParameterSource("merchantId", normalizedMerchantId), ipWhitelistMapper());
    }

    /**
     * 提交当前认证商户的 IP 白名单。
     *
     * @param merchantId 已认证商户号
     * @param request    IP 地址和提交说明
     * @return 新增待审核记录
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public List<IpWhitelistItem> submitIpWhitelists(String merchantId, IpWhitelistSubmitRequest request) {
        String normalizedMerchantId = requireMerchantId(merchantId);
        if (request == null) {
            throw badRequest("白名单请求不能为空");
        }
        String remark = normalizeRemark(request.getRemark(), 512);
        List<IpAddressNormalizer.NormalizedIp> values = normalizeIps(request.getIpValues());
        LocalDateTime now = LocalDateTime.now();
        String operator = MERCHANT + ":" + normalizedMerchantId;
        List<IpWhitelistItem> created = new ArrayList<>(values.size());
        for (IpAddressNormalizer.NormalizedIp value : values) {
            assertIpAvailable(normalizedMerchantId, value.ipValue());
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("merchantId", normalizedMerchantId)
                    .addValue("ipType", value.ipType())
                    .addValue("ipValue", value.ipValue())
                    .addValue("status", DISABLED)
                    .addValue("approvalStatus", PENDING)
                    .addValue("submitSource", MERCHANT)
                    .addValue("remark", remark)
                    .addValue("operator", operator)
                    .addValue("now", now);
            try {
                jdbcTemplate.update("""
                        INSERT INTO merchant_ip_whitelist (
                            merchant_id, ip_type, ip_value, status, approval_status,
                            approval_remark, submit_source, review_by, review_time,
                            remark, create_by, update_by, gmt_create, gmt_modified, deleted
                        ) VALUES (
                            :merchantId, :ipType, :ipValue, :status, :approvalStatus,
                            NULL, :submitSource, NULL, NULL,
                            :remark, :operator, :operator, :now, :now, 0
                        )
                        """, params, keyHolder, new String[]{"id"});
            } catch (DuplicateKeyException exception) {
                throw badRequest("同一商户下 IP 白名单不能重复");
            }
            created.add(ipWhitelistItem(keyHolder.getKey(), normalizedMerchantId, value, remark, now));
        }
        return created;
    }

    /**
     * 校验来源域名在当前商户的访问配置中未重复。
     */
    private void assertSourceUrlAvailable(String merchantId, String sourceHost) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM risk_rule_source_url
                WHERE merchant_id = :merchantId AND source_host = :sourceHost AND deleted = 0
                """, new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("sourceHost", sourceHost), Long.class);
        if (count != null && count > 0) {
            throw badRequest("来源网址已存在");
        }
    }

    /**
     * 校验 IP 地址在当前商户的白名单中未重复。
     */
    private void assertIpAvailable(String merchantId, String ipValue) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM merchant_ip_whitelist
                WHERE merchant_id = :merchantId AND ip_value = :ipValue AND deleted = 0
                """, new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("ipValue", ipValue), Long.class);
        if (count != null && count > 0) {
            throw badRequest("同一商户下 IP 白名单不能重复");
        }
    }

    /**
     * 规范化店铺网址并拒绝重复或超出数量限制的域名。
     */
    private List<SourceUrlValue> normalizeSourceUrls(List<String> sourceUrls) {
        Map<String, SourceUrlValue> normalized = new LinkedHashMap<>();
        if (sourceUrls != null) {
            for (String sourceUrl : sourceUrls) {
                SourceUrlValue value = normalizeSourceUrl(sourceUrl);
                if (normalized.putIfAbsent(value.host(), value) != null) {
                    throw badRequest("来源网址已存在");
                }
            }
        }
        if (normalized.isEmpty()) {
            throw badRequest("至少录入一个来源网址");
        }
        return List.copyOf(normalized.values());
    }

    /**
     * 校验店铺网址协议和主机名，并生成用于唯一性查询的规范化值。
     */
    private SourceUrlValue normalizeSourceUrl(String sourceUrl) {
        String text = trimToNull(sourceUrl);
        if (text == null) {
            throw badRequest("请输入允许来源网址");
        }
        if (text.length() > 512) {
            throw badRequest("来源网址长度不能超过512个字符");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw badRequest("允许来源网址必须以 http:// 或 https:// 开头");
        }
        try {
            URI uri = new URI(text);
            String host = uri.getHost();
            String scheme = uri.getScheme();
            if (!StringUtils.hasText(host)
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw badRequest("来源网址格式不正确");
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (normalizedHost.length() > 255) {
                throw badRequest("来源网址 Host 长度不能超过255个字符");
            }
            return new SourceUrlValue(text, normalizedHost);
        } catch (URISyntaxException exception) {
            throw badRequest("来源网址格式不正确");
        }
    }

    /**
     * 规范化 IP 白名单并拒绝重复或超出数量限制的地址。
     */
    private List<IpAddressNormalizer.NormalizedIp> normalizeIps(List<String> ipValues) {
        Map<String, IpAddressNormalizer.NormalizedIp> normalized = new LinkedHashMap<>();
        if (ipValues != null) {
            for (String value : ipValues) {
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                try {
                    IpAddressNormalizer.NormalizedIp ip = IpAddressNormalizer.normalizeExact(value);
                    normalized.putIfAbsent(ip.ipValue(), ip);
                } catch (IllegalArgumentException exception) {
                    throw badRequest(exception.getMessage());
                }
            }
        }
        if (normalized.isEmpty()) {
            throw badRequest("至少录入一个精确 IP");
        }
        return List.copyOf(normalized.values());
    }

    /**
     * 强制使用认证上下文提供的商户号作为本地查询和修改边界，禁止接受浏览器自报商户身份。
     *
     * @param merchantId 当前认证上下文中的商户号
     * @return 规范化后的可信商户号
     * @throws ServiceException 认证上下文缺少商户号时抛出
     */
    private String requireMerchantId(String merchantId) {
        String normalized = trimToNull(merchantId);
        if (normalized == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return normalized;
    }

    /**
     * 校验并规范化备注。
     */
    private String normalizeRemark(String remark, int maxLength) {
        String normalized = trimToNull(remark);
        if (normalized != null && normalized.length() > maxLength) {
            throw badRequest("提交说明长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    /**
     * 去除字符串首尾空白，并将空字符串转换为空值。
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 创建参数校验失败业务异常。
     */
    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    /**
     * 创建来源网址数据库结果映射器。
     */
    private RowMapper<SourceUrlItem> sourceUrlMapper() {
        return (rs, rowNum) -> {
            SourceUrlItem item = new SourceUrlItem();
            item.setId(rs.getString("id"));
            item.setMerchantId(rs.getString("merchant_id"));
            item.setSourceUrl(rs.getString("source_url"));
            item.setSourceHost(rs.getString("source_host"));
            item.setStatus(rs.getInt("status"));
            item.setApprovalStatus(rs.getInt("approval_status"));
            item.setApprovalRemark(rs.getString("approval_remark"));
            item.setSubmitSource(rs.getString("submit_source"));
            item.setReviewBy(rs.getString("review_by"));
            item.setReviewTime(toLocalDateTime(rs.getTimestamp("review_time")));
            item.setRemark(rs.getString("remark"));
            item.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            item.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            return item;
        };
    }

    /**
     * 创建 IP 白名单数据库结果映射器。
     */
    private RowMapper<IpWhitelistItem> ipWhitelistMapper() {
        return (rs, rowNum) -> {
            IpWhitelistItem item = new IpWhitelistItem();
            item.setId(rs.getString("id"));
            item.setMerchantId(rs.getString("merchant_id"));
            item.setIpType(rs.getString("ip_type"));
            item.setIpValue(rs.getString("ip_value"));
            item.setStatus(rs.getInt("status"));
            item.setApprovalStatus(rs.getInt("approval_status"));
            item.setApprovalRemark(rs.getString("approval_remark"));
            item.setSubmitSource(rs.getString("submit_source"));
            item.setReviewBy(rs.getString("review_by"));
            item.setReviewTime(toLocalDateTime(rs.getTimestamp("review_time")));
            item.setRemark(rs.getString("remark"));
            item.setGmtCreate(toLocalDateTime(rs.getTimestamp("gmt_create")));
            item.setGmtModified(toLocalDateTime(rs.getTimestamp("gmt_modified")));
            return item;
        };
    }

    /**
     * 将来源网址查询行转换为商户响应条目。
     */
    private SourceUrlItem sourceUrlItem(Number id, String merchantId, SourceUrlValue value,
                                        String remark, LocalDateTime now) {
        SourceUrlItem item = new SourceUrlItem();
        item.setId(id == null ? null : id.toString());
        item.setMerchantId(merchantId);
        item.setSourceUrl(value.url());
        item.setSourceHost(value.host());
        item.setStatus(DISABLED);
        item.setApprovalStatus(PENDING);
        item.setSubmitSource(MERCHANT);
        item.setRemark(remark);
        item.setCreateTime(now);
        item.setUpdateTime(now);
        return item;
    }

    /**
     * 将 IP 白名单查询行转换为商户响应条目。
     */
    private IpWhitelistItem ipWhitelistItem(Number id, String merchantId,
                                             IpAddressNormalizer.NormalizedIp value,
                                             String remark, LocalDateTime now) {
        IpWhitelistItem item = new IpWhitelistItem();
        item.setId(id == null ? null : id.toString());
        item.setMerchantId(merchantId);
        item.setIpType(value.ipType());
        item.setIpValue(value.ipValue());
        item.setStatus(DISABLED);
        item.setApprovalStatus(PENDING);
        item.setSubmitSource(MERCHANT);
        item.setRemark(remark);
        item.setGmtCreate(now);
        item.setGmtModified(now);
        return item;
    }

    /**
     * 将数据库时间戳转换为本地日期时间。
     */
    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    /**
     * 规范化后的来源网址值及其数据库查询键。
     */
    private record SourceUrlValue(String url, String host) {
    }
}
