package searchengine.dto.indexing;


import lombok.Data;

import java.util.List;

@Data
public class SearchResultsResponse {
    private boolean result;
    private int count;
    private List<SearchResultItem> data;
    private String message;

    public SearchResultsResponse(boolean result, int count, List<SearchResultItem> data, String message) {
        this.result = result;
        this.count = count;
        this.data = data;
        this.message = message;
    }
}
