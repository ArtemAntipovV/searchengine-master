package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import java.util.List;



@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {

    @Query("SELECT i.rank FROM Index i WHERE i.page.id = :pageId AND i.lemma.lemma = :lemma")
    List<Float> getRankForPage(@Param("pageId") int pageId, @Param("lemma") String lemma);



    @Query("SELECT i FROM Index i WHERE i.lemma = :lemma AND i.page = :page")
    List<Index> findByLemmaAndPage(@Param("lemma") Lemma lemma, @Param("page") Page page);


}
