package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;

import java.util.Optional;


@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {

    @Query("SELECT i.rank FROM Index i WHERE i.page.id = :pageId AND i.lemma.lemma = :lemma")
    Optional<Double> getRankForPage(@Param("pageId") int pageId, @Param("lemma") String lemma);
}
