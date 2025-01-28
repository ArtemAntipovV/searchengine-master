package searchengine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.persistence.*;
import javax.persistence.Index;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

@Entity
@Table(name = "page",
        uniqueConstraints = @UniqueConstraint(columnNames = {"path", "site_id"}),
        indexes = {@Index(name = "IDX_path", columnList = "path")}) // Индекс для поля path
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "path", length = 512, nullable = false)
    private String path;

    @Column(name = "status_code", nullable = false)
    private int code;

    @Column(name = "url", nullable = false, unique = true)
    private String url;

    @Lob
    @Column(name = "content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PageLemma> pageLemmas;


    @Override
    public String toString() {
        return "Page{id=" + id + ", url='" + url + "', path='" + path + "'}";
    }


}
