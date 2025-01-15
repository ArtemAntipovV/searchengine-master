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

    @ManyToMany
    @JoinTable(
            name = "page_lemma",
            joinColumns = @JoinColumn(name = "page_id"),
            inverseJoinColumns = @JoinColumn(name = "lemma_id")
    )
    private List<Lemma> lemmas;

    public Page(String url, String content, Site site, int responseCode) throws MalformedURLException {
        this.url = url;
        this.path = new URL(url).getPath();
        this.content = content;
        this.site = site;
        this.code = responseCode;
    }

    public String getTitle() {
        if (content == null || content.isEmpty()) {
            return "Без заголовка";
        }

        Document doc = Jsoup.parse(content);
        String title = doc.title();
        return title != null && !title.isEmpty() ? title : "Без заголовка";
    }

    @Override
    public String toString() {
        return "Page{id=" + id + ", url='" + url + "', path='" + path + "'}";
    }


}
