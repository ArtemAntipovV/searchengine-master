package searchengine.model;


import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "page_lemma", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"lemma_id", "page_id"})
})
public class Index {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="page_id", nullable=false, foreignKey = @ForeignKey(name = "FK_index_page"))
    private Page page;

    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="lemma_id", nullable=false, foreignKey = @ForeignKey(name = "FK_index_lemma"))
    private Lemma lemma;

    @Column(name="rank_value", nullable = false)
    private float rank;

    @Override
    public String toString() {
        return "Index{" +
                "id=" + id +
                ", pageId=" + (page != null ? page.getId() : "null") +
                ", lemmaId=" + (lemma != null ? lemma.getId() : "null") +
                ", rank=" + rank +
                '}';
    }


}
