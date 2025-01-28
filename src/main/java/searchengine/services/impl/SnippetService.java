package searchengine.services.impl;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SnippetService {

    public String buildSnippet(String content, Set<String> lemmas) {
        if (content == null || content.isEmpty()) {
            return "Содержимое страницы недоступно.";
        }

        String plainText = Jsoup.parse(content).text();
        final int SNIPPET_LENGTH = 200;
        StringBuilder snippet = new StringBuilder();
        boolean foundMatch = false;

        for (String lemma : lemmas) {
            int index = plainText.toLowerCase().indexOf(lemma.toLowerCase());
            if (index != -1) {
                foundMatch = true;
                int start = Math.max(0, index - SNIPPET_LENGTH / 2);
                int end = Math.min(plainText.length(), start + SNIPPET_LENGTH);
                String fragment = plainText.substring(start, end);
                for (String match : lemmas) {
                    fragment = fragment.replaceAll("(?i)" + match, "<b>" + match + "</b>");
                }
                snippet.append(fragment).append("...");
                break;
            }
        }

        return foundMatch ? snippet.toString().trim() : "Совпадений по запросу не найдено.";
    }
}
