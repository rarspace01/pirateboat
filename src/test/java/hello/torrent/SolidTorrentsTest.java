package hello.torrent;

import hello.utilities.HttpHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SolidTorrentsTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow(() -> new SolidTorrents(new HttpHelper()).searchTorrents("planet"));
    }
}