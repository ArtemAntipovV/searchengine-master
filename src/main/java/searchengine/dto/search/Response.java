package searchengine.dto.search;

import lombok.Data;

@Data
public class Response {

    private final boolean result;
    private String message;

    public Response(boolean result) {
        this.result = result;
        this.message = message;
    }

    public Response(boolean result, String message) {
        this.result = result;
        this.message = message;
    }
}
