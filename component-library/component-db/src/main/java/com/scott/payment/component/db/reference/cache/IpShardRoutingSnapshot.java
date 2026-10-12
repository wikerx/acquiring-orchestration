package com.scott.payment.component.db.reference.cache;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.reference.entity.IpLibraryShardDO;
import com.scott.payment.component.db.reference.mapper.IpLocationLookupMapper;
import com.scott.payment.component.db.reference.model.IpShardRoute;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpShardRoutingSnapshot
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : IP 分片路由的本地不可变快照，从 SLAVE 预加载少量路由元数据，使普通冷查询只访问一次动态数据表
 * @status : create
 */
@Service
public class IpShardRoutingSnapshot {

    private final IpLocationLookupMapper mapper;
    private final AtomicReference<State> state = new AtomicReference<>(new State("uninitialized", List.of()));

    public IpShardRoutingSnapshot(IpLocationLookupMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 从 SLAVE 刷新完整路由快照。
     *
     * @return 路由版本相对上一次是否发生变化
     */
    @DS(DataSourceName.SLAVE)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public synchronized boolean refresh() {
        List<IpLibraryShardDO> rows = mapper.selectAllReadyShards();
        List<Route> routes = rows == null ? List.of() : rows.stream()
                .map(this::toRoute)
                .sorted(Comparator.comparing(Route::ipType)
                        .thenComparing(Route::rangeStart)
                        .thenComparing(Route::id))
                .toList();
        validateNoOverlap(routes);
        String version = version(routes);
        State previous = state.getAndSet(new State(version, routes));
        return !version.equals(previous.version());
    }

    /**
     * 从当前内存快照定位唯一分片；首次预加载失败或尚未执行时同步从 SLAVE 加载一次。
     */
    public IpShardRoute resolve(String ipType, String ipNumber) {
        State current = state.get();
        if (current.routes().isEmpty()) {
            refresh();
            current = state.get();
        }
        BigInteger value = new BigInteger(ipNumber);
        List<Route> matches = current.routes().stream()
                .filter(route -> route.ipType().equals(ipType))
                .filter(route -> route.rangeStart().compareTo(value) <= 0
                        && route.rangeEnd().compareTo(value) >= 0)
                .toList();
        if (matches.size() != 1) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        Route match = matches.get(0);
        return new IpShardRoute(
                current.version(),
                match.shard().getTableName(),
                match.shard().getDataVersion()
        );
    }

    /** 返回当前路由版本，供精确 IP 缓存隔离数据版本。 */
    public String currentVersion() {
        return state.get().version();
    }

    private Route toRoute(IpLibraryShardDO row) {
        if (row == null || row.getId() == null || !StringUtils.hasText(row.getIpType())
                || !StringUtils.hasText(row.getRangeStart()) || !StringUtils.hasText(row.getRangeEnd())
                || !StringUtils.hasText(row.getTableName()) || !StringUtils.hasText(row.getDataVersion())) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        try {
            BigInteger start = new BigInteger(row.getRangeStart());
            BigInteger end = new BigInteger(row.getRangeEnd());
            if (start.signum() < 0 || start.compareTo(end) > 0) {
                throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
            }
            return new Route(row.getId(), row.getIpType(), start, end, row);
        } catch (NumberFormatException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateNoOverlap(List<Route> routes) {
        Route previous = null;
        for (Route route : routes) {
            if (previous != null && previous.ipType().equals(route.ipType())
                    && previous.rangeEnd().compareTo(route.rangeStart()) >= 0) {
                throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR);
            }
            previous = route;
        }
    }

    private String version(List<Route> routes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Route route : routes) {
                String value = route.id() + "|" + route.ipType() + "|" + route.rangeStart()
                        + "|" + route.rangeEnd() + "|" + route.shard().getTableName()
                        + "|" + route.shard().getDataVersion() + '\n';
                digest.update(value.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record State(String version, List<Route> routes) {
    }

    private record Route(Long id,
                         String ipType,
                         BigInteger rangeStart,
                         BigInteger rangeEnd,
                         IpLibraryShardDO shard) {
    }
}
