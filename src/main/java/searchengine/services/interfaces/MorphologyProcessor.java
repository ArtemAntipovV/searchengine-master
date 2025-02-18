package searchengine.services.interfaces;


import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.HashMap;
import java.util.List;

public interface MorphologyProcessor {
    List<String> getWords(String text);
    List<String> getNormalFormsWords(String word);
    boolean checkString(String text);
}
