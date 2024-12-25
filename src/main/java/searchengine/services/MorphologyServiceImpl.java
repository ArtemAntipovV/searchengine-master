package searchengine.services;


import org.apache.lucene.morphology.LuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;


import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class MorphologyServiceImpl implements MorphologyService {

    private Lemma lemma;
    private Index index;
    private final LuceneMorphology luceneMorphology;
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
    List<String> wordBaseForms = List.of("и|СОЮЗ", "или|СОЮЗ");
    private  LemmaRepository lemmaRepository;

    public MorphologyServiceImpl(LuceneMorphology luceneMorphology) {
        this.luceneMorphology = luceneMorphology;
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
    public void processOnePage(Page page) {
        String text = page.getContent();
        HashMap<String, Integer> lemmas = collectLemmas(text);
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemma = entry.getKey();
            Integer count = entry.getValue();
        }
    }

    @Override
    public void processSite(Site site) {
        List<Page> pages = site.getPages();
        for (Page page : pages) {
            processOnePage(page);
        }
    }

    @Override
    public List<Lemma> findLemmasByName(String word, Site site) {
        return List.of();
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

    private String[] arrayContainsRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-я\\s])", " ")
                .trim()
                .split("\\s+");
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
