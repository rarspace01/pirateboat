package boat.torrent;

import java.util.List;

public interface TorrentSearchEngine {

    List<Torrent> searchTorrents(String torrentName);

    String getBaseUrl();

}