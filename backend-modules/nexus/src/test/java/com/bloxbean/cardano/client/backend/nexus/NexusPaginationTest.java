package com.bloxbean.cardano.client.backend.nexus;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class NexusPaginationTest {
    private final List<Integer> ten = List.of(1,2,3,4,5,6,7,8,9,10);
    @Test void page1_size3() { assertThat(NexusPagination.subList(ten,3,1)).containsExactly(1,2,3); }
    @Test void page2_size3() { assertThat(NexusPagination.subList(ten,3,2)).containsExactly(4,5,6); }
    @Test void lastPartialPage() { assertThat(NexusPagination.subList(ten,3,4)).containsExactly(10); }
    @Test void pageBeyondRange_empty() { assertThat(NexusPagination.subList(ten,3,5)).isEmpty(); }
    @Test void pageLessThan1_empty() { assertThat(NexusPagination.subList(ten,3,0)).isEmpty(); }
    @Test void nullInput_empty() { assertThat(NexusPagination.subList(null,3,1)).isEmpty(); }
    @Test void countLessThan1_empty() {
        assertThat(NexusPagination.subList(ten,0,1)).isEmpty();
        assertThat(NexusPagination.subList(ten,-1,1)).isEmpty();
    }
    @Test void emptyInput_empty() { assertThat(NexusPagination.subList(List.of(),3,1)).isEmpty(); }
}
