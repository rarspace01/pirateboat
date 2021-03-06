package boat.info;

import java.util.List;

import boat.torrent.Torrent;
import boat.utilities.HttpHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class TheFilmDataBaseServiceTest {

    private TheFilmDataBaseService tfdbs;

    @BeforeEach
    public void beforeMethod() {
        this.tfdbs = new TheFilmDataBaseService(new HttpHelper());
    }

    @Test
    void search() {
        // Given
        // When
        List<MediaItem> mediaItems = tfdbs.search("Planet");
        // Then
        assertTrue(mediaItems.size() > 0);
    }

    @Test
    void searchEmpty() {
        // Given
        // When
        List<MediaItem> mediaItems = tfdbs.search("TrestTrest");
        // Then
        assertEquals(0, mediaItems.size());
    }

    @Test
    void determineMediaType() {
        // Given
        Torrent mockTorrent = new Torrent("Test");
        mockTorrent.name = "Big Buck Bunny (2008) [720p] [PLA]";
        // When
        MediaType mediaType = tfdbs.determineMediaType(mockTorrent);
        // Then
        assertEquals(MediaType.Movie, mediaType);

    }

    @Test
    void determineMediaTypeMore() {
        // Given
        Torrent mockTorrent = new Torrent("Test");
        mockTorrent.name = "Big.Buck.Bunny.2008.REMASTERED.1080p.BluRay.x264.DTS-FGT";
        // When
        MediaType mediaType = tfdbs.determineMediaType(mockTorrent);
        // Then
        assertEquals(MediaType.Movie, mediaType);

    }

}