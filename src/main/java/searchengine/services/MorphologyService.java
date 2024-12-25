package searchengine.services;


import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.HashMap;
import java.util.List;

public interface MorphologyService {
    HashMap<String, Integer> collectLemmas(String text);
    List<String> getWords(String text);
    boolean isNotWord(List<String> words);
    void processOnePage(Page page);
    void processSite(Site site);
    List<Lemma> findLemmasByName(String word, Site site);
    String getNormalFormsWords(String word);
    boolean checkString(String text);
}
