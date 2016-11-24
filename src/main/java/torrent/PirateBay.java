package torrent;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import utilities.HttpHelper;
import utilities.PropertiesHelper;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by deha on 02/10/2016.
 */
public class PirateBay implements TorrentSearchEngine {

    private static final int MAX_PAGES = 5;

    public static void main(String[] args) {


        System.out.println(PropertiesHelper.getProperty("customer_id"));

        List<Torrent> resultList = new PirateBay().searchTorrents("iron man 3");

        System.out.println("G: " + resultList.size());

        new PirateBay().printResults(resultList);

    }

    @Override
    public List<Torrent> searchTorrents(String torrentname) {

        int iPageindex=0;

        CopyOnWriteArrayList<Torrent> torrentList= new CopyOnWriteArrayList<>();

        for(int i=0;i<MAX_PAGES;i++){

            System.out.println("P ["+(i+1)+"/"+MAX_PAGES+"]");

            iPageindex = i;
            String localString= HttpHelper.getPage("https://thepiratebay.org/search/" + torrentname + "/"+iPageindex+"/99/200",null,"lw=s");
            torrentList.addAll(parseTorrentsOnResultPage(localString,torrentname));
        }

        return torrentList;
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String torrentname) {

        ArrayList<Torrent> torrentList= new ArrayList<>();

        Document doc = Jsoup.parse(pageContent);

        Elements torrentListOnPage = doc.select("tr:not(.header)");

        for (Element torrent:torrentListOnPage) {

            Torrent tempTorrent = new Torrent();

            // extract ahref for title
            Elements nameElements = torrent.select("a[title~=Details for]");
            if(nameElements.size()>0) {
                tempTorrent.name = nameElements.get(0).text();
            }

            // extract uri for magnetlink
            Elements uriElements = torrent.select("a[title~=using magnet]");
            if(uriElements.size()>0) {
                tempTorrent.magnetUri = uriElements.get(0).attributes().get("href");
            }

            // extract date

            String inputDateString = torrent.select("td").get(2).text().replace("\u00a0"," ");

            SimpleDateFormat formatter = new SimpleDateFormat("MM-dd yyyy");

            try {
                tempTorrent.date = formatter.parse(inputDateString);
            } catch (ParseException e) {
                e.printStackTrace();
            }

            // extract size
            tempTorrent.size = torrent.select("td").get(4).text().replace("\u00a0"," ");

            if (tempTorrent.size.contains("GiB")) {
                tempTorrent.lsize = (long) (Double.parseDouble(tempTorrent.size.replace("GiB","").trim())*1024);
            } else if (tempTorrent.size.contains("MiB")) {
                tempTorrent.lsize = (long) (Double.parseDouble(tempTorrent.size.replace("MiB","").trim()));
            }


            // extract seeder
            tempTorrent.seeder = Integer.parseInt(torrent.select("td").get(5).text());

            // extract leecher
            tempTorrent.leecher = Integer.parseInt(torrent.select("td").get(6).text());

            // evaluate result
            if(tempTorrent.name.toLowerCase().replaceAll("[ .]","").contains(torrentname.toLowerCase().replaceAll("[ .]",""))) {
                tempTorrent.searchRating+=2;
            }
            if(tempTorrent.lsize>10*1024) {
                tempTorrent.searchRating++;
            } /* else if(tempTorrent.lsize>1300)
            {
                tempTorrent.searchRating++;
            } */
            if(tempTorrent.seeder>30) {
                tempTorrent.searchRating++;
            }


            // filter torrents without any seeders
            if(tempTorrent.seeder>0) {
                torrentList.add(tempTorrent);
            }
        }

        return torrentList;

    }

    private void printResults(List<Torrent> torrents){

        DecimalFormat df = new DecimalFormat("#.###");

        torrents.sort(new Comparator<Torrent>() {
            @Override
            public int compare(Torrent o1, Torrent o2) {

                if(o1.searchRating>o2.searchRating) {
                    return -1;
                } else if(o1.searchRating<o2.searchRating) {
                    return 1;
                } else {
                    if(o1.lsize>o2.lsize){
                        return -1;
                    } else if(o1.lsize<o2.lsize) {
                        return 1;
                    } else {
                        return 0;
                    }
                }

            }
        });

        for(Torrent t : torrents){
            double seedRatio = 0;

            if(t.leecher>0) {
                seedRatio = (double)t.seeder/(double)t.leecher;
            } else {
                seedRatio = t.seeder;
            }

            System.out.println("["+t.name+"]["+t.size+"]["+t.leecher+"/"+t.seeder+"@"+df.format(seedRatio)+"] R:"+t.searchRating); //["+t.date+"]");
        }

    }

}
