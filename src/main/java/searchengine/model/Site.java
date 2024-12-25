package searchengine.model;

import lombok.Data;


import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity(name = "site")
@Data
public class Site {

    @Column(name = "id", nullable = false)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('INDEXING','INDEXED', 'FAILED')", nullable = false)
    private Status statusEnum;

    @Column(name = "status_time",  nullable = false)
    private LocalDateTime time;

    @Column(name = "last_error", nullable = false)
    private String error = null;


    @Column(columnDefinition = "VARCHAR (255)", nullable = false)
    private String url;

    @Column(columnDefinition = "VARCHAR (255)", nullable = false)
    private String name;

    @OneToMany(mappedBy = "site", fetch = FetchType.LAZY)
    private List<Page> pages;

    public Site() {
    }

    public Site(String url, LocalDateTime time, String name) {
        this.url = url;
        this.time = time;
        this.name = name;
        this.statusEnum = Status.INDEXING;
        this.error = "";
    }
}
