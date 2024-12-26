package searchengine.services;


import searchengine.dto.search.Response;


public interface IndexingService {
    Response startIndexing();
    Response stopIndexing();
    boolean isIndexing();
}
