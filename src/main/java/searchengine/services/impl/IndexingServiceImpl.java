package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.*;

import searchengine.dto.search.Response;


import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.IndexPageService;
import searchengine.services.interfaces.IndexingService;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.IOException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    private final SitesList sitesList;

    public static volatile boolean isIndexing;
    private final UserAgent userAgentConfig;

    @PersistenceContext
    private EntityManager entityManager;

    private final Map<String, Boolean> visitedUrls = new ConcurrentHashMap<>();

    private final ForkJoinPool pool = new ForkJoinPool(4);
    ;
    private ExecutorService executorService;

    @Autowired
    private IndexPageService indexPageService;

    private CrawlPageTask crawlPageTask;

    @Override
    @Transactional
    public Response startIndexing() {
        System.out.println("siteRepository: " + siteRepository);

        if (!canStartIndexing()) {
            return new Response(false, "Индексация уже запущена");
        }
        isIndexing = true;
        this.executorService = Executors.newFixedThreadPool(5);

        try {
            for (SiteConfig siteConfig : sitesList.getSites()) {
                executorService.submit(() -> {
                    try {

                        Optional<Site> siteOptional = siteRepository.findByUrl(siteConfig.getUrl());
                        Site site;

                        if (siteOptional.isEmpty()) {
                            site = new Site(siteConfig.getUrl(), LocalDateTime.now(), siteConfig.getName());
                            site.setStatusEnum(Status.INDEXING);
                            site = siteRepository.save(site);
                        } else {
                            site = siteOptional.get();
                            deleteExistingDataForSite(site);
                            site.setStatusEnum(Status.INDEXING);
                            site.setTime(LocalDateTime.now());
                            siteRepository.save(site);
                            System.out.println("Сайт уже существует, старые данные удалены: " + site.getUrl());
                        }


                        crawlPages(siteConfig.getUrl(), site);


                        site.setStatusEnum(Status.INDEXED);
                        site.setTime(LocalDateTime.now());
                        siteRepository.save(site);
                        System.out.println("Индексация завершена для сайта: " + site.getUrl());
                    } catch (Exception e) {
                        e.printStackTrace();
                        Optional<Site> failedSite = siteRepository.findByUrl(siteConfig.getUrl());
                        failedSite.ifPresent(site -> {
                            site.setStatusEnum(Status.FAILED);
                            site.setError("Ошибка индексации: " + e.getMessage());
                            siteRepository.save(site);
                            System.out.println("Ошибка индексации для сайта: " + site.getUrl());
                        });
                    }
                });
            }
        } finally {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.MINUTES)) {
                    System.out.println("Не удалось завершить индексацию вовремя.");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            isIndexing = false;
        }
        return new Response(true, "Индексация успешно завершена");
    }

    private synchronized boolean canStartIndexing() {
        return !isIndexing;
    }


    private void crawlPages(String url, Site site) throws IOException {
        String userAgent = userAgentConfig.getUser(0);
        String referrer = userAgentConfig.getReferrer();


        Optional<Site> siteOptional = siteRepository.findByUrl(site.getUrl());
        if (siteOptional.isEmpty()) {
            System.out.println("Сайт не найден в базе данных: " + site.getUrl());
            return;
        }

        Site existingSite = siteOptional.get();
        System.out.println("Обнаружен существующий сайт: " + existingSite.getUrl());


        Queue<String> urlsToVisit = new LinkedList<>();
        urlsToVisit.add(url);

        while (!urlsToVisit.isEmpty()) {
            String currentUrl = urlsToVisit.poll();

            if (visitedUrls.containsKey(currentUrl)) {
                continue;
            }
            visitedUrls.put(currentUrl, true);

            try {
                boolean indexed = indexPageService.indexPage(currentUrl);
                if (!indexed) {
                    System.out.println("Ошибка индексации страницы: " + currentUrl);
                    continue;
                }

                CrawlPageTask crawlPageTask = new CrawlPageTask(currentUrl, existingSite, pageRepository, siteRepository, visitedUrls, userAgent, referrer);
                Elements elements = crawlPageTask.getElements(currentUrl);
                List<String> newLinks = crawlPageTask.extractNewLinks(elements);

                for (String link : newLinks) {
                    if (!visitedUrls.containsKey(link)) {
                        urlsToVisit.add(link);
                    }
                }
            } catch (Exception e) {
                System.out.println("Ошибка при обработке страницы: " + currentUrl);
                e.printStackTrace();
            }
        }
    }

    private void deleteExistingDataForSite(Site site) {
        System.out.println("Удаляем старые данные для сайта: " + site.getUrl() + " с ID: " + site.getId());
        pageRepository.deleteAll(pageRepository.findAllBySiteId(site.getId()));
        System.out.println("Старые страницы удалены для сайта: " + site.getUrl());
    }


    @Override
    public Response stopIndexing() {
        if (!isIndexing) {
            return new Response(false, "Индексация не запущена");
        }

        try {
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdownNow();
                System.out.println("Все потоки индексации остановлены.");
            }

            List<Site> sitesInProgress = siteRepository.findByStatusEnum(Status.INDEXING);
            for (Site site : sitesInProgress) {
                site.setStatusEnum(Status.FAILED);
                site.setError("Индексация остановлена пользователем");
                siteRepository.save(site);
            }
            isIndexing = false;
            executorService = null;
            visitedUrls.clear();
            return new Response(true, "Индексация остановлена успешно");
        } catch (Exception e) {
            e.printStackTrace();
            return new Response(false, "Ошибка при остановке индексации: " + e.getMessage());
        }
    }

    @Override
    public boolean isIndexing() {
        return isIndexing;
    }

}
