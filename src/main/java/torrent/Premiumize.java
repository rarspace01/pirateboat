package torrent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import utilities.HttpHelper;
import utilities.PropertiesHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by denis on 24/11/2016.
 */
public class Premiumize {

    private List<Torrent> torrentList = new ArrayList<>();

    public String addTorrentToQueue(Torrent toBeAddedTorrent) {
        String response;
        String addTorrenntUrl = "https://www.premiumize.me/api/transfer/create?customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin") +
                "&type=torrent&src=" + toBeAddedTorrent.magnetUri;
        response = HttpHelper.getPage(addTorrenntUrl);
        System.out.println("GET: " + addTorrenntUrl);
        return response;
    }

    public ArrayList<Torrent> getRemoteTorrents() {

        ArrayList<Torrent> remoteTorrentList;

        String responseTorrents;
        responseTorrents = HttpHelper.getPage("https://www.premiumize.me/api/transfer/list?customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin"));
        System.out.println("getRemoteTorrents URL: " + "https://www.premiumize.me/api/transfer/list?customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin"));
        System.out.println("getRemoteTorrents: " + responseTorrents);

        remoteTorrentList = parseRemoteTorrents(responseTorrents);

        return remoteTorrentList;
    }

    public String getMainFileURLFromTorrent(Torrent torrent) {
        List<TorrentFile> tfList = new Premiumize().getFilesFromTorrent(torrent);

        String remoteURL = null;

        // iterate over and check for One File Torrent
        long biggestFileYet = 0;
        for (TorrentFile tf : tfList) {
            if (tf.filesize > biggestFileYet) {
                biggestFileYet = tf.filesize;
                remoteURL = tf.url;
            }
        }
        return remoteURL;
    }

    private List<TorrentFile> getFilesFromTorrent(Torrent torrent) {
        List<TorrentFile> returnList = new ArrayList<>();

        String responseFiles = HttpHelper.getPage("https://www.premiumize.me/api/folder/list?id=" + torrent.folder_id +
                "&customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin"));

        System.out.println(responseFiles);

        ObjectMapper m = new ObjectMapper();
        try {
            JsonNode rootNode = m.readTree(responseFiles);

            JsonNode localNodes = rootNode.path("content");

            List<JsonNode> fileList = localNodes.findParents("type");

            for (JsonNode jsonFile : fileList) {

                if (jsonFile.get("type").asText().equals("file")) {
                    TorrentFile tf = new TorrentFile();

                    // check if torrent is onefile and is located in root
                    if (torrent.file_id != null && torrent.folder_id != null) {
                        if (String.valueOf(jsonFile.get("id").asText()).equals(torrent.file_id)) {
                            tf.name = jsonFile.get("name").asText();
                            tf.filesize = jsonFile.get("size").asLong();
                            tf.url = jsonFile.get("link").asText();
                            returnList.add(tf);
                        }
                    } else {
                        tf.name = jsonFile.get("name").asText();
                        tf.filesize = jsonFile.get("size").asLong();
                        tf.url = jsonFile.get("link").asText();
                        returnList.add(tf);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


        return returnList;
    }

    private ArrayList<Torrent> parseRemoteTorrents(String pageContent) {

        ArrayList<Torrent> remoteTorrentList = new ArrayList<>();

        ObjectMapper m = new ObjectMapper();
        try {
            JsonNode rootNode = m.readTree(pageContent);
            JsonNode localNodes = rootNode.path("transfers");

            for (JsonNode localNode : localNodes) {
                System.out.println(localNode.toString());
                Torrent tempTorrent = new Torrent();
                tempTorrent.name = localNode.get("name").asText();
                tempTorrent.folder_id = localNode.get("folder_id").asText();
                tempTorrent.file_id = localNode.get("file_id").asText();
                tempTorrent.folder_id = cleanJsonNull(tempTorrent.folder_id);
                tempTorrent.file_id = cleanJsonNull(tempTorrent.file_id);
                tempTorrent.remoteId = localNode.get("id").toString().replace("\"", "");
                tempTorrent.status = localNode.get("status").asText();
                tempTorrent.progress = localNode.get("progress").toString();
                remoteTorrentList.add(tempTorrent);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return remoteTorrentList;

    }

    private String cleanJsonNull(String inputString) {
        return inputString.equals("null") ? null : inputString;
    }

    public void delete(Torrent remoteTorrent) {
        String removeTorrenntUrl = "https://www.premiumize.me/api/transfer/delete?id=" + remoteTorrent.remoteId + "&" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin") +
                "&type=torrent&src=" + remoteTorrent.magnetUri;
        HttpHelper.getPage(removeTorrenntUrl);
    }

    public boolean isSingleFileDownload(Torrent remoteTorrent) {
        List<TorrentFile> tfList = new Premiumize().getFilesFromTorrent(remoteTorrent);
        // getMaxFilesize
        // getSumSize
        long sumFileSize = 0L;
        long biggestFileYet = 0L;
        for (TorrentFile tf : tfList) {
            if (tf.filesize > biggestFileYet) {
                biggestFileYet = tf.filesize;
            }
            sumFileSize += tf.filesize;
        }
        // if maxfilesize >90% sumSize --> Singlefile
        return biggestFileYet > (0.9d * sumFileSize);
    }
}
