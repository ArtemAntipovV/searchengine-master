package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.PageLemma;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Map;
import java.util.Optional;


@Repository
public interface PageLemmaRepository extends JpaRepository<PageLemma, Integer> {

    @Query("SELECT i.rank FROM PageLemma i WHERE i.page.id = :pageId AND i.lemma.lemma = :lemma")
    Optional<Float> getRankForPage(@Param("pageId") int pageId, @Param("lemma") String lemma);

    @Query("SELECT i FROM PageLemma i WHERE i.lemma = :lemma AND i.page = :page")
    List<PageLemma> findByLemmaAndPage(@Param("lemma") Lemma lemma, @Param("page") Page page);


    @Query("SELECT COUNT(p) FROM Page p WHERE p.site IN :sites")
    long countTotalPages(@Param("sites") List<Site> sites);

    @Query("SELECT pl.lemma.lemma, COUNT(DISTINCT pl.page) FROM PageLemma pl " +
            "WHERE pl.lemma.lemma IN :lemmas GROUP BY pl.lemma.lemma")
    List<Object[]> countPagesForLemmas(@Param("lemmas") List<String> lemmas);

    @Query("SELECT DISTINCT p FROM Page p " +
            "JOIN p.pageLemmas i " +
            "JOIN i.lemma l " +
            "WHERE l.lemma IN :lemmas AND p.site.id IN :siteIds")
    List<Page> findPagesByLemmasAndSites(@Param("lemmas") List<String> lemmas, @Param("siteIds") List<Integer> siteIds);

}
