package com.scott.payment.component.db.reference.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.reference.cache.IpShardRoutingSnapshot;
import com.scott.payment.component.db.reference.entity.CardBinRangeDO;
import com.scott.payment.component.db.reference.entity.IpLibraryDataRow;
import com.scott.payment.component.db.reference.mapper.CardBinLookupMapper;
import com.scott.payment.component.db.reference.mapper.IpLocationLookupMapper;
import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupDetail;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupSnapshot;
import com.scott.payment.component.db.reference.model.IpShardRoute;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReferenceDataLookupServiceImpl
 * @date : 2026-08-11 15:35
 * @email : scott_x@163.com
 * @description : 基础数据公共只读检索；IP 与 BIN 查询均走从库，并保护 IP 动态分表边界。
 * @status : create
 */
@Service
public class ReferenceDataLookupServiceImpl implements ReferenceDataLookupService {

    /**
     * {@code IPV4}常量，统一 {@code ReferenceDataLookupServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String IPV4 = "IPV4";
    /**
     * {@code IPV6}常量，统一 {@code ReferenceDataLookupServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String IPV6 = "IPV6";
    /**
     * {@code MIN_BIN_LENGTH}常量，统一 {@code ReferenceDataLookupServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int MIN_BIN_LENGTH = 6;
    /**
     * {@code NORMALIZED_BIN_LENGTH}常量，统一 {@code ReferenceDataLookupServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int NORMALIZED_BIN_LENGTH = 11;
    private static final Set<String> IP_TABLE_ALLOWLIST = IntStream.rangeClosed(1, 8)
            .mapToObj(index -> String.format("%02d", index))
            .flatMap(suffix -> List.of("ip_library_v4_data_" + suffix, "ip_library_v6_data_" + suffix).stream())
            .collect(Collectors.toUnmodifiableSet());

    /** IP 分片及归属数据只读 Mapper，不允许为空。 */
    private final IpLocationLookupMapper ipLocationLookupMapper;

    /** 卡 BIN 最优匹配只读 Mapper，不允许为空。 */
    private final CardBinLookupMapper cardBinLookupMapper;

    /** IP 分片本地不可变路由快照，避免每个冷 IP 先查询一次路由表。 */
    private final IpShardRoutingSnapshot ipShardRoutingSnapshot;

    /**
     * 创建基础数据公共检索服务。
     *
     * @param ipLocationLookupMapper IP 归属 Mapper
     * @param cardBinLookupMapper    卡 BIN Mapper
     * @param ipShardRoutingSnapshot IP 分片路由快照
     */
    public ReferenceDataLookupServiceImpl(IpLocationLookupMapper ipLocationLookupMapper,
                                          CardBinLookupMapper cardBinLookupMapper,
                                          IpShardRoutingSnapshot ipShardRoutingSnapshot) {
        this.ipLocationLookupMapper = ipLocationLookupMapper;
        this.cardBinLookupMapper = cardBinLookupMapper;
        this.ipShardRoutingSnapshot = ipShardRoutingSnapshot;
    }

