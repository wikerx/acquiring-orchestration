package com.scott.payment.admin.application.base;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.base.IpLibraryDTOs;
import com.scott.payment.admin.entity.base.IpLibraryEntities;
import com.scott.payment.admin.mapper.IpLibraryDataMapper;
import com.scott.payment.admin.mapper.IpLibrarySplitModelMapper;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.reference.cache.IpLookupCacheReader;
import com.scott.payment.component.db.reference.model.IpLookupCacheEntry;
import com.scott.payment.component.db.reference.model.IpLookupDetail;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 管理端 IP 地址库应用服务，用于运营后台，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 */
@Service
public class AdminBaseIpLibraryApplicationService {

    private static final String IPV4 = "IPV4";
    private static final String IPV6 = "IPV6";
    private static final int ACTIVE = 1;
    private static final long IPV4_MAX = 4_294_967_295L;
    private static final BigInteger IPV6_MAX = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
    private static final Set<String> ALLOWED_TABLES = IntStream.rangeClosed(1, 8)
            .mapToObj(index -> String.format("%02d", index))
            .flatMap(suffix -> List.of("ip_library_v4_data_" + suffix, "ip_library_v6_data_" + suffix).stream())
            .collect(Collectors.toUnmodifiableSet());

    private final IpLibrarySplitModelMapper splitModelMapper;
    private final IpLibraryDataMapper dataMapper;
    private final IpLookupCacheReader ipLookupCacheReader;

    /**
     * 创建全球 IP 库应用服务。
     */
    public AdminBaseIpLibraryApplicationService(IpLibrarySplitModelMapper splitModelMapper,
                                                IpLibraryDataMapper dataMapper,
                                                IpLookupCacheReader ipLookupCacheReader) {
        this.splitModelMapper = splitModelMapper;
        this.dataMapper = dataMapper;
        this.ipLookupCacheReader = ipLookupCacheReader;
    }

    /**
     * 分页查询 IP 库区间。
     */
    @DS(DataSourceName.SLAVE)
    public PageResult<IpLibraryDTOs.IpLibraryRecordResponse> page(IpLibraryDTOs.IpLibraryQueryRequest request) {
        IpLibraryDTOs.IpLibraryQueryRequest query = request == null ? new IpLibraryDTOs.IpLibraryQueryRequest() : request;
        String ipType = normalizeIpType(query.getIpType(), query.getIpAddress());
        String ipNumber = StringUtils.hasText(query.getIpAddress()) ? ipToNumber(query.getIpAddress(), ipType) : null;
        List<IpLibraryEntities.IpLibrarySplitModelDO> shards = routeShards(ipType, ipNumber);
        if (shards.isEmpty()) {
            return PageResult.of(0, query.safePageNo(), query.safePageSize(), List.of());
        }

        long total = 0;
        long remainingOffset = (long) (query.safePageNo() - 1) * query.safePageSize();
        int remainingLimit = query.safePageSize();
        List<IpLibraryDTOs.IpLibraryRecordResponse> records = new ArrayList<>();
        for (IpLibraryEntities.IpLibrarySplitModelDO shard : shards) {
            String tableName = checkedTableName(shard.getTableName());
            String version = requiredVersion(shard);
            long shardTotal = dataMapper.countRows(tableName, version, ipNumber);
            total += shardTotal;
            if (shardTotal <= 0 || remainingLimit <= 0) {
                continue;
            }
            if (remainingOffset >= shardTotal) {
                remainingOffset -= shardTotal;
                continue;
            }
            List<IpLibraryDTOs.IpLibraryRecordResponse> shardRows = dataMapper.selectPageRows(tableName, version, ipNumber,
                                remainingOffset,
                                remainingLimit)
                        .stream()
                        .map(row -> toResponse(row, shard.getIpType()))
                        .toList();
            records.addAll(shardRows);
            remainingLimit -= shardRows.size();
            remainingOffset = 0;
        }

        return PageResult.of(total, query.safePageNo(), query.safePageSize(), records);
    }

