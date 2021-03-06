package boat.torrent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import boat.utilities.HttpHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class YTS extends HttpUser implements TorrentSearchEngine {

    YTS(HttpHelper httpHelper) {
        super(httpHelper);
    }

    @Override
    public List<Torrent> searchTorrents(String searchName) {

        CopyOnWriteArrayList<Torrent> torrentList = new CopyOnWriteArrayList<>();

        String resultString = null;
        resultString = httpHelper.getPage(buildSearchUrl(searchName));

        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName));
        torrentList.sort(TorrentHelper.torrentSorter);
        return torrentList;
    }

    private String buildSearchUrl(String searchName) {
        return String.format(getBaseUrl() + "/api/v2/list_movies.json?limit=50&query_term=%s&sort_by=seeds",
            URLEncoder.encode(searchName, StandardCharsets.UTF_8));
    }

    @Override
    public String getBaseUrl() {
        return "https://yts.mx";
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {
        ArrayList<Torrent> torrentList = new ArrayList<>();

        JsonElement jsonRoot = JsonParser.parseString(pageContent);
        JsonElement data = jsonRoot.getAsJsonObject().get("data");
        if (data == null) {
            return torrentList;
        }
        JsonElement results = data.getAsJsonObject().get("movies");
        if (results == null) {
            return torrentList;
        }
        JsonArray jsonArray = results.getAsJsonArray();
        jsonArray.forEach(jsonTorrentElement -> {
            Torrent tempTorrent = new Torrent(toString());
            final JsonObject jsonTorrent = jsonTorrentElement.getAsJsonObject();
            tempTorrent.name = jsonTorrent.get("title").getAsString() + " " + jsonTorrent.get("year").getAsInt();
            JsonObject bestTorrentSource = retrieveBestTorrent(jsonTorrent.get("torrents").getAsJsonArray());
            tempTorrent.isVerified = true;
            tempTorrent.magnetUri = TorrentHelper
                .buildMagnetUriFromHash(bestTorrentSource.get("hash").getAsString().toLowerCase(), tempTorrent.name);
            tempTorrent.seeder = bestTorrentSource.get("seeds").getAsInt();
            tempTorrent.leecher = bestTorrentSource.get("peers").getAsInt();
            tempTorrent.size = bestTorrentSource.get("size").getAsString();
            tempTorrent.lsize = bestTorrentSource.get("size_bytes").getAsLong() / 1024.0f / 1024.0f;
            tempTorrent.date = new Date(bestTorrentSource.get("date_uploaded_unix").getAsLong() * 1000);

            TorrentHelper.evaluateRating(tempTorrent, searchName);
            if (TorrentHelper.isValidTorrent(tempTorrent)) {
                torrentList.add(tempTorrent);
            }
        });

        return torrentList;
    }

    private JsonObject retrieveBestTorrent(JsonArray torrentElements) {
        AtomicReference<JsonObject> bestTorrent = new AtomicReference<>();
        torrentElements.forEach(torrentElement -> {
            if (bestTorrent.get() == null || bestTorrent.get().get("size_bytes").getAsLong() < torrentElement
                .getAsJsonObject().get("size_bytes").getAsLong()) {
                bestTorrent.set(torrentElement.getAsJsonObject());
            }
        });
        return bestTorrent.get();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

}
