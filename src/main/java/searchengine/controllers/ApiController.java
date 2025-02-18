package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.Response;
import searchengine.dto.search.SearchResultsResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.interfaces.IndexPageService;
import searchengine.services.interfaces.IndexingService;
import searchengine.services.interfaces.SearchService;
import searchengine.services.interfaces.StatisticsService;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

        @Autowired
       private IndexingService indexingService;

        @Autowired
        private IndexPageService indexPageService;

        @Autowired
        private  SearchService searchService;


    private final StatisticsService statisticsService;

    public ApiController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResultsResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(
                    new SearchResultsResponse(false, 0, Collections.emptyList(), "Запрос не может быть пустым.")
            );
        }

        SearchResultsResponse searchResponse = searchService.search(query, site, offset, limit);

        return ResponseEntity.ok(searchResponse);
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        StatisticsResponse response = statisticsService.getStatistics();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Response> startIndexing() {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    @PostMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        Response response = indexingService.stopIndexing();

        if(!response.isResult()) {
            return ResponseEntity.badRequest().body(Map.of("result", false,
                                                            "error", response.getMessage()
            ));
        }
        return ResponseEntity.ok(Map.of("result", true));
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestParam String url) {
        try {
            boolean result = indexPageService.indexPage(url);
            if (result) {
                return ResponseEntity.ok(Map.of("result", true));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "result", false,
                        "error", "Данная страница находится за пределами сайтов, указанных в конфигурационном файле"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "result", false,
                    "error", "Произошла ошибка: " + e.getMessage()
            ));
        }
    }
    }

