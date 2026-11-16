package com.scott.payment.component.web.upload;

import com.scott.payment.component.core.model.ManagedImageReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedImageStoreTest {
    private final ImageObjectStorage backend = mock(ImageObjectStorage.class);
    private ManagedImageStore store;
    @BeforeEach @SuppressWarnings("unchecked") void setup() { var provider = mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(backend); store = new ManagedImageStore(provider); TransactionSynchronizationManager.initSynchronization(); }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }
    @Test void rollbackCleansNewObjectAndKeepsOldObject() {
        String key = store.write("avatar/1", new byte[]{1}); assertThat(ManagedImageReference.valid(key)).isTrue(); assertThat(ManagedImageReference.version(key)).hasSize(32);
        for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCompletion(1);
        verify(backend).delete(key);
    }
    @Test void oldObjectIsRemovedOnlyAfterCommit() {
        String key = store.write("channel/2", new byte[]{1}); store.deleteAfterCommit(key); verify(backend, never()).delete(any());
        for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCommit(); verify(backend).delete(key);
    }
    @Test void referenceTamperingAndWrongOwnershipAreRejected() {
        String key = store.write("avatar/1", new byte[]{1});
        assertThatThrownBy(() -> store.read(key, "avatar/2")).hasMessageContaining("不可访问");
        assertThatThrownBy(() -> store.read("https://external.test/image.png", "avatar/1")).hasMessageContaining("不可访问"); verify(backend, never()).get(any());
        when(backend.get(key)).thenReturn(new byte[]{2}); assertThatThrownBy(() -> store.read(key, "avatar/1")).hasMessageContaining("完整性");
    }
}
