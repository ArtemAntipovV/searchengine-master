package searchengine.model;


import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.util.List;


@Getter
@Setter
@Entity
@Table
public class Lemma {

    @Column(name = "id", nullable = false)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="site_id", nullable=false, foreignKey = @ForeignKey(name = "FK_lemma_site"))
    private Site site;

    @Column(name = "lemma", columnDefinition = "VARCHAR (255)", nullable = false)
    private String lemma;

    @Column(nullable = false)
    private Integer frequency;

    @OneToMany(mappedBy = "lemma", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Index> indexes;

    @ManyToMany(mappedBy = "lemmas")
    private List<Page> pages;

    @Override
    public String toString() {
        return "Lemma{id=" + id + ", lemma='" + lemma + "', frequency=" + frequency + "}";
    }

}
