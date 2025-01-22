package searchengine.config;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HtmlUtils {


    public static String getHtmlContent(String url) throws IOException {
        return Jsoup.connect(url).get().html();
    }

}
