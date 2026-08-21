package com.scott.payment.component.db.reference.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.reference.entity.CardBinRangeDO;
import com.scott.payment.component.db.reference.entity.IpLibraryDataRow;
import com.scott.payment.component.db.reference.entity.IpLibraryShardDO;
import com.scott.payment.component.db.reference.mapper.CardBinLookupMapper;
import com.scott.payment.component.db.reference.mapper.IpLocationLookupMapper;
import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
 * @description : 基础数据公共只读检索实现，固定路由到从库并保护 IP 动态分表边界
 * @status : create
 */
@Service
public class ReferenceDataLookupServiceImpl implements ReferenceDataLookupService {

    private static final String IPV4 = "IPV4";
    private static final String IPV6 = "IPV6";
    private static final int MIN_BIN_LENGTH = 6;
    private static final int NORMALIZED_BIN_LENGTH = 11;
    private static final Set<String> IP_TABLE_ALLOWLIST = IntStream.rangeClosed(1, 8)
            .mapToObj(index -> String.format("%02d", index))
            .flatMap(suffix -> List.of("ip_library_v4_data_" + suffix, "ip_library_v6_data_" + suffix).stream())
            .collect(Collectors.toUnmodifiableSet());

    /** IP 分片及归属数据只读 Mapper，不允许为空。 */
    private final IpLocationLookupMapper ipLocationLookupMapper;

    /** 卡 BIN 最优匹配只读 Mapper，不允许为空。 */
    private final CardBinLookupMapper cardBinLookupMapper;

    /**
     * 创建基础数据公共检索服务。
     *
     * @param ipLocationLookupMapper IP 归属 Mapper
     * @param cardBinLookupMapper    卡 BIN Mapper
     */
    public ReferenceDataLookupServiceImpl(IpLocationLookupMapper ipLocationLookupMapper,
                                          CardBinLookupMapper cardBinLookupMapper) {
        this.ipLocationLookupMapper = ipLocationLookupMapper;
        this.cardBinLookupMapper = cardBinLookupMapper;
    }

    /**
     * 查询精确 IP 的归属信息；合法输入未命中时返回 matched=false。
     *
     * @param ipAddress IP 字面量
     * @return IP 归属查询结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public IpLookupResult lookupIp(String ipAddress) {
        IpAddressNormalizer.NormalizedIp normalizedIp = IpAddressNormalizer.normalizeExact(ipAddress);
        String ipType = normalizedIp.ipv4() ? IPV4 : IPV6;
        String ipNumber = toIpNumber(normalizedIp.ipValue());
        List<IpLibraryShardDO> shards = ipLocationLookupMapper.selectReadyShards(ipType, ipNumber);
        if (shards == null || shards.size() != 1) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        IpLibraryShardDO shard = shards.get(0);
        if (!isAllowedIpTable(ipType, shard.getTableName()) || !StringUtils.hasText(shard.getDataVersion())) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        IpLibraryDataRow row = ipLocationLookupMapper.selectLookupCandidate(
                shard.getTableName(), shard.getDataVersion(), ipNumber);
        if (row == null) {
            return IpLookupResult.miss(normalizedIp.ipValue(), ipType);
        }
        return new IpLookupResult(
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
    }

    /**
     * 查询 6 至 11 位纯数字卡 BIN 的当前有效归属信息；合法输入未命中时返回 matched=false。
     *
     * @param cardBin 商户提交的卡 BIN
     * @return 卡 BIN 归属查询结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public CardBinLookupResult lookupCardBin(String cardBin) {
        if (cardBin == null || !cardBin.matches("^[0-9]{6,11}$")) {
            throw new IllegalArgumentException("cardBin must be 6 to 11 digits");
        }
        long numericValue = Long.parseLong(cardBin + "0".repeat(NORMALIZED_BIN_LENGTH - cardBin.length()));
        CardBinRangeDO row = cardBinLookupMapper.selectBestMatch(numericValue, cardBin.length());
        if (row == null) {
            return CardBinLookupResult.miss(cardBin);
        }
        if (!isValidBinMatch(row, cardBin.length())) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        return new CardBinLookupResult(
                true,
                cardBin,
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
     * 校验数据库返回的 BIN 精度满足对外查询约束，防止异常配置暴露超过请求长度的匹配结果。
     *
     * @param row         数据库命中记录
     * @param inputLength 商户输入 BIN 长度
     * @return true 表示命中记录可以返回
     */
    private boolean isValidBinMatch(CardBinRangeDO row, int inputLength) {
        Integer binLength = row.getBinLength();
        return binLength != null
                && binLength >= MIN_BIN_LENGTH
                && binLength <= NORMALIZED_BIN_LENGTH
                && binLength <= inputLength;
    }

    /**
     * 将已验证的 IP 字面量转换为无符号数值字符串，用于 IPv4/IPv6 分片和区间比较。
     *
     * @param ipAddress 已规范化的 IP 地址
     * @return 无符号十进制数值字符串
     */
    private String toIpNumber(String ipAddress) {
        try {
            return new BigInteger(1, InetAddress.getByName(ipAddress).getAddress()).toString();
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("IP address is invalid", exception);
        }
    }
}
