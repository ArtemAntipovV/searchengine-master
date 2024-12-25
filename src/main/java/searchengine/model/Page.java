package searchengine.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.persistence.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

@Entity
@Table( name = "page",
        uniqueConstraints = @UniqueConstraint(columnNames = {"path", "site_id"}))
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Page {

    @Column(name = "id", nullable = false)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "path",columnDefinition = "TEXT", nullable = false)
    private String path;

    @Column(name = "status_code", nullable = false)
    private int code;

    @Column(name = "url", nullable = false, unique = true)
    private String url;

    @Lob
    @Column(name= "content", columnDefinition = "MEDIUMTEXT", nullable = false)
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
        this.path = new URL(url).getPath(); // URL страницы
        this.content = content; // Содержимое страницы
        this.site = site; // Сайт, к которому относится страница
        this.code = responseCode; // HTTP код ответа
    }

    public String getTitle() {
        if (content == null || content.isEmpty()) {
            return "Без заголовка";
        }

        Document doc = Jsoup.parse(content);
        String title = doc.title();
        return title != null && !title.isEmpty() ? title : "Без заголовка";
    }

}
