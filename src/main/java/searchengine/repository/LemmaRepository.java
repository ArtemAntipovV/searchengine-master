package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import searchengine.model.Lemma;
import searchengine.model.Site;
import java.util.List;



@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma AND l.site IN :sites")
    List<Lemma> findByLemmaAndSites(@Param("lemma") String lemma, @Param("sites") List<Site> sites);

    @Query("SELECT COUNT(l) FROM Lemma l")
    int countAllLemmas();

    @Query("SELECT COUNT(l) FROM Lemma l WHERE l.site.id = :siteId")
    int countBySiteId(@Param("siteId") Integer siteId);

    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma")
    List<Lemma> findByLemma(@Param("lemma") String lemma);


    @Query("SELECT COUNT(l) FROM Lemma l WHERE l.lemma = :lemma AND l.site IN :sites")
    int countByLemmaAndSites(@Param("lemma") String lemma, @Param("sites") List<Site> sites);

    @Query("SELECT l.lemma, COUNT(l.id) FROM Lemma l WHERE l.lemma IN :lemmas GROUP BY l.lemma")
    List<Object[]> findLemmaFrequencies(@Param("lemmas") List<String> lemmas);

    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma AND l.site = :site")
    List<Lemma> findByLemmaAndSite(@Param("lemma") String lemma, @Param("site") Site site);


}
