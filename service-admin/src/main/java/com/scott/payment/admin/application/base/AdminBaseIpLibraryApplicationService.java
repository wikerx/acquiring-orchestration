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
import com.scott.payment.component.db.constant.DataSourceName;
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
 * @description : Admin Base IP Library Application Service 应用服务，位于 运营后台服务，编排控制器入参、登录或商户上下文、领域服务调用和响应模型组装。
 * @status : create
 */
public class AdminBaseIpLibraryApplicationService {

    /**
     * IPV 4，用于保存 Admin Base IP Library Application Service 中与 ipv4 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String IPV4 = "IPV4";
    /**
     * IPV 6，用于保存 Admin Base IP Library Application Service 中与 ipv6 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String IPV6 = "IPV6";
    /**
     * ACTIVE，用于保存 Admin Base IP Library Application Service 中与 active 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int ACTIVE = 1;
    /**
     * IPV 4 MAX，用于保存 Admin Base IP Library Application Service 中与 ipv4max 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final long IPV4_MAX = 4_294_967_295L;
    /**
     * IPV 6 MAX，用于保存 Admin Base IP Library Application Service 中与 ipv6max 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final BigInteger IPV6_MAX = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
    private static final Set<String> ALLOWED_TABLES = IntStream.rangeClosed(1, 8)
            .mapToObj(index -> String.format("%02d", index))
            .flatMap(suffix -> List.of("ip_library_v4_data_" + suffix, "ip_library_v6_data_" + suffix).stream())
            .collect(Collectors.toUnmodifiableSet());

    /**
     * split Model Mapper 依赖，用于 Admin Base IP Library Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final IpLibrarySplitModelMapper splitModelMapper;
    /**
     * data Mapper 依赖，用于 Admin Base IP Library Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 规范化routeshards，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param ipType IP Type 输入值，参与 iptype 的查询、校验、转换、写入或日志摘要
     * @param ipNumber IP Number 输入值，参与 ipnumber 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 规范化between，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param current current 输入值，参与 当前 的查询、校验、转换、写入或日志摘要
     * @param start start 输入值，参与 start 的查询、校验、转换、写入或日志摘要
     * @param end end 输入值，参与 end 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private boolean between(BigInteger current, String start, String end) {
        return current.compareTo(new BigInteger(start)) >= 0 && current.compareTo(new BigInteger(end)) <= 0;
    }

    /**
     * 解析normalizeiptype，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param ipType IP Type 输入值，参与 iptype 的查询、校验、转换、写入或日志摘要
     * @param ipAddress IP Address 输入值，参与 ipaddress 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 校验checkedtablename输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param tableName table Name 输入值，参与 tablename 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String checkedTableName(String tableName) {
        if (!ALLOWED_TABLES.contains(tableName)) {
            throw badRequest("IP 库分片表未在白名单中");
        }
        return tableName;
    }

    /**
     * 校验requiredversion输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param shard shard 输入值，参与 shard 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String requiredVersion(IpLibraryEntities.IpLibrarySplitModelDO shard) {
        return Optional.ofNullable(trimToNull(shard.getDataVersion()))
                .orElseThrow(() -> badRequest("IP 库分片缺少生效版本"));
    }

    /**
     * 构造响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param ipType IP Type 输入值，参与 iptype 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 规范化numbertoip，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param ipType IP Type 输入值，参与 iptype 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 校验ipliteral输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param ipAddress IP Address 输入值，参与 ipaddress 的查询、校验、转换、写入或日志摘要
     * @param ipType IP Type 输入值，参与 iptype 的查询、校验、转换、写入或日志摘要
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
     * 规范化trimtonull，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 整理bad请求，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), message);
    }
}
