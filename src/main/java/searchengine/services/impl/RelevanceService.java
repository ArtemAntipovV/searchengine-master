package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResultItem;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageLemmaRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelevanceService {

    private final PageLemmaRepository pageLemmaRepository;
    private final SnippetService snippetService;

    public Map<String, Integer> filterFrequentLemmas(Map<String, Integer> lemmas, List<Site> sites) {
        long totalPageCount = pageLemmaRepository.countTotalPages(sites);
        double threshold = totalPageCount * 0.8;

        Map<String, Long> lemmaFrequencies = pageLemmaRepository.countPagesForLemmas(new ArrayList<>(lemmas.keySet()))
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));

        return lemmas.entrySet().stream()
                .filter(entry -> lemmaFrequencies.getOrDefault(entry.getKey(), 0L) <= threshold)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<Page> findPagesByLemmas(List<Site> sites, List<String> lemmas) {
        List<Integer> siteIds = sites.stream().map(Site::getId).collect(Collectors.toList());

        return pageLemmaRepository.findPagesByLemmasAndSites(lemmas, siteIds)
                .stream()
                .filter(page -> containsLemmaInContent(page.getContent(), lemmas))
                .collect(Collectors.toList());
    }

    private boolean containsLemmaInContent(String content, List<String> lemmas) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        String plainText = Jsoup.parse(content).text().toLowerCase();
        return lemmas.stream().anyMatch(lemma -> plainText.contains(lemma.toLowerCase()));
    }

    public List<SearchResultItem> buildSearchResults(List<Page> pages, Map<String, Integer> queryLemmas, Integer offset, Integer limit) {
        Set<String> lemmas = queryLemmas.keySet();
        double maxRelevance = pages.stream()
                .mapToDouble(page -> calculateRelevance(page, lemmas))
                .max()
                .orElse(1.0);

        return pages.stream()
                .map(page -> {
                    double relevance = calculateRelevance(page, lemmas);
                    if (relevance == 0.0) return null;

                    String snippet = snippetService.buildSnippet(Jsoup.parse(page.getContent()).text(), lemmas);

                    return new SearchResultItem(
                            page.getSite().getUrl(),
                            sanitizeUri(page.getUrl()),
                            page.getSite().getName(),
                            Jsoup.parse(page.getContent()).title(),
                            snippet,
                            relevance / maxRelevance
                    );
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SearchResultItem::getRelevance).reversed())
                .collect(Collectors.toList());
    }

    private double calculateRelevance(Page page, Set<String> lemmas) {
        return lemmas.stream()
                .mapToDouble(lemma -> pageLemmaRepository.getRankForPage(page.getId(), lemma)
                        .stream()
                        .findFirst()
                        .orElse(0.0F))
                .sum();
    }

    private String sanitizeUri(String uri) {
        if (uri == null || uri.trim().isEmpty() || "undefined".equalsIgnoreCase(uri)) {
            return "/";
        }
        try {
            if (uri.startsWith("http")) {
                return new java.net.URI(uri).getPath() != null ? new java.net.URI(uri).getPath() : "/";
            }
        } catch (Exception e) {
            System.err.println("Ошибка обработки URL: " + uri);
        }
        return uri.startsWith("/") ? uri : "/" + uri;
    }
}