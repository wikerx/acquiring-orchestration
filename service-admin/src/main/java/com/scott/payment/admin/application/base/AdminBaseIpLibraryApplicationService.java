package com.scott.payment.admin.application.base;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.base.IpLibraryDTOs;
import com.scott.payment.admin.entity.base.IpLibraryEntities;
import com.scott.payment.admin.mapper.IpLibraryDataMapper;
import com.scott.payment.admin.mapper.IpLibrarySplitModelMapper;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseIpLibraryApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 基础数据Admin Base Ip Library Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminBaseIpLibraryApplicationService {

    /**
     * 基础数据固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String IPV4 = "IPV4";
    /**
     * 基础数据固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String IPV6 = "IPV6";
    /**
     * 基础数据固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final int ACTIVE = 1;
    /**
     * 基础数据固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final long IPV4_MAX = 4_294_967_295L;
    private static final BigInteger IPV6_MAX = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
    private static final Set<String> ALLOWED_TABLES = IntStream.rangeClosed(1, 8)
            .mapToObj(index -> String.format("%02d", index))
            .flatMap(suffix -> List.of("ip_library_v4_data_" + suffix, "ip_library_v6_data_" + suffix).stream())
            .collect(Collectors.toUnmodifiableSet());

    /**
     * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final IpLibrarySplitModelMapper splitModelMapper;
    /**
     * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final IpLibraryDataMapper dataMapper;

    /**
     * 创建全球 IP 库应用服务。
     */
    public AdminBaseIpLibraryApplicationService(IpLibrarySplitModelMapper splitModelMapper,
                                                IpLibraryDataMapper dataMapper) {
        this.splitModelMapper = splitModelMapper;
        this.dataMapper = dataMapper;
    }

    /**
     * 分页查询 IP 库区间。
     */
    /**
     * 查询基础数据列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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
     */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public IpLibraryDTOs.IpLibraryRecordResponse lookup(IpLibraryDTOs.IpLibraryLookupRequest request) {
        String ipType = normalizeIpType(request.getIpType(), request.getIpAddress());
        String ipNumber = ipToNumber(request.getIpAddress(), ipType);
        IpLibraryEntities.IpLibrarySplitModelDO shard = routeShards(ipType, ipNumber).stream()
                .findFirst()
                .orElseThrow(() -> badRequest("未找到当前 IP 对应的分片配置"));
        IpLibraryEntities.IpLibraryDataRow row = dataMapper.selectLookupCandidate(
                checkedTableName(shard.getTableName()),
                requiredVersion(shard),
                ipNumber);
        if (row == null || new BigInteger(row.getIpNumberEnd()).compareTo(new BigInteger(ipNumber)) < 0) {
            return null;
        }
        return toResponse(row, shard.getIpType());
    }

    /**
     * 将 IP 地址转换为无符号数值字符串。
     */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param ipAddress 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param expectedIpType 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public String ipToNumber(String ipAddress, String expectedIpType) {
        String normalized = trimToNull(ipAddress);
        if (normalized == null) {
            throw badRequest("ipAddress is required");
        }
        try {
            String ipType = normalizeIpType(expectedIpType, normalized);
            validateIpLiteral(normalized, ipType);
            InetAddress address = InetAddress.getByName(normalized);
            byte[] bytes = address.getAddress();
            String actualType = bytes.length == 4 ? IPV4 : IPV6;
            if (!actualType.equals(ipType)) {
                throw badRequest("IP 类型与 IP 地址不匹配");
            }
            BigInteger number = new BigInteger(1, bytes);
            if (IPV4.equals(ipType) && number.compareTo(BigInteger.valueOf(IPV4_MAX)) > 0) {
                throw badRequest("IPv4 数值超出范围");
            }
            if (IPV6.equals(ipType) && number.compareTo(IPV6_MAX) > 0) {
                throw badRequest("IPv6 数值超出范围");
            }
            return number.toString();
        } catch (UnknownHostException ex) {
            throw badRequest("IP 地址格式不正确");
        }
    }

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

    private boolean between(BigInteger current, String start, String end) {
        return current.compareTo(new BigInteger(start)) >= 0 && current.compareTo(new BigInteger(end)) <= 0;
    }

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

    private String checkedTableName(String tableName) {
        if (!ALLOWED_TABLES.contains(tableName)) {
            throw badRequest("IP 库分片表未在白名单中");
        }
        return tableName;
    }

    private String requiredVersion(IpLibraryEntities.IpLibrarySplitModelDO shard) {
        return Optional.ofNullable(trimToNull(shard.getDataVersion()))
                .orElseThrow(() -> badRequest("IP 库分片缺少生效版本"));
    }

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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), message);
    }
}
