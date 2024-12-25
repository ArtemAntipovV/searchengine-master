package searchengine.dto.indexing;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SearchResultItem {
    private String url;
    private String siteName;
    private String title;
    private String snippet;
    private double relevance;

}
