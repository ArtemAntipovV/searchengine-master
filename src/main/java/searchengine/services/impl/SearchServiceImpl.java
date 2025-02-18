package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.search.SearchResultItem;
import searchengine.dto.search.SearchResultsResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.SearchService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SiteRepository siteRepository;
    private final LemmaServiceImpl lemmaService;
    private final RelevanceService relevanceService;


    @Override
    @Transactional
    public SearchResultsResponse search(String query, String siteUrl, Integer offset, Integer limit) {
        System.out.println("=== Начало метода search ===");
        System.out.println("Поисковый запрос: " + query);
        System.out.println("URL сайта: " + siteUrl);
        System.out.println("Offset: " + offset + ", Limit: " + limit);

        if (query == null || query.trim().isEmpty()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Ошибка: запрос не введен.");
        }

        offset = (offset == null) ? 0 : offset;
        limit = (limit == null) ? 20 : limit;

        List<Site> sites = getSites(siteUrl);
        if (sites.isEmpty()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Ошибка: сайты не найдены.");
        }

        Map<String, Integer> queryLemmas = lemmaService.collectLemmas(query);
        if (queryLemmas.isEmpty()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Ошибка: нет значимых слов в запросе.");
        }

        System.out.println("Уникальные леммы запроса: " + queryLemmas.keySet());

        Map<String, Integer> filteredLemmas = relevanceService.filterFrequentLemmas(queryLemmas, sites);
        if (filteredLemmas.isEmpty()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Ошибка: слишком частые слова.");
        }

        List<Page> pages = relevanceService.findPagesByLemmas(sites, new ArrayList<>(filteredLemmas.keySet()));
        if (pages.isEmpty()) {
            return new SearchResultsResponse(false, 0, Collections.emptyList(), "Совпадений по запросу не найдено.");
        }

        System.out.println("Найдено страниц: " + pages.size());

        List<SearchResultItem> allResults = relevanceService.buildSearchResults(pages, filteredLemmas, 0, Integer.MAX_VALUE);

        return buildResponse(allResults, offset, limit);
    }

    private SearchResultsResponse buildResponse(List<SearchResultItem> data, Integer offset, Integer limit) {
        int totalResults = data.size();

        if (offset >= totalResults) {
            return new SearchResultsResponse(true, totalResults, Collections.emptyList(), "Результатов нет на этой странице.");
        }

        int end = Math.min(offset + limit, totalResults);
        List<SearchResultItem> paginatedResults = data.subList(offset, end);

        return new SearchResultsResponse(true, totalResults, paginatedResults, "Поиск выполнен успешно.");
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
