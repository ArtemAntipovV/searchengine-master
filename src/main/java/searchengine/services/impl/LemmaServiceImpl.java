package searchengine.services.impl;

import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.PageLemma;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageLemmaRepository;
import searchengine.services.interfaces.LemmaService;

import javax.transaction.Transactional;
import java.util.*;

@Service
public class LemmaServiceImpl implements LemmaService {

    private final LemmaRepository lemmaRepository;
    private final PageLemmaRepository pageLemmaRepository;
    private final MorphologyProcessorImpl morphologyProcessorImpl;

    public LemmaServiceImpl(LemmaRepository lemmaRepository, PageLemmaRepository pageLemmaRepository, MorphologyProcessorImpl morphologyProcessorImpl) {
        this.lemmaRepository = lemmaRepository;
        this.pageLemmaRepository = pageLemmaRepository;
        this.morphologyProcessorImpl = morphologyProcessorImpl;
    }

    @Override
    @Transactional
    public HashMap<String, Integer> collectLemmas(String text) {
        HashMap<String, Integer> lemmas = new HashMap<>();

        if (text == null || text.isBlank()) {
            System.out.println("Текст для лемматизации пуст.");
            return lemmas;
        }

        String cleanText = text.toLowerCase(Locale.ROOT).replaceAll("[^а-яёa-z\\s]", "").trim();
        System.out.println("Очищенный текст: " + cleanText);

        List<String> words = morphologyProcessorImpl.getWords(cleanText);

        for (String word : words) {
            System.out.println("Обрабатываем слово: " + word);
            try {
                List<String> normalForms = morphologyProcessorImpl.getNormalFormsWords(word);
                for (String normalForm : normalForms) {
                    lemmas.put(normalForm, lemmas.getOrDefault(normalForm, 0) + 1);
                }
            } catch (Exception e) {
                System.out.println("Ошибка обработки слова: " + word + " - " + e.getMessage());
            }
        }

        return lemmas;
    }

    @Override
    @Transactional
    public void processOnePage(Page page) {
        String text = page.getContent();
        HashMap<String, Integer> lemmas = collectLemmas(text);

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemmaText = entry.getKey();
            Integer count = entry.getValue();

            List<Lemma> lemmasList = lemmaRepository.findByLemmaAndSite(lemmaText, page.getSite());

            Lemma lemma;
            if (lemmasList.isEmpty()) {
                lemma = new Lemma();
                lemma.setSite(page.getSite());
                lemma.setLemma(lemmaText);
                lemma.setFrequency(count);
                lemmaRepository.save(lemma);
            } else {
                lemma = lemmasList.get(0);
                lemma.setFrequency(lemma.getFrequency() + count);
                lemmaRepository.save(lemma);
            }

            List<PageLemma> existingPageLemmas = pageLemmaRepository.findByLemmaAndPage(lemma, page);
            if (existingPageLemmas.isEmpty()) {
                PageLemma pageLemma = new PageLemma();
                pageLemma.setPage(page);
                pageLemma.setLemma(lemma);
                pageLemma.setRank(count);
                pageLemmaRepository.save(pageLemma);
            }
        }
    }

    @Override
    @Transactional
    public void processSite(Site site) {
        for (Page page : site.getPages()) {
            processOnePage(page);
        }
    }

    @Override
    public List<Lemma> findLemmasByName(String word, Site site) {
        return lemmaRepository.findByLemmaAndSite(word, site);
    }
}
