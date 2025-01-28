package searchengine.config;

import org.jsoup.Jsoup;


import java.io.IOException;


public class HtmlUtils {


    public static String getHtmlContent(String url) throws IOException {
        return Jsoup.connect(url).get().html();
    }

}
