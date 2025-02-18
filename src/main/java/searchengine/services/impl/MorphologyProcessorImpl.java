package searchengine.services.impl;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;
import searchengine.services.interfaces.MorphologyProcessor;


import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class MorphologyProcessorImpl implements MorphologyProcessor {

    private final LuceneMorphology luceneMorphologyRu;
    private final LuceneMorphology luceneMorphologyEn;
    private static final String[] PARTICLE_NAMES = {"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ", "ARTICLE", "CONJ", "PREP"};

    public MorphologyProcessorImpl() throws IOException {
        this.luceneMorphologyRu = new RussianLuceneMorphology();
        this.luceneMorphologyEn = new EnglishLuceneMorphology();
    }

    @Override
    public List<String> getWords(String text) {
        return Arrays.stream(text.split("\\P{IsAlphabetic}+"))
                .filter(word -> !word.isBlank())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getNormalFormsWords(String word) {
        List<String> normalForms = new ArrayList<>();
        try {
            List<String> baseForms = getNormalForms(word);
            for (String baseForm : baseForms) {
                List<String> wordInfo = getMorphInfo(baseForm);

                if (anyWordBaseBelongToParticle(wordInfo)) {
                    System.out.println("Исключено слово (служебная часть речи): " + baseForm + " " + wordInfo);
                    return Collections.emptyList();
                }
                normalForms.add(baseForm);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (normalForms.isEmpty()) {
            normalForms.add(word);
        }

        return normalForms;
    }

    @Override
    public boolean checkString(String text) {
        return text != null && !text.isBlank();
    }

    private List<String> getMorphInfo(String word) {
        if (word.matches("[а-яё]+")) {
            return luceneMorphologyRu.getMorphInfo(word);
        } else if (word.matches("[a-z]+")) {
            return luceneMorphologyEn.getMorphInfo(word);
        }
        return new ArrayList<>();
    }

    private List<String> getNormalForms(String word) {
        if (word.matches("[а-яё]+")) {
            return luceneMorphologyRu.getNormalForms(word);
        } else if (word.matches("[a-z]+")) {
            return luceneMorphologyEn.getNormalForms(word);
        }
        return new ArrayList<>();
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        for (String baseForm : wordBaseForms) {
            for (String property : PARTICLE_NAMES) {
                if (baseForm.toUpperCase().contains(property)) {
                    return true;
                }
            }
        }

        for (String baseForm : wordBaseForms) {
            if (baseForm.contains("ГЛ") || baseForm.contains("ПРИЧ") || baseForm.contains("НАРЕЧ")) {
                return true;
            }
        }

        return false;
    }
}