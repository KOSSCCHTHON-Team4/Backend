package team4.emotionmap.memory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class MemoryAccessServiceTest {
    private final MemoryRepository memories = mock(MemoryRepository.class);
    private final MemoryReadAccess deliveries = mock(MemoryReadAccess.class);
    private final MemoryAccessService access = new MemoryAccessService(memories, deliveries);
    private final UUID owner = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();

    @Test
    void formerRecipientGetsGoneAfterOriginalDeletion() {
        Memory memory = unavailableMemory(ContentStatus.DELETED);
        when(deliveries.hasDelivery(viewer, memory.getId())).thenReturn(true);
        assertStatus(viewer, memory, HttpStatus.GONE);
    }

    @Test
    void deletedOriginalDoesNotRevealItsExistenceToAnOutsider() {
        Memory memory = unavailableMemory(ContentStatus.DELETED);
        assertStatus(viewer, memory, HttpStatus.NOT_FOUND);
    }

    @Test
    void ownerGetsGoneWhenTheirMemoryIsHidden() {
        Memory memory = unavailableMemory(ContentStatus.HIDDEN);
        assertStatus(owner, memory, HttpStatus.GONE);
    }

    private Memory unavailableMemory(ContentStatus status) {
        Memory memory = Memory.builder().id(UUID.randomUUID()).ownerId(owner)
                .distributionType(DistributionType.LETTER).contentStatus(status)
                .moderationStatus(ModerationStatus.APPROVED).build();
        when(memories.findById(memory.getId())).thenReturn(Optional.of(memory));
        return memory;
    }

    private void assertStatus(UUID actor, Memory memory, HttpStatus expected) {
        assertThatThrownBy(() -> access.requireReadable(actor, memory.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode()).isEqualTo(expected));
    }
}
