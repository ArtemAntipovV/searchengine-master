package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MorphologyServiceImpl implements MorphologyService {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LuceneMorphology luceneMorphology;
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
    private final List<String> wordBaseForms = List.of("и|СОЮЗ", "или|СОЮЗ");


    public MorphologyServiceImpl(LuceneMorphology luceneMorphology, LemmaRepository lemmaRepository, IndexRepository indexRepository) {
        this.luceneMorphology = luceneMorphology;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }


    @Override
    @Transactional
    public HashMap<String, Integer> collectLemmas(String text) {
        HashMap<String, Integer> lemmas = new HashMap<>();

        if (text == null || text.isBlank()) {
            System.out.println("Текст для лемматизации пуст.");
            return lemmas;
        }

        String cleanText = text.toLowerCase(Locale.ROOT).replaceAll("[^а-яё\\s]", "");
        System.out.println("Очищенный текст: " + cleanText);

        String[] words = cleanText.split("\\s+");

        for (String word : words) {
            if (word.isBlank()) continue;

            System.out.println("Processing word: " + word);
            try {
                List<String> normalForms = luceneMorphology.getNormalForms(word);
                for (String normalForm : normalForms) {
                    lemmas.put(normalForm, lemmas.getOrDefault(normalForm, 0) + 1);
                }
            } catch (Exception e) {
                System.out.println("Ошибка обработки слова: " + word + " - " + e.getMessage());
            }
        }

        System.out.println("Обнаруженные леммы: " + lemmas);
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
            try {
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


                List<Index> existingIndexes = indexRepository.findByLemmaAndPage(lemma, page);
                if (existingIndexes.isEmpty()) {
                    Index index = new Index();
                    index.setPage(page);
                    index.setLemma(lemma);
                    index.setRank(count);
                    indexRepository.save(index);
                } else {
                    for (Index index : existingIndexes) {
                        index.setRank(index.getRank() + count);
                        indexRepository.save(index);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    @Transactional
    public void processSite(Site site) {
        List<Page> pages = site.getPages();
        for (Page page : pages) {
            processOnePage(page);
        }
    }

    @Override
    public List<String> getWords(String text) {
        return Arrays.stream(text.split("\\P{IsAlphabetic}+"))
                .filter(word -> !word.isBlank())
                .collect(Collectors.toList());
    }

    @Override
    public boolean isNotWord(List<String> words) {
        return words.stream()
                .anyMatch(word -> word.matches(".*(СОЮЗ|МЕЖД|ПРЕДЛ|ЧАСТ).*$"));
    }

    @Override
    public String getNormalFormsWords(String word) {
        try {
            List<String> normalForms = luceneMorphology.getNormalForms(word);
            for (String normalForm : normalForms) {
                List<String> wordInfo = luceneMorphology.getMorphInfo(normalForm);
                if (!anyWordBaseBelongToParticle(wordInfo)) {
                    return normalForm;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean checkString(String text) {
        return text != null && !text.isBlank();
    }

    // Реализация метода findLemmasByName
    @Override
    @Transactional
    public List<Lemma> findLemmasByName(String word, Site site) {
        List<Lemma> lemmas = lemmaRepository.findByLemmaAndSite(word, site);
        return lemmas.isEmpty() ? Collections.emptyList() : lemmas;
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }

    private boolean hasParticleProperty(String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }
}