    /**
     * 查询单个 IP 命中的归属区间。
     *
     * <p>管理端与 OpenAPI 共用 {@link IpLookupCacheReader}。缓存命中时直接读取同一个 JSON String；
     * 缓存未命中或路由版本变化时，由公共组件从 SLAVE 查询一次并回写。管理端仅负责校验显式
     * {@code ipType} 与地址一致，并把公共详情转换为后台响应。</p>
     */
    public IpLibraryDTOs.IpLibraryRecordResponse lookup(IpLibraryDTOs.IpLibraryLookupRequest request) {
        String ipType = normalizeIpType(request.getIpType(), request.getIpAddress());
        // 先保留管理端原有的显式类型校验，避免 IPV4 请求携带 IPv6 地址时绕过参数约束。
        ipToNumber(request.getIpAddress(), ipType);
        IpLookupCacheEntry entry = ipLookupCacheReader.lookupEntry(request.getIpAddress());
        IpLookupResult result = entry.result();
        if (result == null || !Boolean.TRUE.equals(result.matched())) {
            return null;
        }
        if (!ipType.equals(result.ipType())) {
            throw badRequest("IP 类型与 IP 地址不匹配");
        }
        return toResponse(entry.detail());
    }

    /**
     * 将 IP 地址转换为无符号数值字符串。
     */
    public String ipToNumber(String ipAddress, String expectedIpType) {
        String normalized = trimToNull(ipAddress);
        if (normalized == null) {
            throw badRequest("ipAddress is required");
        }
        try {
            String ipType = normalizeIpType(expectedIpType, normalized);
            validateIpLiteral(normalized, ipType);
            IpAddressNormalizer.NormalizedIp normalizedIp = IpAddressNormalizer.normalizeExact(normalized);
            String actualType = normalizedIp.ipv4() ? IPV4 : IPV6;
            if (!actualType.equals(ipType)) {
                throw badRequest("IP 类型与 IP 地址不匹配");
            }
            BigInteger number = new BigInteger(IpAddressNormalizer.toUnsignedNumber(normalizedIp));
            if (IPV4.equals(ipType) && number.compareTo(BigInteger.valueOf(IPV4_MAX)) > 0) {
                throw badRequest("IPv4 数值超出范围");
            }
            if (IPV6.equals(ipType) && number.compareTo(IPV6_MAX) > 0) {
                throw badRequest("IPv6 数值超出范围");
            }
            return number.toString();
        } catch (IllegalArgumentException ex) {
            throw badRequest("IP 地址格式不正确");
        }
    }

    /**
     * 计算 IP 地址区间覆盖的分片集合。
     */
    private List<IpLibraryEntities.IpLibrarySplitModelDO> routeShards(String ipType, String ipNumber) {
        List<IpLibraryEntities.IpLibrarySplitModelDO> shards = splitModelMapper.selectList(
                Wrappers.<IpLibraryEntities.IpLibrarySplitModelDO>lambdaQuery()
                        .eq(IpLibraryEntities.IpLibrarySplitModelDO::getActiveFlag, ACTIVE)
                        .eq(StringUtils.hasText(ipType), IpLibraryEntities.IpLibrarySplitModelDO::getIpType, ipType)
                        .orderByAsc(IpLibraryEntities.IpLibrarySplitModelDO::getIpType)
                        .orderByAsc(IpLibraryEntities.IpLibrarySplitModelDO::getShardNo));
        if (ipNumber == null) {
            return shards;
        }
        BigInteger current = new BigInteger(ipNumber);
        return shards.stream()
                .filter(shard -> between(current, shard.getRangeStart(), shard.getRangeEnd()))
                .toList();
    }

    /**
     * 构建 IP 地址区间查询条件。
     */
    private boolean between(BigInteger current, String start, String end) {
        return current.compareTo(new BigInteger(start)) >= 0 && current.compareTo(new BigInteger(end)) <= 0;
    }

    /**
     * 规范化并校验 IP 地址类型。
     */
    private String normalizeIpType(String ipType, String ipAddress) {
        String normalized = trimToNull(ipType);
        if (normalized != null) {
            String upper = normalized.toUpperCase(Locale.ROOT).replace("_", "");
            if (List.of("IPV4", "IPV6").contains(upper)) {
                return upper;
            }
            throw badRequest("ipType 只支持 IPV4 / IPV6");
        }
        if (StringUtils.hasText(ipAddress)) {
            return ipAddress.contains(":") ? IPV6 : IPV4;
        }
        return IPV4;
    }

    /**
     * 校验并返回允许访问的物理分片表名。
     */
    private String checkedTableName(String tableName) {
        if (!ALLOWED_TABLES.contains(tableName)) {
            throw badRequest("IP 库分片表未在白名单中");
        }
        return tableName;
    }