    /**
     * 查询精确 IP 的归属信息；合法输入未命中时返回 matched=false。
     *
     * @param ipAddress IP 字面量
     * @return IP 归属查询结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public IpLookupResult lookupIp(String ipAddress) {
        return lookupIpSnapshot(ipAddress).result();
    }

    /**
     * 在同一个不可变路由上下文中完成分片选择和数据查询，防止刷新并发导致缓存版本与查询数据错配。
     *
     * @param ipAddress IP 字面量
     * @return 实际路由版本和查询结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public IpLookupSnapshot lookupIpSnapshot(String ipAddress) {
        IpAddressNormalizer.NormalizedIp normalizedIp = IpAddressNormalizer.normalizeExact(ipAddress);
        String ipType = normalizedIp.ipv4() ? IPV4 : IPV6;
        String ipNumber = IpAddressNormalizer.toUnsignedNumber(normalizedIp);
        // 路由必须唯一：缺失或重叠是库配置错误，不能伪装成普通未命中。
        IpShardRoute route = ipShardRoutingSnapshot.resolve(ipType, ipNumber);
        if (!isAllowedIpTable(ipType, route.tableName()) || !StringUtils.hasText(route.dataVersion())) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        // 表名由数据库配置提供，只允许固定表族；数据版本与路由记录保持一致。
        IpLibraryDataRow row = ipLocationLookupMapper.selectLookupCandidate(
                route.tableName(), route.dataVersion(), ipNumber);
        if (row == null) {
            return new IpLookupSnapshot(route.routingVersion(), IpLookupDetail.miss(
                    IpLookupResult.miss(normalizedIp.ipValue(), ipType)));
        }
        IpLookupResult result = new IpLookupResult(
                true,
                normalizedIp.ipValue(),
                ipType,
                row.getCountryAlpha2(),
                row.getCountryAlpha3(),
                row.getCountryNumeric(),
                row.getCountryName(),
                row.getStateProvince(),
                row.getCity()
        );
        return new IpLookupSnapshot(route.routingVersion(), new IpLookupDetail(
                result,
                row.getId(),
                row.getIpNumberStart(),
                row.getIpNumberEnd(),
                row.getDataVersion(),
                row.getCreateTime(),
                row.getCreateBy()
        ));
    }

    /**
     * 查询 6 至 11 位纯数字卡 BIN 的启用归属信息；独立只读事务确保支付主事务内仍路由从库。
     *
     * <p>输入先右补零为十一位数值，再通过一条区间 SQL 查询覆盖记录。重叠区间固定选择
     * 主键最大的增量记录；数据库未命中直接返回 {@code matched=false}，由支付或 OpenAPI
     * 上层执行卡品牌规则兜底。</p>
     *
     * @param cardBin 商户提交的卡 BIN
     * @return 卡 BIN 归属查询结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public CardBinLookupResult lookupCardBin(String cardBin) {
        if (cardBin == null || !cardBin.matches("^[0-9]{6,11}$")) {
            throw new IllegalArgumentException("cardBin must be 6 to 11 digits");
        }
        // 数据库起止区间统一使用十一位数值；短输入右补零后只执行一次覆盖查询。
        String normalizedCardBin = cardBin + "0".repeat(NORMALIZED_BIN_LENGTH - cardBin.length());
        long numericValue = Long.parseLong(normalizedCardBin);
        CardBinRangeDO row = cardBinLookupMapper.selectBestMatch(numericValue);
        if (row == null) {
            return CardBinLookupResult.miss(normalizedCardBin);
        }
        if (!isValidBinMatch(row, numericValue)) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        return new CardBinLookupResult(
                true,
                normalizedCardBin,
                row.getBinLength(),
                row.getCardBrand(),
                row.getCardSubBrand(),
                row.getCardType(),
                row.getCardLevel(),
                row.getIssuerCountryName(),
                row.getIssuerCountryAlpha2(),
                row.getIssuerCountryAlpha3(),
                row.getIssuerCountryNumeric(),
                row.getIssuerBank()
        );
    }

    /**
     * 校验动态物理表已在固定白名单内，且表族与目标 IP 类型一致。
     *
     * @param ipType    目标 IP 类型
     * @param tableName 分片配置中的物理表名
     * @return true 表示允许执行动态表查询
     */
    private boolean isAllowedIpTable(String ipType, String tableName) {
        if (!IP_TABLE_ALLOWLIST.contains(tableName)) {
            return false;
        }
        if (IPV4.equals(ipType)) {
            return tableName.startsWith("ip_library_v4_data_");
        }
        if (IPV6.equals(ipType)) {
            return tableName.startsWith("ip_library_v6_data_");
        }
        return false;
    }

    /**
     * 校验数据库返回记录确实覆盖查询值，防止异常映射或脏数据被当作有效归属返回。
     *
     * @param row 数据库命中记录
     * @param numericValue 规范化后的十一位查询数值
     * @return true 表示命中记录可以返回
     */
    private boolean isValidBinMatch(CardBinRangeDO row, long numericValue) {
        Integer binLength = row.getBinLength();
        return row.getId() != null
                && row.getCardBinStart() != null
                && row.getCardBinEnd() != null
                && row.getCardBinStart() <= numericValue
                && row.getCardBinEnd() >= numericValue
                && binLength != null
                && binLength >= MIN_BIN_LENGTH
                && binLength <= NORMALIZED_BIN_LENGTH;
    }

}
