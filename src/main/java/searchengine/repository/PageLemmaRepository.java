package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.PageLemma;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;
import java.util.Optional;


@Repository
public interface PageLemmaRepository extends JpaRepository<PageLemma, Integer> {

    @Query("SELECT i.rank FROM PageLemma i WHERE i.page.id = :pageId AND i.lemma.lemma = :lemma")
    Optional<Float> getRankForPage(@Param("pageId") int pageId, @Param("lemma") String lemma);



    @Query("SELECT i FROM PageLemma i WHERE i.lemma = :lemma AND i.page = :page")
    List<PageLemma> findByLemmaAndPage(@Param("lemma") Lemma lemma, @Param("page") Page page);


    @Query("SELECT l.lemma FROM Lemma l WHERE l.lemma IN :lemmas")
    List<String> findExistingLemmas(@Param("lemmas") List<String> lemmas);
}
