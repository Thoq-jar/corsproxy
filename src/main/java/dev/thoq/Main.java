package dev.thoq;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        System.out.println("Proxy running @:" + port);

        server.createContext("/", new ProxyHandler());
        server.setExecutor(null);
        server.start();
    }

    @SuppressWarnings("CallToPrintStackTrace")
    static class ProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String site = null;

            if (query != null && query.contains("site=")) {
                site = query.split("site=")[1];
                site = URLDecoder.decode(site, StandardCharsets.UTF_8);
            }

            if (site == null || site.isBlank()) {
                sendResponse(exchange, 400, "Missing 'site' query parameter.");
                return;
            }

            try {
                URI uri = new URI(site);
                URL url = uri.toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(exchange.getRequestMethod());

                exchange.getRequestHeaders().forEach((key, values) -> {
                    for (String value : values) {
                        connection.setRequestProperty(key, value);
                    }
                });

                if (exchange.getRequestMethod().equalsIgnoreCase("POST") ||
                        exchange.getRequestMethod().equalsIgnoreCase("PUT")) {
                    connection.setDoOutput(true);
                    try (OutputStream os = connection.getOutputStream()) {
                        exchange.getRequestBody().transferTo(os);
                    }
                }

                int responseCode = connection.getResponseCode();
                InputStream responseStream = (responseCode >= 200 && responseCode < 300) ?
                        connection.getInputStream() : connection.getErrorStream();

                if (responseStream == null) {
                    sendResponse(exchange, 502, "No response from target site.");
                    return;
                }

                String responseBody = new BufferedReader(new InputStreamReader(responseStream))
                        .lines().collect(Collectors.joining("\n"));

                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");

                sendResponse(exchange, responseCode, responseBody);
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Error processing request: " + e.getMessage());
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}
