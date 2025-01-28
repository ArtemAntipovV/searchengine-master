package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResultItem;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageLemmaRepository;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelevanceService {

    private final EntityManager entityManager;
    private final PageLemmaRepository pageLemmaRepository;
    private final SnippetService snippetService;

    public Map<String, Integer> filterFrequentLemmas(Map<String, Integer> lemmas, List<Site> sites) {
        List<String> existingLemmas = pageLemmaRepository.findExistingLemmas(new ArrayList<>(lemmas.keySet()));
        return lemmas.entrySet().stream()
                .filter(entry -> existingLemmas.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<Page> findPagesByLemmas(List<Site> sites, List<String> lemmas) {
        List<Integer> siteIds = sites.stream().map(Site::getId).collect(Collectors.toList());
        String hql = "SELECT DISTINCT p FROM Page p " +
                "JOIN p.pageLemmas pl " +
                "JOIN pl.lemma l " +
                "WHERE l.lemma IN :lemmas AND p.site.id IN :siteIds";

        List<Page> pages = entityManager.createQuery(hql, Page.class)
                .setParameter("lemmas", lemmas)
                .setParameter("siteIds", siteIds)
                .getResultList();

        return pages.stream()
                .filter(page -> containsLemmaInContent(page.getContent(), lemmas))
                .collect(Collectors.toList());
    }

    private boolean containsLemmaInContent(String content, List<String> lemmas) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        String plainText = Jsoup.parse(content).text().toLowerCase();
        return lemmas.stream().anyMatch(lemma -> plainText.contains(lemma.toLowerCase()));
    }

    public List<SearchResultItem> buildSearchResults(List<Page> pages, Map<String, Integer> queryLemmas, int offset, int limit) {
        Set<String> lemmas = queryLemmas.keySet();
        double maxRelevance = pages.stream()
                .mapToDouble(page -> calculateRelevance(page, lemmas))
                .max()
                .orElse(1.0);

        return pages.stream()
                .map(page -> {
                    double relevance = calculateRelevance(page, lemmas);
                    if (relevance == 0.0) return null;

                    String snippet = snippetService.buildSnippet(Jsoup.parse(page.getContent()).text(), lemmas);
                    return new SearchResultItem(
                            page.getSite().getUrl(),
                            page.getSite().getName(),
                            page.getUrl(),
                            Jsoup.parse(page.getContent()).title(),
                            snippet,
                            relevance / maxRelevance
                    );
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SearchResultItem::getRelevance).reversed())
                .collect(Collectors.toList());
    }

    private double calculateRelevance(Page page, Set<String> lemmas) {
        String content = Jsoup.parse(page.getContent()).text().toLowerCase();
        if (lemmas.stream().noneMatch(lemma -> content.contains(lemma.toLowerCase()))) {
            return 0.0;
        }
        return lemmas.stream()
                .mapToDouble(lemma -> pageLemmaRepository.getRankForPage(page.getId(), lemma)
                        .stream()
                        .findFirst()
                        .orElse(0.0F))
                .sum();
    }
}
