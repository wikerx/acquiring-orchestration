package com.scott.payment.component.db.reference.cache;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.reference.entity.IpLibraryShardDO;
import com.scott.payment.component.db.reference.mapper.IpLocationLookupMapper;
import com.scott.payment.component.db.reference.model.IpShardRoute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证 IP 分片路由快照的区间选择、重叠保护和版本变化判定。 */
class IpShardRoutingSnapshotTests {

    @Test
    void shouldResolveSingleShardFromImmutableReplicaSnapshot() {
        IpLocationLookupMapper mapper = mock(IpLocationLookupMapper.class);
        IpLibraryShardDO first = shard(1L, "0", "99", "v1", "ip_library_v4_data_01");
        IpLibraryShardDO second = shard(2L, "100", "199", "v1", "ip_library_v4_data_02");
        when(mapper.selectAllReadyShards()).thenReturn(List.of(second, first));
        IpShardRoutingSnapshot snapshot = new IpShardRoutingSnapshot(mapper);

        assertThat(snapshot.refresh()).isTrue();
        IpShardRoute route = snapshot.resolve("IPV4", "150");
        assertThat(route.tableName()).isEqualTo("ip_library_v4_data_02");
        assertThat(route.dataVersion()).isEqualTo("v1");
        assertThat(route.routingVersion()).isEqualTo(snapshot.currentVersion());
        assertThat(snapshot.currentVersion()).isNotEqualTo("uninitialized");
    }

    @Test
    void shouldRejectOverlappingRangesInsteadOfChoosingArbitrarily() {
        IpLocationLookupMapper mapper = mock(IpLocationLookupMapper.class);
        when(mapper.selectAllReadyShards()).thenReturn(List.of(
                shard(1L, "0", "100", "v1", "ip_library_v4_data_01"),
                shard(2L, "100", "200", "v1", "ip_library_v4_data_02")
        ));
        IpShardRoutingSnapshot snapshot = new IpShardRoutingSnapshot(mapper);

        assertThatThrownBy(snapshot::refresh).isInstanceOf(ServiceException.class);
    }

    @Test
    void shouldOnlyReportVersionChangeWhenReplicaRoutingContentChanges() {
        IpLocationLookupMapper mapper = mock(IpLocationLookupMapper.class);
        IpLibraryShardDO versionOne = shard(1L, "0", "99", "v1", "ip_library_v4_data_01");
        IpLibraryShardDO versionTwo = shard(1L, "0", "99", "v2", "ip_library_v4_data_01");
        when(mapper.selectAllReadyShards())
                .thenReturn(List.of(versionOne), List.of(versionOne), List.of(versionTwo));
        IpShardRoutingSnapshot snapshot = new IpShardRoutingSnapshot(mapper);

        assertThat(snapshot.refresh()).isTrue();
        assertThat(snapshot.refresh()).isFalse();
        assertThat(snapshot.refresh()).isTrue();
    }

    private IpLibraryShardDO shard(Long id,
                                   String start,
                                   String end,
                                   String version,
                                   String tableName) {
        IpLibraryShardDO shard = new IpLibraryShardDO();
        shard.setId(id);
        shard.setIpType("IPV4");
        shard.setRangeStart(start);
        shard.setRangeEnd(end);
        shard.setTableName(tableName);
        shard.setDataVersion(version);
        shard.setActiveFlag(1);
        shard.setLoadStatus("READY");
        return shard;
    }
}
