package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;


@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {



    Optional<Page> findBySiteIdAndUrl(Integer siteId, String url);
    
    @Query("SELECT p FROM Page p WHERE p.path = :path AND p.site.id = :siteId")
    List<Page> findByPathAndSiteId(@Param("path") String path, @Param("siteId") Integer siteId);

    @Query("SELECT COUNT(p) FROM Page p")
    int countAllPages();

    @Query("SELECT COUNT(p) FROM Page p WHERE p.site.id = :siteId")
    int countBySiteId(@Param("siteId") Integer siteId);


    List<Page> findAllById(Iterable<Integer> ids);

    @Query("SELECT COUNT(p) FROM Page p WHERE p.site = :site")
    int countBySite(@Param("site") Site site);

    @Query("SELECT DISTINCT p FROM Page p " +
            "JOIN p.pageLemmas i " +
            "JOIN i.lemma l " +
            "WHERE l.lemma IN :lemmas AND p.site.id IN :siteIds")
    List<Page> findPagesByLemmasAndSites(@Param("lemmas") List<String> lemmas, @Param("siteIds") List<Integer> siteIds);

    @Query("SELECT p FROM Page p WHERE p.site.id = :siteId")
    List<Page> findAllBySiteId(@Param("siteId") Integer siteId);

}
