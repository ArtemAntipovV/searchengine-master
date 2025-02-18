package searchengine.services.impl;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.services.interfaces.MorphologyProcessor;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SnippetService {

    private final MorphologyProcessor morphologyService;

    public SnippetService(MorphologyProcessor morphologyService) {
        this.morphologyService = morphologyService;
    }

    public String buildSnippet(String content, Set<String> lemmas) {
        if (content == null || content.isEmpty()) {
            return "Содержимое страницы недоступно.";
        }

        String plainText = Jsoup.parse(content).text().trim();
        if (plainText.isEmpty()) {
            return "Текст страницы не найден.";
        }

        final int SNIPPET_LENGTH = 200;
        StringBuilder snippet = new StringBuilder();


        List<String> allForms = lemmas.stream()
                .flatMap(lemma -> getAllWordForms(lemma).stream())
                .distinct()
                .collect(Collectors.toList());


        System.out.println(" Поисковые формы: " + allForms);
        System.out.println(" Текст страницы (первые 500 символов): " + plainText.substring(0, Math.min(500, plainText.length())));

        int index = findFirstOccurrence(plainText, allForms);
        if (index != -1) {
            int start = Math.max(0, index - SNIPPET_LENGTH / 2);
            int end = Math.min(plainText.length(), start + SNIPPET_LENGTH);

            String fragment = plainText.substring(start, end);
            fragment = highlightWords(fragment, allForms);
            snippet.append(fragment).append("...");
        } else {
            return "Совпадений по запросу не найдено.";
        }

        return snippet.toString().trim();
    }

    private int findFirstOccurrence(String text, List<String> words) {
        text = text.toLowerCase();
        for (String word : words) {
            Pattern pattern = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.start();
            }
        }
        return -1;
    }

    private String highlightWords(String text, List<String> words) {
        for (String word : words) {
            Pattern pattern = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            text = matcher.replaceAll(matchResult -> "<b>" + matchResult.group() + "</b>");
        }
        return text;
    }

    private List<String> getAllWordForms(String word) {
        List<String> normalForms = morphologyService.getNormalFormsWords(word);
        if (normalForms == null || normalForms.isEmpty()) {
            return Collections.singletonList(word);
        }
        normalForms.add(word);
        return normalForms;
    }
}
