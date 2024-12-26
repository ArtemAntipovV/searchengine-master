package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResultItem;
import searchengine.dto.search.SearchResultsResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import javax.transaction.Transactional;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final MorphologyService morphologyService;
    private final IndexRepository indexRepository;

    @Override
    @Transactional
    public SearchResultsResponse search(String query, String siteUrl, int offset, int limit) {
        System.out.println("Search query: " + query);
        System.out.println("Site URL: " + siteUrl);
        System.out.println("Offset: " + offset + ", Limit: " + limit);

        if (query == null || query.isBlank()) {
            System.out.println("Query is empty.");
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Поисковый запрос не может быть пустым.");
        }

        HashMap<String, Integer> lemmasSearchQueryMap = morphologyService.collectLemmas(query);
        System.out.println("Extracted lemmas: " + lemmasSearchQueryMap);

        if (lemmasSearchQueryMap.isEmpty()) {
            System.out.println("No lemmas found in query.");
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Леммы не найдены в поисковом запросе.");
        }

        List<Site> sites = getSites(siteUrl);
        System.out.println("Sites for search: " + sites);

        if (sites.isEmpty()) {
            System.out.println("No sites available for search.");
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Сайты для поиска отсутствуют.");
        }

        lemmasSearchQueryMap = filterFrequentLemmas(lemmasSearchQueryMap, 80.0, sites);
        System.out.println("Filtered lemmas: " + lemmasSearchQueryMap);

        if (lemmasSearchQueryMap.isEmpty()) {
            System.out.println("All lemmas were filtered out.");
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Все леммы были отфильтрованы.");
        }

        List<Page> pages = findPagesByLemmas(sites, new ArrayList<>(lemmasSearchQueryMap.keySet()));
        System.out.println("Pages found: " + pages);

        if (pages.isEmpty()) {
            System.out.println("No pages found in database. Attempting external search.");
            return searchExternally(query, sites, offset, limit);
        }

        List<SearchResultItem> searchResults = calculateRelevanceAndBuildResponse(pages, new ArrayList<>(lemmasSearchQueryMap.keySet()), offset, limit);
        System.out.println("Search results: " + searchResults);
        System.out.println("Final search results: " + searchResults.size());
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

    private List<Page> findPagesByLemmas(List<Site> sites, List<String> lemmas) {
        Set<Integer> pageIds = new HashSet<>();

        for (String lemma : lemmas) {
            List<Lemma> lemmaEntities = findLemmas(lemma, sites);

            System.out.println("Lemma: " + lemma + ", Lemma entities: " + lemmaEntities);

            if (lemmaEntities.isEmpty()) {
                return Collections.emptyList();
            }

            Set<Integer> pagesForLemma = lemmaEntities.stream()
                    .flatMap(lemmaEntity -> lemmaEntity.getIndexes().stream())
                    .map(index -> index.getPage().getId())
                    .collect(Collectors.toSet());

            if (pageIds.isEmpty()) {
                pageIds.addAll(pagesForLemma);
            } else {
                pageIds.retainAll(pagesForLemma);
            }

            if (pageIds.isEmpty()) break;
        }

        List<Page> pages = pageRepository.findAllById(pageIds);
        System.out.println("Filtered pages by lemmas: " + pages);
        return pages;
    }

    private HashMap<String, Integer> filterFrequentLemmas(HashMap<String, Integer> lemmasMap, double threshold, List<Site> sites) {
        int totalPages = sites.stream()
                .mapToInt(site -> pageRepository.countBySiteId(site.getId()))
                .sum();
        System.out.println("Total pages in sites: " + totalPages);

        return lemmasMap.entrySet().stream()
                .filter(entry -> {
                    int frequency = lemmaRepository.countByLemmaAndSites(entry.getKey(), sites);
                    System.out.println("Lemma: " + entry.getKey() + ", Frequency: " + frequency);
                    return ((double) frequency / totalPages * 100) <= threshold;
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        HashMap::new
                ));
    }

    private List<SearchResultItem> calculateRelevanceAndBuildResponse(List<Page> pages, List<String> lemmas, int offset, int limit) {
        Map<Integer, Double> pageRelevance = new HashMap<>();
        double maxRelevance = 0;

        for (Page page : pages) {
            double relevance = lemmas.stream()
                    .mapToDouble(lemma -> indexRepository.getRankForPage(page.getId(), lemma).orElse(0.0))
                    .sum();
            pageRelevance.put(page.getId(), relevance);
            maxRelevance = Math.max(maxRelevance, relevance);
        }


        double finalMaxRelevance = maxRelevance == 0 ? 1 : maxRelevance;

        return pages.stream()
                .distinct()
                .map(page -> {
                    double relevance = pageRelevance.get(page.getId()) / finalMaxRelevance;


                    String snippet = buildSnippet(Jsoup.parse(page.getContent()).text(), lemmas);

                    return new SearchResultItem(
                            page.getSite().getUrl(),
                            page.getSite().getName(),
                            extractTitle(page),
                            snippet,
                            relevance
                    );
                })
                .sorted(Comparator.comparing(SearchResultItem::getRelevance).reversed())
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    private SearchResultsResponse searchExternally(String query, List<Site> sites, int offset, int limit) {
        List<SearchResultItem> externalResults = new ArrayList<>();

        try {
            for (Site site : sites) {
                System.out.println("Fetching content from site: " + site.getUrl());
                String siteContent = fetchSiteContent(site.getUrl());
                externalResults.addAll(parseSiteContentForQuery(siteContent, query, site));

                if (externalResults.size() >= limit) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Ошибка при выполнении внешнего поиска.");
        }

        System.out.println("External search results: " + externalResults.size());
        return createResponse(externalResults, offset, limit);
    }

    private String fetchSiteContent(String siteUrl) throws IOException {
        System.out.println("Connecting to site: " + siteUrl);
        return Jsoup.connect(siteUrl).get().html();
    }

    private List<SearchResultItem> parseSiteContentForQuery(String siteContent, String query, Site site) {
        List<SearchResultItem> results = new ArrayList<>();

        String text = Jsoup.parse(siteContent).text();
        System.out.println("Извлеченный текст страницы: " + text);

        HashMap<String, Integer> pageLemmas = morphologyService.collectLemmas(text);
        System.out.println("Леммы на странице: " + pageLemmas);

        List<String> queryLemmas = new ArrayList<>(morphologyService.collectLemmas(query).keySet());
        System.out.println("Леммы из запроса: " + queryLemmas);

        for (String queryLemma : queryLemmas) {
            if (pageLemmas.containsKey(queryLemma)) {
                System.out.println("Совпадение найдено для леммы: " + queryLemma);
            } else {
                System.out.println("Лемма не найдена: " + queryLemma);
            }
        }

        boolean hasMatch = queryLemmas.stream().anyMatch(pageLemmas::containsKey);
        if (hasMatch) {
            String snippet = buildSnippet(text, queryLemmas);
            SearchResultItem item = new SearchResultItem(
                    site.getUrl(),
                    site.getName(),
                    query,
                    snippet,
                    1.0
            );
            results.add(item);
        } else {
            System.out.println("Нет совпадений для лемм: " + queryLemmas + " на сайте: " + site.getUrl());
        }

        return results;
    }

    private String buildSnippet(String content, List<String> lemmas) {
        StringBuilder snippet = new StringBuilder();

        for (String lemma : lemmas) {
            if (content.contains(lemma)) {
                snippet.append("<b>").append(lemma).append("</b>").append(" ");
            } else {
                System.out.println("Лемма не найдена в тексте: " + lemma);
            }
        }

        return snippet.length() > 200 ? snippet.substring(0, 200) + "..." : snippet.toString();
    }

    private String extractTitle(Page page) {
        return Jsoup.parse(page.getContent()).title();
    }

    private List<Lemma> findLemmas(String lemma, List<Site> sites) {
        if (sites.isEmpty()) {
            return lemmaRepository.findByLemma(lemma);
        }
        return lemmaRepository.findByLemmaAndSites(lemma, sites);
    }

    private SearchResultsResponse createResponse(List<SearchResultItem> searchResults, int offset, int limit) {
        List<SearchResultItem> paginatedResults = searchResults.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
        return new SearchResultsResponse(true, searchResults.size(), paginatedResults, "Поиск выполнен успешно.");
    }

}
