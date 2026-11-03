package de.homelabs.dashboard.testsupport;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Stand-in for the projects REST API: a JDK {@link HttpServer} that records what the
 * dashboard sent (method, path, query, Bearer header) and answers the stubbed
 * status/body. Deliberately no WireMock and no second application context — the contract
 * under test is the HTTP call itself, and observing it needs no dependency.
 *
 * <p>An unstubbed call answers 404 with an error body, so a view that quietly calls an
 * endpoint the test did not expect fails loudly instead of rendering nothing.
 */
public final class FakeProjectsApi implements AutoCloseable {

    /** One recorded request, reduced to the parts the dashboard is responsible for. */
    public record Call(String method, String path, String query, String authorization, String body) {
    }

    private record Stub(int status, String contentType, byte[] body, Map<String, String> headers) {
    }

    private final HttpServer server;

    private final Map<String, Stub> stubs = new ConcurrentHashMap<>();

    private final List<Call> calls = new CopyOnWriteArrayList<>();

    public FakeProjectsApi() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    /** The base URL to configure as the module's API URL. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Answers {@code method path} with the given status and JSON body. */
    public void json(String method, String path, int status, String json) {
        stub(method, path, status, "application/json", json.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    /** Answers {@code method path} with bytes, their content type and the given headers. */
    public void binary(String method, String path, int status, String contentType, byte[] body,
            Map<String, String> headers) {
        stub(method, path, status, contentType, body, headers);
    }

    /** Forgets stubs and recorded calls between tests. */
    public void reset() {
        stubs.clear();
        calls.clear();
    }

    /** Every call recorded so far, in order. */
    public List<Call> calls() {
        return List.copyOf(calls);
    }

    /** The calls to one endpoint, in order. */
    public List<Call> callsTo(String method, String path) {
        return calls.stream().filter(call -> call.method().equals(method) && call.path().equals(path)).toList();
    }

    /** The single call to one endpoint; fails when the route was called a different number of times. */
    public Call onlyCall(String method, String path) {
        List<Call> matching = callsTo(method, path);
        if (matching.size() != 1) {
            throw new AssertionError("expected exactly one " + method + " " + path + " call, got " + matching.size()
                    + " (all calls: " + calls + ")");
        }
        return matching.getFirst();
    }

    private void stub(String method, String path, int status, String contentType, byte[] body,
            Map<String, String> headers) {
        stubs.put(method + " " + path, new Stub(status, contentType, body, headers));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        // The body is part of what the dashboard is responsible for: a misspelled wire
        // field would be dropped by the API's Jackson without any error, so the tests
        // assert the exact payload a mutation sent.
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        calls.add(new Call(method, path, exchange.getRequestURI().getQuery(),
                exchange.getRequestHeaders().getFirst("Authorization"), body));

        Stub stub = stubs.get(method + " " + path);
        if (stub == null) {
            stub = new Stub(404, "application/json",
                    ("{\"error\":\"no stub for " + method + " " + path + "\"}").getBytes(StandardCharsets.UTF_8),
                    Map.of());
        }
        Map<String, String> headers = new LinkedHashMap<>(stub.headers());
        exchange.getResponseHeaders().add("Content-Type", stub.contentType());
        headers.forEach(exchange.getResponseHeaders()::add);
        exchange.sendResponseHeaders(stub.status(), stub.body().length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(stub.body());
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
