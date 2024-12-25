package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final Random random = new Random();
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;



    @Override
    public StatisticsResponse getStatistics() {
        // Формируем общую статистику
        TotalStatistics totalStatistics = new TotalStatistics();
        List<Site> sites = siteRepository.findAll();

        int totalPages = pageRepository.countAllPages();
        int totalLemmas = lemmaRepository.countAllLemmas();
        boolean isIndexing = IndexingServiceImpl.isIndexing;

        totalStatistics.setSites(sites.size());
        totalStatistics.setPages(totalPages);
        totalStatistics.setLemmas(totalLemmas);
        totalStatistics.setIndexing(isIndexing);

        // Формируем детализированную статистику
        List<DetailedStatisticsItem> detailedStatistics = new ArrayList<>();
        for (Site site : sites) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(site.getUrl());
            item.setName(site.getName());
            item.setStatus(site.getStatusEnum().toString());
            item.setStatusTime(site.getTime().toEpochSecond(ZoneOffset.UTC));
            item.setError(site.getError());
            item.setPages(pageRepository.countBySiteId(site.getId()));
            item.setLemmas(lemmaRepository.countBySiteId(site.getId()));
            detailedStatistics.add(item);
        }

        // Собираем полную статистику
        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setTotal(totalStatistics);
        statisticsData.setDetailed(detailedStatistics);

        // Формируем ответ
        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(statisticsData);

        return response;
    }
}
