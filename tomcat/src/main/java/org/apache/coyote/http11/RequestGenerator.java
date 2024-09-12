package org.apache.coyote.http11;

import java.io.BufferedReader;
import java.io.IOException;
import org.apache.coyote.http.Cookie;
import org.apache.coyote.http.request.HttpRequest;
import org.apache.coyote.http.request.RequestBody;
import org.apache.coyote.http.request.RequestHeaders;
import org.apache.coyote.http.request.RequestLine;
import org.apache.coyote.http.request.RequestParameters;

public class RequestGenerator {

    public static HttpRequest accept(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        RequestLine requestLine = parseRequestLine(firstLine);
        RequestHeaders requestHeaders = readHeaders(reader);
        RequestParameters requestParameters = parseParameters(firstLine);
        Cookie cookie = requestHeaders.parseCookie();
        RequestBody requestBody = readRequestBody(reader, requestHeaders.getContentLength());
        return new HttpRequest(requestLine, requestHeaders, requestParameters, cookie, requestBody);
    }

    private static RequestLine parseRequestLine(String line) {
        String[] token = line.split(" ");
        return new RequestLine(token[0], token[1], token[2]);
    }

    private static RequestHeaders readHeaders(BufferedReader reader) {
        String[] headers = reader.lines()
                .takeWhile(line -> !line.isEmpty())
                .toArray(String[]::new);
        return new RequestHeaders(headers);
    }

    private static RequestParameters parseParameters(String requestLine) {
        String path = requestLine.split(" ")[1];
        if (!path.contains("?")) {
            return RequestParameters.empty();
        }
        int beginIndex = path.indexOf("?") + 1;
        String parameters = path.substring(beginIndex);
        return new RequestParameters(parameters);
    }

    private static RequestBody readRequestBody(BufferedReader reader, int contentLength) throws IOException {
        if (contentLength == 0) {
            return RequestBody.empty();
        }
        char[] buffer = new char[contentLength];
        int ignore = reader.read(buffer, 0, contentLength);
        String requestBody = new String(buffer);
        return RequestBody.fromFormData(requestBody);
    }
}
