package io.antigen.core.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class ApacheHTTPResponse implements Response {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, Object> headers;
    private final String body;
    private final int statusCode;
    private final Map<String, Object> responseAsMap;

    public ApacheHTTPResponse(HttpResponse response) throws IOException {
        this.statusCode = response.getStatusLine().getStatusCode();
        this.body = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

        Header[] allHeaders = response.getAllHeaders();
        if (allHeaders != null && allHeaders.length > 0) {
            this.headers = Arrays.stream(allHeaders)
                    .collect(Collectors.toMap(
                            Header::getName,
                            Header::getValue,
                            (existing, replacement) -> replacement
                    ));
        } else {
            this.headers = Collections.emptyMap();
        }

        this.responseAsMap = parseBodyToMap(this.body);
    }


    private ApacheHTTPResponse(int statusCode, Map<String, Object> headers, String body) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
        this.responseAsMap = parseBodyToMap(body);
    }

    @Override
    public String getUrl() {
        return null;
    }

    @Override
    public Map<String, Object> getHeaders() {
        return this.headers;
    }

    @Override
    public String getBody() {
        return this.body;
    }


    @Override
    public void setBody(String body) {
        throw new UnsupportedOperationException("ApacheHTTPResponse is immutable. Use withBody() to create a new instance with a different body.");
    }

    @Override
    public Response withBody(String newBody) {
        return new ApacheHTTPResponse(this.statusCode, this.headers, newBody);
    }

    @Override
    public Map<String, Object> getResponseAsMap() {
        return responseAsMap;
    }

    public int getStatusCode() {
        return this.statusCode;
    }


    private Map<String, Object> parseBodyToMap(String body) {
        if (body == null || body.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            System.err.println("[Antigen-WARN] Failed to parse response body as JSON: " + e.getMessage());
            return Collections.emptyMap();
        }
    }
}