package com.scott.payment.admin.application.channel;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.scott.payment.admin.dto.channel.ChannelDTOs.*;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelInfoDO;
import com.scott.payment.admin.mapper.ChannelInfoMapper;
import com.scott.payment.admin.service.AdminChannelService;
import com.scott.payment.component.core.auth.*;
import com.scott.payment.component.web.upload.*;
import org.junit.jupiter.api.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminChannelImageApplicationServiceTest {
    private final AdminChannelService channels = mock(AdminChannelService.class);
    private final ChannelInfoMapper mapper = mock(ChannelInfoMapper.class);
    private final SafeImageProcessor processor = mock(SafeImageProcessor.class);
    private final ManagedImageStore store = mock(ManagedImageStore.class);
    private final AdminChannelImageApplicationService service = new AdminChannelImageApplicationService(channels, mapper, processor, store);
    private final MultipartFile file = mock(MultipartFile.class);
    @BeforeEach void setup() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), ""); assistant.setCurrentNamespace(getClass().getName()); TableInfoHelper.initTableInfo(assistant, ChannelInfoDO.class);
        var a = new InternalAuthAccount(); a.setUserId(1L); a.setAccountId(2L); a.setAppCode("ADMIN"); a.setPermissions(List.of("*:*:*")); InternalAuthContextHolder.set(a);
    }
    @AfterEach void clear() { InternalAuthContextHolder.clear(); }
    private ChannelInfoDO entity() { var e = new ChannelInfoDO(); e.setId(10L); e.setDeleted(0L); e.setLogoObjectKey("old-image"); return e; }
    @Test void permissionRejectionComesBeforeAnyFileOrDatabaseAccess() {
        InternalAuthContextHolder.get().setPermissions(List.of()); assertThatThrownBy(() -> service.save(10L, new ChannelInfoSaveRequest(), file, false)).hasMessageContaining("无权"); verifyNoInteractions(channels, mapper, processor, store, file);
    }
    @Test void nonexistentChannelIsRejectedBeforeScan() {
        assertThatThrownBy(() -> service.save(10L, new ChannelInfoSaveRequest(), file, false)).hasMessageContaining("不可访问"); verifyNoInteractions(processor, store, file, channels);
    }
    @Test void scanFailurePreservesOldLogoAndChannelFacts() {
        when(mapper.selectOne(any())).thenReturn(entity()); when(processor.process(file, false)).thenThrow(new IllegalStateException("scan failed"));
        assertThatThrownBy(() -> service.save(10L, new ChannelInfoSaveRequest(), file, false)).hasMessageContaining("scan failed"); verifyNoInteractions(store, channels); verify(mapper, never()).update(any(), any());
    }
    @Test void newChannelAndLogoAreSavedThroughOwnerService() {
        var request = new ChannelInfoSaveRequest(); var saved = new ChannelInfoResponse(); saved.setId(10L);
        when(processor.process(file, false)).thenReturn(new byte[]{1}); when(channels.createChannel(request)).thenReturn(saved);
        when(store.write(eq("channel/10"), any())).thenReturn("new-image"); when(mapper.update(isNull(), any())).thenReturn(1); when(channels.getChannel(10L)).thenReturn(saved);
        assertThat(service.save(null, request, file, false)).isSameAs(saved);
        var order = inOrder(processor, channels, store, mapper); order.verify(processor).process(file, false); order.verify(channels).createChannel(request); order.verify(store).write(eq("channel/10"), any()); order.verify(mapper).update(isNull(), any());
    }
    @Test void removalDoesNotRequireScanAndCleansOnlyAfterCommit() {
        when(mapper.selectOne(any())).thenReturn(entity()); var saved = new ChannelInfoResponse(); saved.setId(10L); when(channels.updateChannel(eq(10L), any())).thenReturn(saved); when(mapper.update(isNull(), any())).thenReturn(1);
        service.save(10L, new ChannelInfoSaveRequest(), null, true); verifyNoInteractions(processor); verify(store).deleteAfterCommit("old-image");
    }
    @Test void cannotUploadAndRemoveInSameRequest() {
        when(mapper.selectOne(any())).thenReturn(entity()); assertThatThrownBy(() -> service.save(10L, new ChannelInfoSaveRequest(), file, true)).hasMessageContaining("同时"); verifyNoInteractions(processor, store, channels, file);
    }
}
