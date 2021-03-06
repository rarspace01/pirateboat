package boat.multifileHoster

import boat.torrent.HttpUser
import boat.torrent.Torrent
import boat.torrent.TorrentFile
import boat.torrent.TorrentHelper
import boat.utilities.HttpHelper
import boat.utilities.PropertiesHelper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.apache.logging.log4j.util.Strings
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.stream.Collectors

class Premiumize(httpHelper: HttpHelper?) : HttpUser(httpHelper), MultifileHoster {
    override fun addTorrentToQueue(toBeAddedTorrent: Torrent): String {
        val response: String
        val addTorrenntUrl =
            "https://www.premiumize.me/api/transfer/create?apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY") +
                    "&type=hello.torrent&src=" + cleanMagnetUri(toBeAddedTorrent.magnetUri)
        response = httpHelper.getPage(addTorrenntUrl)
        return response
    }

    private fun cleanMagnetUri(magnetUri: String): String {
        return magnetUri.replace(" ".toRegex(), "_")
    }

    override fun getRemoteTorrents(): List<Torrent> {
        val remoteTorrentList: List<Torrent>
        val responseTorrents: String =
            httpHelper.getPage("https://www.premiumize.me/api/transfer/list?apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY"))
        remoteTorrentList = parseRemoteTorrents(responseTorrents)
        return remoteTorrentList
    }

    override fun getPrio(): Int {
        return 1
    }

    override fun getRemainingTrafficInMB(): Double {
        val responseAccount: String =
            httpHelper.getPage("https://www.premiumize.me/api/account/info?apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY"))
        val boostAccount: String =
            httpHelper.getPage("https://www.premiumize.me/account?apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY"))
        return parseRemainingTrafficInMB(responseAccount) + parseRemainingBoostTrafficInMB(boostAccount)
    }

    private fun parseRemainingBoostTrafficInMB(boostAccount: String): Double {
        val document = Jsoup.parse(boostAccount)
        return document.getElementsByClass("col-md-12")
            .filter { element -> element.text().matches(Regex(".*Booster Points .* points available learn more.*")) }
            .filterNotNull()
            .map { element: Element ->
                var boosterPoints = 0.0
                val matcher =
                    Pattern.compile(".*Booster Points (.*) points available learn more.*").matcher(element.text())
                while (matcher.find()) {
                    boosterPoints = matcher.group(1).toDouble()
                }
                boosterPoints * 1024.0
            }.getOrElse(0, defaultValue = { 0.0 })
    }

    private fun parseRemainingTrafficInMB(responseAccount: String): Double {
        val mapper = ObjectMapper()
        try {
            val jsonNode = mapper.readTree(responseAccount)
            return (1.0 - jsonNode.get("limit_used").asDouble()) * 1024.0 * 1024.0
        } catch (ignored: Exception) {
            return 0.0
        }
    }

    override fun getName(): String {
        return this.javaClass.simpleName
    }

    override fun getFilesFromTorrent(torrent: Torrent): List<TorrentFile> {
        val returnList: MutableList<TorrentFile> = ArrayList()
        val responseFiles = httpHelper.getPage(
            "https://www.premiumize.me/api/folder/list?id=" + torrent.folder_id +
                    "&apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY")
        )
        val m = ObjectMapper()
        try {
            val rootNode = m.readTree(responseFiles)
            val localNodes = rootNode.path("content")
            val fileList = localNodes.findParents("type")
            for (jsonFile in fileList) {
                if (jsonFile["type"].asText() == "file") {
                    extractTorrentFileFromJSON(torrent, returnList, jsonFile, "")
                } else if (jsonFile["type"].asText() == "folder") {
                    extractTorrentFilesFromJSONFolder(torrent, returnList, jsonFile, "")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return returnList
    }

    private fun extractTorrentFilesFromJSONFolder(
        torrent: Torrent,
        returnList: MutableList<TorrentFile>,
        jsonFolder: JsonNode,
        prefix: String
    ) {
        val responseFiles = httpHelper.getPage(
            "https://www.premiumize.me/api/folder/list?id=" + jsonFolder["id"].asText() +
                    "&apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY")
        )
        val folderName = prefix + jsonFolder["name"].asText() + "/"
        val m = ObjectMapper()
        val rootNode: JsonNode
        try {
            rootNode = m.readTree(responseFiles)
            val localNodes = rootNode.path("content")
            val fileList = localNodes.findParents("type")
            for (jsonFile in fileList) {
                if (jsonFile["type"].asText() == "file") {
                    extractTorrentFileFromJSON(torrent, returnList, jsonFile, folderName)
                } else if (jsonFile["type"].asText() == "folder") {
                    extractTorrentFilesFromJSONFolder(torrent, returnList, jsonFile, folderName)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun extractTorrentFileFromJSON(
        torrent: Torrent,
        returnList: MutableList<TorrentFile>,
        jsonFile: JsonNode,
        prefix: String
    ) {
        val tf = TorrentFile()
        // check if hello.torrent is onefile and is located in root
        if (torrent.file_id != null && torrent.folder_id != null) {
            if (jsonFile["id"].asText().toString() == torrent.file_id) {
                tf.name = prefix + jsonFile["name"].asText()
                tf.filesize = jsonFile["size"].asLong()
                tf.url = jsonFile["link"].asText()
                returnList.add(tf)
            }
        } else {
            tf.name = prefix + jsonFile["name"].asText()
            tf.filesize = jsonFile["size"].asLong()
            tf.url = jsonFile["link"].asText()
            returnList.add(tf)
        }
    }

    private fun parseRemoteTorrents(pageContent: String): ArrayList<Torrent> {
        val remoteTorrentList = ArrayList<Torrent>()
        val m = ObjectMapper()
        try {
            val rootNode = m.readTree(pageContent)
            val localNodes = rootNode.path("transfers")
            for (localNode in localNodes) {
                val tempTorrent = Torrent(getName())
                tempTorrent.name = localNode["name"].asText()
                tempTorrent.folder_id = localNode["folder_id"].asText()
                tempTorrent.file_id = localNode["file_id"].asText()
                tempTorrent.folder_id = cleanJsonNull(tempTorrent.folder_id)
                tempTorrent.file_id = cleanJsonNull(tempTorrent.file_id)
                tempTorrent.remoteId = localNode["id"].asText()
                tempTorrent.status = localNode["status"].asText()
                val src = localNode["src"].asText()
                if (src.contains("btih")) {
                    tempTorrent.magnetUri = src
                }
                val messages = localNode["message"].asText().split(",").toTypedArray()
                if (messages.size == 3) {
                    tempTorrent.eta = messages[2]
                }
                tempTorrent.progress = localNode["progress"].toString()
                remoteTorrentList.add(tempTorrent)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return remoteTorrentList
    }

    private fun cleanJsonNull(inputString: String): String? {
        return if (inputString == "null") null else inputString
    }

    override fun delete(remoteTorrent: Torrent) {
        val removeTorrenntUrl = "https://www.premiumize.me/api/transfer/delete?id=" + remoteTorrent.remoteId + "&" +
                "&apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY") +
                "&type=hello.torrent&src=" + remoteTorrent.magnetUri
        val page = httpHelper.getPage(removeTorrenntUrl)
        if (!page.contains("success")) {
            log.error("Deleting failed: {}", removeTorrenntUrl)
        }
    }

    override fun enrichCacheStateOfTorrents(torrents: List<Torrent>) {
        val maximumQueryCacheSize = 150
        if (torrents.size <= maximumQueryCacheSize) {
            enrichCacheStatusForGivenTorrents(torrents)
        } else {
            for (i in torrents.indices step maximumQueryCacheSize) {
                enrichCacheStatusForGivenTorrents(
                    torrents.subList(
                        i,
                        (i + maximumQueryCacheSize).coerceAtMost(torrents.size - 1)
                    )
                )
            }
        }
    }

    private fun enrichCacheStatusForGivenTorrents(torrents: List<Torrent>) {
        val requestUrl =
            "https://www.premiumize.me/api/cache/check?" + "apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY") + "%s"
        val urlEncodedBrackets = TorrentHelper.urlEncode("[]")
        val collected = torrents.stream().map { obj: Torrent -> obj.torrentId }
            .collect(Collectors.joining("&items$urlEncodedBrackets=", "&items$urlEncodedBrackets=", ""))
        val checkUrl = String.format(requestUrl, collected)
        var pageContent = httpHelper.getPage(checkUrl)
        if (Strings.isEmpty(pageContent)) {
            Thread.sleep(100)
            pageContent = httpHelper.getPage(checkUrl)
        }
        val jsonRoot = JsonParser.parseString(pageContent)
        if (jsonRoot == null || !jsonRoot.isJsonObject) {
            log.error("couldn't retrieve cache for:$checkUrl")
            log.error(pageContent)
        } else {
            val response = jsonRoot.asJsonObject["response"]
            val responseArray = response.asJsonArray
            val index = AtomicInteger()
            if (responseArray.size() == torrents.size) {
                responseArray.forEach(Consumer { jsonElement: JsonElement ->
                    if (jsonElement.asBoolean) {
                        torrents[index.get()].cached.add(this.javaClass.simpleName)
                    }
                    index.getAndIncrement()
                })
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(Premiumize::class.java)
    }
}