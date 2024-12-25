package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.HtmlUtils;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.net.URL;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IndexPageImpl implements IndexPageService {


    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final MorphologyService morphologyService;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    @Override
    @Transactional
    public boolean indexPage(String url) {
        Site site = findSiteForUrl(url);
        if (site == null) {
            return false; // URL за пределами сайтов
        }
        try {
            // Получение относительного пути из полного URL
            String path = new URL(url).getPath();
            if (path.isBlank()) {
                path = "/";
            }

            // Проверяем существование страницы
            List<Page> existingPage = pageRepository.findByPathAndSiteId(path, site.getId());
            Page page;

            if (!existingPage.isEmpty()) {
                if (existingPage.size() > 1) {
                    System.out.println("Обнаружены дубликаты для path: " + path + " и site_id: " + site.getId());
                    handleDuplicatePages(existingPage);
                }
                page = existingPage.get(0);
                page.setContent(HtmlUtils.getHtmlContent(url));
                page.setCode(200);
                pageRepository.save(page);
                System.out.println("Обновлена существующая страница: " + url);
            } else {
                // Если страницы нет, создаём новую
                page = new Page();
                page.setSite(site);
                page.setPath(path);
                page.setUrl(url);
                page.setContent(HtmlUtils.getHtmlContent(url));
                page.setCode(200);
                pageRepository.save(page);
                System.out.println("Создана новая страница: " + url);
            }

            // Преобразуем HTML-контент в леммы и сохраняем их
            HashMap<String, Integer> lemmas = morphologyService.collectLemmas(page.getContent());
            System.out.println("Количество лемм: " + lemmas.size());

            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                String lemmaText = entry.getKey();
                Integer count = entry.getValue();

                // Обновляем или добавляем леммы
                List<Lemma> lemmaList = lemmaRepository.findByLemmaAndSites(lemmaText, Collections.singletonList(site));
                Lemma lemma;
                if (lemmaList.isEmpty()) {
                    lemma = new Lemma();
                    lemma.setSite(site);
                    lemma.setLemma(lemmaText);
                    lemma.setFrequency(0);
                } else {
                    lemma = lemmaList.get(0);
                }
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.save(lemma);

                // Создаем связь между страницей и леммой
                Index index = new Index();
                index.setPage(page);
                index.setLemma(lemma);
                index.setRank(count); // Количество упоминаний леммы на странице
                indexRepository.save(index);
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private Site findSiteForUrl(String url) {
        String normalizedUrl = url.replaceFirst("^https?://(www\\.)?", "").replaceAll("/$", "");
        return siteRepository.findAll().stream()
                .filter(site -> normalizedUrl.startsWith(site.getUrl().replaceFirst("^https?://(www\\.)?", "").replaceAll("/$", "")))
                .findFirst()
                .orElse(null);
    }

    private void handleDuplicatePages(List<Page> duplicatePages) {
        duplicatePages.sort(Comparator.comparing(Page::getId));
        Page primaryPage = duplicatePages.get(0); // Оставляем запись с минимальным ID
        for (int i = 1; i < duplicatePages.size(); i++) {
            Page duplicate = duplicatePages.get(i);
            pageRepository.delete(duplicate);
            System.out.println("Удален дубликат страницы с ID: " + duplicate.getId());
        }
    }
}

