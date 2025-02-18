package searchengine.services.interfaces;

import searchengine.dto.search.SearchResultsResponse;

public interface SearchService {

    SearchResultsResponse search(String query, String siteUrl, Integer offset, Integer limit);
}
