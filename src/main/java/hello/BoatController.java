package hello;

import hello.info.TheFilmDataBaseService;
import hello.info.TorrentMetaService;
import hello.torrent.Premiumize;
import hello.torrent.Torrent;
import hello.torrent.TorrentHelper;
import hello.torrent.TorrentSearchEngine;
import hello.torrent.TorrentSearchEngineService;
import hello.utilities.HttpHelper;
import hello.utilities.PropertiesHelper;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public final class BoatController {
    private final String switchToProgress = "<a href=\"../debug\">Show Progress</a> ";
    private final TorrentSearchEngineService torrentSearchEngineService;
    private final HttpHelper httpHelper;
    private final TorrentMetaService torrentMetaService;
    private final TheFilmDataBaseService theFilmDataBaseService;

    @Autowired
    public BoatController(TorrentSearchEngineService torrentSearchEngineService,
                          HttpHelper httpHelper,
                          TorrentMetaService torrentMetaService,
                          TheFilmDataBaseService theFilmDataBaseService) {
        this.torrentSearchEngineService = torrentSearchEngineService;
        this.httpHelper = httpHelper;
        this.torrentMetaService = torrentMetaService;
        this.theFilmDataBaseService = theFilmDataBaseService;
    }

    @GetMapping({"/"})
    @NotNull
    public final String index() {
        return "Greetings from Spring Boot!";
    }

    @GetMapping({"/search"})
    @NotNull
    public final String search() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<body style=\"font-size: 2em;\">\n" +
                "\n" +
                "<h2>Here to serve you</h2>\n" +
                "\n" +
                "<form action=\"../boat\" target=\"_blank\" method=\"GET\">\n" +
                "  Title:<br>\n" +
                "  <input type=\"text\" name=\"q\" value=\"\" style=\"font-size: 2em; \">\n" +
                "  <br>\n" +
                "  <input type=\"submit\" value=\"Search\" style=\"font-size: 2em; \">\n" +
                "</form>\n" +
                "  <br>\n" +
                "  <br>\n" +
                "<form action=\"../boat/download/\" target=\"_blank\" method=\"POST\">\n" +
                "  Direct download URL (multiple seperate by comma):<br>\n" +
                "  <input type=\"text\" name=\"dd\" value=\"\" style=\"font-size: 2em; \">\n" +
                "  <br>\n" +
                "  <input type=\"submit\" value=\"Download\" style=\"font-size: 2em; \">\n" +
                "</form>\n" +
                "<br/>\n" +
                switchToProgress.replace("..", "../boat") +
                "</body>\n" +
                "</html>\n";
    }

    @GetMapping({"/boat"})
    @NotNull
    public final String getTorrents(@RequestParam("q") @NotNull String searchString) {
        List<Torrent> combineResults = new ArrayList<>();

        long currentTimeMillis = System.currentTimeMillis();

        final List<TorrentSearchEngine> activeSearchEngines = new ArrayList<>();
        activeSearchEngines.addAll(torrentSearchEngineService.getActiveSearchEngines());
        activeSearchEngines.parallelStream()
                .forEach(torrentSearchEngine -> combineResults.addAll(torrentSearchEngine.searchTorrents(searchString)));
        List<Torrent> returnResults = new ArrayList<>(cleanDuplicates(combineResults));
        // checkAllForCache
        List<Torrent> cacheStateOfTorrents = new Premiumize(httpHelper, theFilmDataBaseService).getCacheStateOfTorrents(returnResults);
        List<Torrent> torrentList = cacheStateOfTorrents.stream().map(torrent -> TorrentHelper.evaluateRating(torrent, searchString)).collect(Collectors.toList());
        torrentList.sort(TorrentHelper.torrentSorter);

        System.out.println(String.format("Took: [%s]ms for [%s]", (System.currentTimeMillis() - currentTimeMillis), searchString));

        return "G: " + torrentList.stream().limit(25).collect(Collectors.toList());
    }

    private List<Torrent> cleanDuplicates(List<Torrent> combineResults) {
        ArrayList<Torrent> cleanedTorrents = new ArrayList<>();
        combineResults.forEach(result -> {
            if (!cleanedTorrents.contains(result)) {
                cleanedTorrents.add(result);
            }
        });
        return cleanedTorrents;
    }

    @RequestMapping({"/boat/download"})
    @NotNull
    public final String downloadTorrentToPremiumize(@RequestParam(value = "d", required = false) String downloadUri, @RequestParam(value = "dd", required = false) String directDownloadUri) {
        List<Torrent> torrentsToBeDownloaded = new ArrayList<>();
        String decodedUri;
        if (Strings.isNotEmpty(downloadUri)) {
            byte[] magnetUri = Base64.getUrlDecoder().decode(downloadUri);
            decodedUri = new String(magnetUri, StandardCharsets.UTF_8);
            addUriToQueue(torrentsToBeDownloaded, decodedUri);
        } else if (Strings.isNotEmpty(directDownloadUri)) {
            decodedUri = directDownloadUri;
            if (!decodedUri.contains(",")) {
                addUriToQueue(torrentsToBeDownloaded, decodedUri);
            } else {
                String[] uris = decodedUri.split(",");
                Stream.of(uris).forEach(uri -> addUriToQueue(torrentsToBeDownloaded, uri));
            }
        }
        if(torrentsToBeDownloaded.size()==1) {
        return switchToProgress + (new Premiumize(httpHelper, theFilmDataBaseService)).addTorrentToQueue(torrentsToBeDownloaded.get(0));
        } else {
            Premiumize premiumize = new Premiumize(httpHelper, theFilmDataBaseService);
            torrentsToBeDownloaded.forEach(premiumize::addTorrentToQueue);
            return switchToProgress;
        }
    }

    private void addUriToQueue(List<Torrent> torrentsToBeDownloaded, String decodedUri) {
        Torrent torrentToBeDownloaded = new Torrent("BoatController");
        torrentToBeDownloaded.magnetUri = decodedUri;
        torrentsToBeDownloaded.add(torrentToBeDownloaded);
    }

    @RequestMapping({"/boat/tfdb"})
    @NotNull
    public final String searchTfdb(@RequestParam(value = "q") String query) {
        return theFilmDataBaseService.search(query).toString();
    }

    @GetMapping({"/boat/debug"})
    @NotNull
    public final String getDebugInfo() {
        torrentMetaService.refreshTorrents();
        List<Torrent> remoteTorrents = torrentMetaService.getActiveTorrents();
        return "v:" + PropertiesHelper.getVersion() + "<br/>ActiveSearchEngines: " + torrentSearchEngineService.getActiveSearchEngines() + "<br/>D: " + remoteTorrents;
    }

    @GetMapping({"/boat/shutdown"})
    @NotNull
    public final void shutdownServer() {
        System.exit(0);
    }
}
