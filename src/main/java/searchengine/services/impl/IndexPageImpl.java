package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import searchengine.config.HtmlUtils;
import searchengine.model.PageLemma;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import searchengine.repository.PageLemmaRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.IndexPageService;
import searchengine.services.interfaces.MorphologyService;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.net.URL;
import java.util.*;


@Service
@RequiredArgsConstructor
public class IndexPageImpl implements IndexPageService {

    @PersistenceContext
    private EntityManager entityManager;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final MorphologyService morphologyService;
    private final LemmaRepository lemmaRepository;
    private final PageLemmaRepository pageLemmaRepository;


    @Override
    @Transactional
    public boolean indexPage(String url) {
        try {
            System.out.println("Начата индексация страницы: " + url);

            Site site = findSiteForUrl(url);
            if (site == null) {
                System.out.println("URL за пределами сайтов: " + url);
                return false;
            }

            String path = new URL(url).getPath();
            String finalPath = path.isBlank() ? "/" : path;

            Page page = pageRepository.findByPathAndSiteId(finalPath, site.getId())
                    .stream().findFirst().orElseGet(() -> {
                        Page newPage = new Page();
                        newPage.setSite(site);
                        newPage.setPath(finalPath);
                        newPage.setUrl(url);
                        try {
                            newPage.setContent(HtmlUtils.getHtmlContent(url));
                        } catch (IOException e) {
                            throw new RuntimeException("Ошибка при получении контента для URL: " + url, e);
                        }
                        newPage.setCode(200);
                        pageRepository.save(newPage);
                        return newPage;
                    });

            HashMap<String, Integer> lemmas = morphologyService.collectLemmas(page.getContent());
            System.out.println("Обнаруженные леммы: " + lemmas);

            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                String lemmaText = entry.getKey();
                Integer count = entry.getValue();


                Lemma lemma = lemmaRepository.findByLemma(lemmaText).stream()
                        .findFirst()
                        .orElseGet(() -> {
                            Lemma newLemma = new Lemma();
                            newLemma.setLemma(lemmaText);
                            newLemma.setSite(site);
                            newLemma.setFrequency(0);
                            lemmaRepository.save(newLemma);
                            return newLemma;
                        });

                lemma.setFrequency(lemma.getFrequency() + count);
                lemmaRepository.save(lemma);

                PageLemma existingPageLemma = pageLemmaRepository.findByLemmaAndPage(lemma, page)
                        .stream()
                        .findFirst()
                        .orElse(new PageLemma());

                existingPageLemma.setPage(page);
                existingPageLemma.setLemma(lemma);
                existingPageLemma.setRank(existingPageLemma.getRank() + count);

                pageLemmaRepository.save(existingPageLemma);  // Сохраняем или обновляем индекс
                System.out.println("Индекс обновлен для леммы: " + lemmaText + " на странице: " + page.getId());
            }

            System.out.println("Индексация успешно завершена для страницы: " + url);
            return true;

        } catch (Exception e) {
            System.out.println("Ошибка при индексации страницы: " + url);
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

}