    /**
     * 获取分片记录的数据版本，缺失时拒绝继续处理。
     */
    private String requiredVersion(IpLibraryEntities.IpLibrarySplitModelDO shard) {
        return Optional.ofNullable(trimToNull(shard.getDataVersion()))
                .orElseThrow(() -> badRequest("IP 库分片缺少生效版本"));
    }

    /**
     * 将 IP 地址库持久化记录转换为管理端响应。
     */
    private IpLibraryDTOs.IpLibraryRecordResponse toResponse(IpLibraryEntities.IpLibraryDataRow row, String ipType) {
        IpLibraryDTOs.IpLibraryRecordResponse response = new IpLibraryDTOs.IpLibraryRecordResponse();
        response.setId(row.getId());
        response.setIpType(ipType);
        response.setIpNumberStart(row.getIpNumberStart());
        response.setIpNumberEnd(row.getIpNumberEnd());
        response.setIpAddressStart(numberToIp(row.getIpNumberStart(), ipType));
        response.setIpAddressEnd(numberToIp(row.getIpNumberEnd(), ipType));
        response.setCountryAlpha2(row.getCountryAlpha2());
        response.setCountryAlpha3(row.getCountryAlpha3());
        response.setCountryNumeric(row.getCountryNumeric());
        response.setCountryName(row.getCountryName());
        response.setStateProvince(row.getStateProvince());
        response.setCity(row.getCity());
        response.setDataVersion(row.getDataVersion());
        response.setCreateTime(row.getCreateTime());
        response.setCreateBy(row.getCreateBy());
        return response;
    }

    /**
     * 将公共缓存详情转换为管理端响应，并保留区间与审计字段。
     */
    private IpLibraryDTOs.IpLibraryRecordResponse toResponse(IpLookupDetail detail) {
        if (detail == null || detail.result() == null) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        IpLookupResult result = detail.result();
        IpLibraryDTOs.IpLibraryRecordResponse response = new IpLibraryDTOs.IpLibraryRecordResponse();
        response.setId(detail.id());
        response.setIpType(result.ipType());
        response.setIpNumberStart(detail.ipNumberStart());
        response.setIpNumberEnd(detail.ipNumberEnd());
        response.setIpAddressStart(numberToIp(detail.ipNumberStart(), result.ipType()));
        response.setIpAddressEnd(numberToIp(detail.ipNumberEnd(), result.ipType()));
        response.setCountryAlpha2(result.countryAlpha2());
        response.setCountryAlpha3(result.countryAlpha3());
        response.setCountryNumeric(result.countryNumeric());
        response.setCountryName(result.countryName());
        response.setStateProvince(result.stateProvince());
        response.setCity(result.city());
        response.setDataVersion(detail.dataVersion());
        response.setCreateTime(detail.createTime());
        response.setCreateBy(detail.createBy());
        return response;
    }

    /**
     * 将无符号整数转换为 IP 地址文本。
     */
    private String numberToIp(String value, String ipType) {
        BigInteger number = new BigInteger(value);
        int length = IPV4.equals(ipType) ? 4 : 16;
        byte[] raw = number.toByteArray();
        byte[] target = new byte[length];
        int copyLength = Math.min(raw.length, length);
        System.arraycopy(raw, raw.length - copyLength, target, length - copyLength, copyLength);
        try {
            return InetAddress.getByAddress(target).getHostAddress();
        } catch (UnknownHostException ex) {
            return value;
        }
    }

    /**
     * 校验 IP 地址文本与地址类型一致。
     */
    private void validateIpLiteral(String ipAddress, String ipType) {
        if (IPV4.equals(ipType)) {
            String[] segments = ipAddress.split("\\.", -1);
            if (segments.length != 4) {
                throw badRequest("IPv4 地址格式不正确");
            }
            for (String segment : segments) {
                if (!segment.matches("\\d{1,3}")) {
                    throw badRequest("IPv4 地址格式不正确");
                }
                int value = Integer.parseInt(segment);
                if (value < 0 || value > 255) {
                    throw badRequest("IPv4 地址格式不正确");
                }
            }
            return;
        }
        if (!ipAddress.contains(":") || !ipAddress.matches("[0-9a-fA-F:.]+")) {
            throw badRequest("IPv6 地址格式不正确");
        }
    }

    /**
     * 去除文本首尾空白并把空串转换为 null。
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 构建参数校验失败异常。
     */
    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), message);
    }
}
