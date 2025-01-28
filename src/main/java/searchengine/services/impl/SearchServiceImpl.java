package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.search.SearchResultItem;
import searchengine.dto.search.SearchResultsResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.MorphologyService;
import searchengine.services.interfaces.SearchService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final MorphologyService morphologyService;
    private final RelevanceService relevanceService;
    private final SnippetService snippetService;

    @Override
    @Transactional
    public SearchResultsResponse search(String query, String siteUrl, int offset, int limit) {
        System.out.println("=== Начало метода search ===");
        System.out.println("Поисковый запрос: " + query);
        System.out.println("URL сайта: " + siteUrl);
        System.out.println("Offset: " + offset + ", Limit: " + limit);

        Map<String, Integer> queryLemmas = morphologyService.collectLemmas(query);
        if (queryLemmas.isEmpty()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Леммы не найдены.");
        }

        List<Site> sites = getSites(siteUrl);
        if (sites.isEmpty()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Сайты не найдены.");
        }

        queryLemmas = relevanceService.filterFrequentLemmas(queryLemmas, sites);
        if (queryLemmas.isEmpty()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Все леммы отсутствуют в базе данных.");
        }

        List<Page> pages = relevanceService.findPagesByLemmas(sites, new ArrayList<>(queryLemmas.keySet()));
        if (pages.isEmpty()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Совпадений по запросу не найдено.");
        }

        List<SearchResultItem> searchResults = relevanceService.buildSearchResults(pages, queryLemmas, offset, limit);
        System.out.println("Количество итоговых результатов: " + searchResults.size());

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
}
