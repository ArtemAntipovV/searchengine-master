package searchengine.config;

import lombok.Data;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.IndexingServiceImpl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RecursiveTask;

@Data
public class CrawlPageTask extends RecursiveTask<Void> {

    private SiteRepository siteRepository;
    private PageRepository pageRepository;
    private String url;
    private Site site;
    private List<String> newLinks;
    private final String userAgent;
    private final String referrer;
    private final Map<String, Boolean> visitedUrls;
    private IndexingServiceImpl indexingServiceImpl;

    public CrawlPageTask(String url, Site site, PageRepository pageRepository, SiteRepository siteRepository, Map<String, Boolean> visitedUrls, String userAgent, String referrer) {
        this.url = url;
        this.site = site;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.visitedUrls = visitedUrls;
        this.userAgent = userAgent;
        this.referrer = referrer;
        this.newLinks = new ArrayList<>();
    }

    @Override
    protected Void compute() {
        try {
            if (!IndexingServiceImpl.isIndexing) {
                return null;
            }

            int responseCode = Jsoup.connect(url).ignoreHttpErrors(true).execute().statusCode();
            savePage(url, site, responseCode);


            Elements elements = getElements(url);
            newLinks = extractNewLinks(elements);

            List<CrawlPageTask> subTasks = new ArrayList<>();
            for (String link : newLinks) {
                if (!visitedUrls.containsKey(link) && IndexingServiceImpl.isIndexing) {
                    visitedUrls.put(link, true);
                    CrawlPageTask task = new CrawlPageTask(link, site, pageRepository, siteRepository, visitedUrls, userAgent, referrer);
                    task.fork();
                    subTasks.add(task);
                }
            }

            for (CrawlPageTask task : subTasks) {
                task.join();
            }

        } catch (Exception e) {
            updateSiteStatus(site, Status.FAILED, e.getMessage());
        }
        return null;
    }

    public Elements getElements(String url) throws Exception {
        Document doc = Jsoup.connect(url)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .maxBodySize(0)
                .timeout(10_000)
                .header("User-Agent", userAgent)
                .referrer(referrer)
                .get();
        return doc.select("a[href]");
    }

    public List<String> extractNewLinks(Elements elements) {
        List<String> links = new ArrayList<>();
        for (org.jsoup.nodes.Element element : elements) {
            String href = element.attr("abs:href");
            if (!href.isEmpty() && href.startsWith("http")) {
                links.add(href);
            }
        }
        return links;
    }

    @Transactional
    private void savePage(String pageUrl, Site site, int responseCode) {
        try {
            Optional<Page> existingPage = pageRepository.findBySiteIdAndUrl(site.getId(), pageUrl);

            if (existingPage.isEmpty()) {
                Page newPage = new Page();
                newPage.setSite(site);
                newPage.setUrl(pageUrl);
                newPage.setPath(getPathFromUrl(pageUrl));
                newPage.setContent(fetchContent(pageUrl));
                newPage.setCode(responseCode);

                pageRepository.save(newPage);
                System.out.println("Страница сохранена: " + pageUrl);
            } else {
                System.out.println("Страница уже существует: " + pageUrl);
            }
        } catch (Exception e) {
            updateSiteStatus(site, Status.FAILED, "Ошибка сохранения страницы: " + pageUrl);
        }
    }

    private void updateSiteStatus(Site site, Status newStatus, String errorMessage) {
        boolean isStatusChanged = site.getStatusEnum() != newStatus;
        boolean isErrorChanged = errorMessage != null && !errorMessage.equals(site.getError());

        if (isStatusChanged || isErrorChanged) {
            site.setStatusEnum(newStatus);
            if (isStatusChanged) {
                site.setTime(LocalDateTime.now());
            }
            if (isErrorChanged) {
                site.setError(errorMessage);
            }
            siteRepository.save(site);
            System.out.println("Site обновлен: ID = " + site.getId() + ", Status = " + site.getStatusEnum());
        } else {
            System.out.println("Site не требует обновления: ID = " + site.getId());
        }
    }

    private String getPathFromUrl(String url) {
        try {
            return new URL(url).getPath();
        } catch (Exception e) {
            throw new RuntimeException("Некорректный URL: " + url, e);
        }
    }

    private String fetchContent(String pageUrl) {
        try {
            return Jsoup.connect(pageUrl).get().html();
        } catch (Exception e) {
            System.out.println("Ошибка получения содержимого для: " + pageUrl);
            return "";
        }
    }
}
