package searchengine.services.interfaces;

import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.HashMap;
import java.util.List;

public interface LemmaService {

    @Transactional
    HashMap<String, Integer> collectLemmas(String text);


    @Transactional
    void processOnePage(Page page);

    @Transactional
    void processSite(Site site);
    List<Lemma> findLemmasByName(String word, Site site);
}
