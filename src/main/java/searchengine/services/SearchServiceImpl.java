package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResultItem;
import searchengine.dto.search.SearchResultsResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final MorphologyService morphologyService;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public SearchResultsResponse search(String query, String siteUrl, int offset, int limit) {
        if (query == null || query.isBlank()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Поисковый запрос не может быть пустым.");
        }

        Map<String, Integer> queryLemmas = morphologyService.collectLemmas(query);
        if (queryLemmas.isEmpty()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Леммы не найдены в запросе.");
        }

        List<Site> sites = getSites(siteUrl);
        if (sites.isEmpty()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Сайты для поиска отсутствуют.");
        }

        queryLemmas = filterFrequentLemmas(queryLemmas, sites);
        if (queryLemmas.isEmpty()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Все леммы были отфильтрованы.");
        }

        List<Page> pages = findPagesByLemmas(sites, new ArrayList<>(queryLemmas.keySet()));
        if (pages.isEmpty()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Страницы не найдены.");
        }

        List<SearchResultItem> searchResults = buildSearchResults(pages, queryLemmas, 0, 30);
        return new SearchResultsResponse(true, searchResults.size(), searchResults, "Поиск выполнен успешно.");
    }

    private List<Site> getSites(String siteUrl) {
        if (siteUrl != null && !siteUrl.isBlank()) {
            return siteRepository.findByUrl(siteUrl)
                    .map(Collections::singletonList)
                    .orElse(Collections.emptyList());
        }
        return siteRepository.findAll();
    }

    private Map<String, Integer> filterFrequentLemmas(Map<String, Integer> lemmas, List<Site> sites) {
        int totalPages = sites.stream()
                .mapToInt(site -> pageRepository.countBySiteId(site.getId()))
                .sum();
        System.out.println("Общее количество страниц: " + totalPages);

        if (totalPages == 0) {
            return lemmas;
        }

        double threshold = totalPages * 0.4;
        System.out.println("Порог фильтрации: " + threshold);

        String sql = "SELECT l.lemma, COUNT(p.id) " +
                "FROM Lemma l JOIN l.pages p " +
                "WHERE l.lemma IN :lemmas AND l.site IN :sites " +
                "GROUP BY l.lemma";

        List<Object[]> result = entityManager.createQuery(sql, Object[].class)
                .setParameter("lemmas", lemmas.keySet())
                .setParameter("sites", sites)
                .getResultList();

        System.out.println("Результаты SQL-запроса для фильтрации лемм:");
        result.forEach(row -> System.out.println("Лемма: " + row[0] + ", Частота: " + row[1]));

        Map<String, Integer> filteredLemmas = result.stream()
                .filter(row -> (long) row[1] <= threshold)
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> lemmas.get(row[0]),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        if (filteredLemmas.isEmpty()) {
            System.out.println("Все леммы были отфильтрованы. Увеличиваем порог фильтрации.");
            final double increasedThreshold = threshold * 2;
            filteredLemmas = result.stream()
                    .filter(row -> (long) row[1] <= increasedThreshold)
                    .collect(Collectors.toMap(
                            row -> (String) row[0],
                            row -> lemmas.get(row[0]),
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
        }

        if (filteredLemmas.isEmpty()) {
            System.out.println("Леммы не найдены даже после увеличения порога.");
            return lemmas;
        }

        System.out.println("Леммы после фильтрации: " + filteredLemmas.keySet());
        return filteredLemmas;
    }

    private List<Page> findPagesByLemmas(List<Site> sites, List<String> lemmas) {
        String sql = "SELECT DISTINCT p FROM Page p " +
                "JOIN p.lemmas l " +
                "WHERE l.lemma IN :lemmas AND p.site IN :sites";
        return entityManager.createQuery(sql, Page.class)
                .setParameter("lemmas", lemmas)
                .setParameter("sites", sites)
                .getResultList();
    }

    private List<SearchResultItem> buildSearchResults(List<Page> pages, Map<String, Integer> queryLemmas, int offset, int limit) {
        Set<String> lemmas = queryLemmas.keySet();
        double maxRelevance = pages.stream()
                .mapToDouble(page -> calculateRelevance(page, lemmas))
                .max()
                .orElse(1.0);

        return pages.stream()
                .map(page -> {
                    double relevance = calculateRelevance(page, lemmas) / maxRelevance;
                    String snippet = buildSnippet(Jsoup.parse(page.getContent()).text(), lemmas);
                    String fullUrl = page.getUrl(); // Полный URL страницы
                    String siteUrl = page.getSite().getUrl(); // URL сайта
                    String relativeUri = fullUrl.replace(siteUrl, ""); // Относительный путь

                    return new SearchResultItem(
                            siteUrl,
                            page.getSite().getName(),
                            relativeUri,
                            extractTitle(page),
                            snippet,
                            relevance
                    );
                })
                .sorted(Comparator.comparing(SearchResultItem::getRelevance).reversed())
                .skip(0)
                .limit(30)
                .collect(Collectors.toList());
    }

    private double calculateRelevance(Page page, Set<String> lemmas) {
        return lemmas.stream()
                .mapToDouble(lemma -> indexRepository.getRankForPage(page.getId(), lemma).stream().findFirst().orElse(0.0F))
                .sum();
    }

    private String extractTitle(Page page) {
        return Jsoup.parse(page.getContent()).title();
    }

    private String buildSnippet(String content, Set<String> lemmas) {
        StringBuilder snippet = new StringBuilder();
        String[] words = content.split("\\s+");

        for (String lemma : lemmas) {
            for (int i = 0; i < words.length; i++) {
                if (words[i].toLowerCase().contains(lemma.toLowerCase())) {
                    int start = Math.max(0, i - 5);
                    int end = Math.min(words.length, i + 6);

                    for (int j = start; j < end; j++) {
                        if (words[j].toLowerCase().contains(lemma.toLowerCase())) {
                            snippet.append("<b>").append(words[j]).append("</b> ");
                        } else {
                            snippet.append(words[j]).append(" ");
                        }
                    }
                    snippet.append("... ");
                    break;
                }
            }
        }

        if (snippet.isEmpty()) {
            snippet.append(content, 0, Math.min(content.length(), 200)).append("...");
        }

        return snippet.toString();
    }
}
