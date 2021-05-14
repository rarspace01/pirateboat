package boat.torrent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import boat.utilities.HttpHelper;

@Service
public class TorrentSearchEngineService {

    private final List<TorrentSearchEngine> activeSearchEngines = new ArrayList<>();
    private final List<TorrentSearchEngine> allSearchEngines;

    @Autowired
    public TorrentSearchEngineService(HttpHelper httpHelper) {
        this.allSearchEngines = Arrays.asList(
            new PirateBay(httpHelper),
            new NyaaSi(httpHelper),
            new SolidTorrents(httpHelper),
            new LeetxTo(httpHelper),
            new YTS(httpHelper),
            new RARBG(httpHelper),
            new Torrentz(httpHelper),
            new MagnetDL(httpHelper),
            new Zooqle(httpHelper)
        );
        this.activeSearchEngines.addAll(allSearchEngines);
    }

    public void refreshTorrentSearchEngines() {
        final List<TorrentSearchEngine> tempActiveSearchEngines = new ArrayList<>();
        allSearchEngines.parallelStream().forEach(torrentSearchEngine -> {
            final List<Torrent> torrents = torrentSearchEngine.searchTorrents("blue");
            if (!torrents.isEmpty()) {
                tempActiveSearchEngines.add(torrentSearchEngine);
            }
        });
        activeSearchEngines.clear();
        activeSearchEngines.addAll(tempActiveSearchEngines);
    }

    public List<TorrentSearchEngine> getActiveSearchEngines() {
        return activeSearchEngines;
    }

    public List<TorrentSearchEngine> getInActiveSearchEngines() {
        return allSearchEngines.stream()
                .filter(torrentSearchEngine -> !activeSearchEngines.contains(torrentSearchEngine))
                .collect(Collectors.toList());
    }
}
