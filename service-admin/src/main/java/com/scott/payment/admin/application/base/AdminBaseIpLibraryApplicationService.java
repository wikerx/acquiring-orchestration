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

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseIpLibraryApplicationService
 * @date : 2026-07-05 00:34
 * @email : scott_x@163.com
 * @description : AdminBaseIpLibraryApplicationService 应用服务，用于编排接口请求、权限上下文、领域服务和外部依赖，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminBaseIpLibraryApplicationService {

    /**
     * IPV4 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String IPV4 = "IPV4";
    /**
     * IPV6 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String IPV6 = "IPV6";
    /**
     * ACTIVE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int ACTIVE = 1;
    /**
     * IPV4 MAX 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final long IPV4_MAX = 4_294_967_295L;
    /**
     * IPV6 MAX 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final BigInteger IPV6_MAX = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
    private static final Set<String> ALLOWED_TABLES = IntStream.rangeClosed(1, 8)
            .mapToObj(index -> String.format("%02d", index))
            .flatMap(suffix -> List.of("ip_library_v4_data_" + suffix, "ip_library_v6_data_" + suffix).stream())
            .collect(Collectors.toUnmodifiableSet());

    /**
     * split Model Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final IpLibrarySplitModelMapper splitModelMapper;
    /**
     * data Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
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

    /**
     * 完成 route Shards 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param ipType ip Type 输入值，含义由调用方法名称和所属业务对象限定
     * @param ipNumber ip Number 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 between 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param current current 输入值，含义由调用方法名称和所属业务对象限定
     * @param start start 输入值，含义由调用方法名称和所属业务对象限定
     * @param end end 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private boolean between(BigInteger current, String start, String end) {
        return current.compareTo(new BigInteger(start)) >= 0 && current.compareTo(new BigInteger(end)) <= 0;
    }

    /**
     * 标准化 normalize Ip Type 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param ipType ip Type 输入值，含义由调用方法名称和所属业务对象限定
     * @param ipAddress ip Address 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
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
     * 完成 checked Table Name 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param tableName table Name 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String checkedTableName(String tableName) {
        if (!ALLOWED_TABLES.contains(tableName)) {
            throw badRequest("IP 库分片表未在白名单中");
        }
        return tableName;
    }

    /**
     * 强制校验 required Version 必填值，缺失时中断当前业务流程。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param shard shard 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String requiredVersion(IpLibraryEntities.IpLibrarySplitModelDO shard) {
        return Optional.ofNullable(trimToNull(shard.getDataVersion()))
                .orElseThrow(() -> badRequest("IP 库分片缺少生效版本"));
    }

    /**
     * 转换生成 to Response 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @param ipType ip Type 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 完成 number To Ip 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param ipType ip Type 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 校验 validate Ip Literal 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param ipAddress ip Address 输入值，含义由调用方法名称和所属业务对象限定
     * @param ipType ip Type 输入值，含义由调用方法名称和所属业务对象限定
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
     * 完成 trim To Null 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 完成 bad Request 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     * @return 当前方法计算或转换后的业务结果
     */
    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), message);
    }
}
