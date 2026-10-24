import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ==========================================
// ABSTRACT BASE CLASS
// ==========================================
abstract class LibraryItem {
    protected String itemId;
    protected String title;
    protected String author;
    protected boolean isIssued;

    public LibraryItem(String itemId, String title, String author) {
        this.itemId = itemId;
        this.title = title;
        this.author = author;
        this.isIssued = false;
    }

    public String getItemId() {
        return itemId;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public boolean isIssued() {
        return isIssued;
    }

    public void setIssued(boolean isIssued) {
        this.isIssued = isIssued;
    }

    public abstract String getItemType();

    public abstract double calculateLateFine(int daysOverdue);

    public abstract String getFinePolicyDescription();
}

// ==========================================
// CONCRETE SUBCLASSES
// ==========================================
class Book extends LibraryItem {
    public Book(String itemId, String title, String author) {
        super(itemId, title, author);
    }

    @Override
    public String getItemType() {
        return "Book";
    }

    @Override
    public double calculateLateFine(int daysOverdue) {
        if (daysOverdue <= 0) return 0.0;
        return daysOverdue * 12.0;
    }

    @Override
    public String getFinePolicyDescription() {
        return "$12.00 / day";
    }
}

class Journal extends LibraryItem {
    public Journal(String itemId, String title, String author) {
        super(itemId, title, author);
    }

    @Override
    public String getItemType() {
        return "Journal";
    }

    @Override
    public double calculateLateFine(int daysOverdue) {
        if (daysOverdue <= 0) return 0.0;
        return daysOverdue * 40.0;
    }

    @Override
    public String getFinePolicyDescription() {
        return "$40.00 / day";
    }
}

class Multimedia extends LibraryItem {
    public Multimedia(String itemId, String title, String author) {
        super(itemId, title, author);
    }

    @Override
    public String getItemType() {
        return "Multimedia";
    }

    @Override
    public double calculateLateFine(int daysOverdue) {
        if (daysOverdue <= 0) return 0.0;
        return 100.0 + (daysOverdue * 15.0);
    }

    @Override
    public String getFinePolicyDescription() {
        return "$100.00 base + $15.00 / day";
    }
}

// ==========================================
// MAIN APPLICATION & HTTP SERVER
// ==========================================
public class LibraryManagementSystem {
    private static final int PORT = 8080;
    private static final List<LibraryItem> catalog = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        initDummyData();

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // Context Handlers
            server.createContext("/", new RootHandler());
            server.createContext("/catalog", new CatalogHandler());
            server.createContext("/issue", new IssueHandler());
            server.createContext("/return", new ReturnHandler());

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            System.out.println("==============================================================");
            System.out.println("   LIBRARY MANAGEMENT SYSTEM - MODERN WEB DASHBOARD");
            System.out.println("==============================================================");
            System.out.println("   [STATUS] Server is running on port " + PORT);
            System.out.println("   [ACCESS] Open in browser: http://localhost:" + PORT + "/");
            System.out.println("   [APIs]   GET  /catalog");
            System.out.println("            POST /issue  (body: {\"id\": \"...\"})");
            System.out.println("            POST /return (body: {\"id\": \"...\", \"daysOverdue\": N})");
            System.out.println("   [OOP]    Polymorphic late fine calculations active");
            System.out.println("==============================================================");
            System.out.println("Press Ctrl+C in this console to stop the server.");
        } catch (IOException e) {
            System.err.println("Failed to start HTTP server on port " + PORT + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void initDummyData() {
        catalog.add(new Book("B101", "The Pragmatic Programmer", "Andrew Hunt & David Thomas"));
        catalog.add(new Journal("J201", "IEEE Software Engineering Journal", "IEEE Computer Society"));
        catalog.add(new Multimedia("M301", "Clean Architecture Video Masterclass", "Robert C. Martin"));
        catalog.add(new Book("B102", "Effective Java (3rd Edition)", "Joshua Bloch"));
        catalog.add(new Multimedia("M302", "Design Patterns Video Lectures", "GoF Academy"));
    }

    public static LibraryItem findItemById(String id) {
        if (id == null) return null;
        String trimmed = id.trim();
        synchronized (catalog) {
            for (LibraryItem item : catalog) {
                if (item.getItemId().equalsIgnoreCase(trimmed)) {
                    return item;
                }
            }
        }
        return null;
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public static String itemToJson(LibraryItem item) {
        return "{\"id\":\"" + escapeJson(item.getItemId()) + "\","
                + "\"title\":\"" + escapeJson(item.getTitle()) + "\","
                + "\"author\":\"" + escapeJson(item.getAuthor()) + "\","
                + "\"type\":\"" + escapeJson(item.getItemType()) + "\","
                + "\"isIssued\":" + item.isIssued() + ","
                + "\"finePolicy\":\"" + escapeJson(item.getFinePolicyDescription()) + "\"}";
    }

    public static String catalogToJson() {
        StringBuilder sb = new StringBuilder("[");
        synchronized (catalog) {
            for (int i = 0; i < catalog.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(itemToJson(catalog.get(i)));
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static Map<String, String> parseParams(HttpExchange exchange) throws IOException {
        Map<String, String> params = new HashMap<>();

        // 1. From URI query string (e.g. ?id=B101)
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            parseFormString(rawQuery, params);
        }

        // 2. From Request Body (if POST/PUT)
        InputStream is = exchange.getRequestBody();
        byte[] bodyBytes = is.readAllBytes();
        if (bodyBytes.length > 0) {
            String body = new String(bodyBytes, StandardCharsets.UTF_8).trim();
            if (body.startsWith("{") && body.endsWith("}")) {
                Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([-\\d\\.]+)|(true|false|null))");
                Matcher matcher = pattern.matcher(body);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    String val = matcher.group(2) != null ? matcher.group(2) : (matcher.group(3) != null ? matcher.group(3) : matcher.group(4));
                    if (key != null && val != null) {
                        params.put(key, val);
                    }
                }
            } else {
                parseFormString(body, params);
            }
        }
        return params;
    }

    private static void parseFormString(String query, Map<String, String> params) {
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            try {
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, val);
                } else if (idx < 0 && !pair.isEmpty()) {
                    params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                }
            } catch (Exception ignored) {}
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void handleCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    // ==========================================
    // HTTP HANDLERS
    // ==========================================
    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/favicon.ico")) {
                exchange.getResponseHeaders().set("Content-Type", "image/svg+xml");
                byte[] icon = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>📚</text></svg>".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, icon.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(icon);
                }
                return;
            }

            if (!path.equals("/") && !path.equals("/index.html")) {
                String notFound = "404 Not Found";
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            byte[] bytes = HTML_PAGE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class CatalogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleCors(exchange);
                return;
            }
            sendJsonResponse(exchange, 200, catalogToJson());
        }
    }

    static class IssueHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleCors(exchange);
                return;
            }

            Map<String, String> params = parseParams(exchange);
            String id = params.get("id");
            if (id == null || id.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Item ID cannot be blank.\"}");
                return;
            }

            LibraryItem item = findItemById(id.trim());
            if (item == null) {
                sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"No item found with ID: " + escapeJson(id.trim()) + "\"}");
                return;
            }

            if (item.isIssued()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Item '" + escapeJson(item.getTitle()) + "' (ID: " + escapeJson(item.getItemId()) + ") is ALREADY ISSUED.\"}");
                return;
            }

            // Issue item
            item.setIssued(true);
            String response = "{\"success\":true,\"message\":\"Successfully issued item: " + escapeJson(item.getTitle()) + "\",\"item\":" + itemToJson(item) + "}";
            sendJsonResponse(exchange, 200, response);
        }
    }

    static class ReturnHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleCors(exchange);
                return;
            }

            Map<String, String> params = parseParams(exchange);
            String id = params.get("id");
            if (id == null || id.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Item ID cannot be blank.\"}");
                return;
            }

            LibraryItem item = findItemById(id.trim());
            if (item == null) {
                sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"No item found with ID: " + escapeJson(id.trim()) + "\"}");
                return;
            }

            if (!item.isIssued()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Item '" + escapeJson(item.getTitle()) + "' is NOT currently issued (already Available).\"}");
                return;
            }

            int daysOverdue = 0;
            String daysStr = params.get("daysOverdue");
            if (daysStr != null && !daysStr.trim().isEmpty()) {
                try {
                    daysOverdue = Integer.parseInt(daysStr.trim());
                    if (daysOverdue < 0) {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Days overdue cannot be negative.\"}");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid number for days overdue.\"}");
                    return;
                }
            }

            // Polymorphic Late Fine Calculation
            double fine = item.calculateLateFine(daysOverdue);
            item.setIssued(false);

            String formattedFine = String.format("$%.2f", fine);
            String response = "{\"success\":true,\"message\":\"Item '" + escapeJson(item.getTitle()) + "' returned successfully.\","
                    + "\"daysOverdue\":" + daysOverdue + ","
                    + "\"fine\":" + String.format(java.util.Locale.US, "%.2f", fine) + ","
                    + "\"formattedFine\":\"" + formattedFine + "\","
                    + "\"finePolicy\":\"" + escapeJson(item.getFinePolicyDescription()) + "\","
                    + "\"item\":" + itemToJson(item) + "}";

            sendJsonResponse(exchange, 200, response);
        }
    }

    // ==========================================
    // BEAUTIFUL DARK-MODE WEB FRONTEND
    // ==========================================
    private static final String HTML_PAGE = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Library Management System - Live Dashboard</title>
    <style>
        :root {
            --bg-base: #0B0F19;
            --bg-surface: #111827;
            --bg-card: #162032;
            --bg-card-hover: #1E2A42;
            --border-subtle: #243048;
            --border-focus: #6366F1;
            --primary: #6366F1;
            --primary-hover: #4F46E5;
            --primary-light: rgba(99, 102, 241, 0.15);
            --text-main: #F3F4F6;
            --text-secondary: #94A3B8;
            --text-muted: #64748B;
            --emerald-bg: rgba(16, 185, 129, 0.16);
            --emerald-text: #34D399;
            --emerald-border: rgba(16, 185, 129, 0.35);
            --rose-bg: rgba(244, 63, 94, 0.16);
            --rose-text: #FB7185;
            --rose-border: rgba(244, 63, 94, 0.35);
            --amber-bg: rgba(245, 158, 11, 0.16);
            --amber-text: #FBBF24;
            --cyan-bg: rgba(6, 182, 212, 0.16);
            --cyan-text: #38BDF8;
            --purple-bg: rgba(168, 85, 247, 0.16);
            --purple-text: #C084FC;
            --shadow-card: 0 10px 25px -5px rgba(0, 0, 0, 0.4), 0 8px 10px -6px rgba(0, 0, 0, 0.4);
            --radius-md: 10px;
            --radius-lg: 14px;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Inter", Helvetica, Arial, sans-serif;
        }

        body {
            background-color: var(--bg-base);
            color: var(--text-main);
            min-height: 100vh;
            padding: 24px 20px 48px;
            background-image: radial-gradient(circle at 15% 10%, rgba(99, 102, 241, 0.08) 0%, transparent 40%),
                              radial-gradient(circle at 85% 85%, rgba(6, 182, 212, 0.06) 0%, transparent 40%);
        }

        .container {
            max-width: 1200px;
            margin: 0 auto;
        }

        /* Top Header */
        header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 16px;
            margin-bottom: 28px;
            padding-bottom: 20px;
            border-bottom: 1px solid var(--border-subtle);
        }

        .brand-container {
            display: flex;
            align-items: center;
            gap: 16px;
        }

        .brand-icon {
            width: 48px;
            height: 48px;
            background: linear-gradient(135deg, #6366F1, #8B5CF6);
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 4px 14px rgba(99, 102, 241, 0.4);
        }

        .brand-icon svg {
            width: 26px;
            height: 26px;
            fill: #FFFFFF;
        }

        .brand-text h1 {
            font-size: 22px;
            font-weight: 700;
            letter-spacing: -0.02em;
            color: #FFFFFF;
        }

        .brand-text p {
            font-size: 13px;
            color: var(--text-secondary);
            margin-top: 2px;
        }

        .status-badge {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            background: rgba(16, 185, 129, 0.1);
            border: 1px solid var(--emerald-border);
            padding: 6px 14px;
            border-radius: 9999px;
            font-size: 12px;
            font-weight: 600;
            color: var(--emerald-text);
        }

        .pulse-dot {
            width: 8px;
            height: 8px;
            background: var(--emerald-text);
            border-radius: 50%;
            box-shadow: 0 0 10px var(--emerald-text);
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.4; transform: scale(1.2); }
            100% { opacity: 1; transform: scale(1); }
        }

        /* Metric Cards */
        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
            gap: 16px;
            margin-bottom: 28px;
        }

        .metric-card {
            background: var(--bg-surface);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-lg);
            padding: 20px;
            box-shadow: var(--shadow-card);
            display: flex;
            align-items: center;
            justify-content: space-between;
            transition: transform 0.2s, border-color 0.2s;
        }

        .metric-card:hover {
            transform: translateY(-2px);
            border-color: rgba(99, 102, 241, 0.4);
        }

        .metric-info h3 {
            font-size: 12px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--text-muted);
            margin-bottom: 6px;
        }

        .metric-info .value {
            font-size: 28px;
            font-weight: 700;
            color: #FFFFFF;
        }

        .metric-info .subtext {
            font-size: 12px;
            color: var(--text-secondary);
            margin-top: 4px;
        }

        .metric-icon-wrap {
            width: 48px;
            height: 48px;
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .metric-icon-wrap svg {
            width: 24px;
            height: 24px;
        }

        /* Action Toolbar */
        .toolbar {
            background: var(--bg-surface);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-lg);
            padding: 16px 20px;
            margin-bottom: 24px;
            display: flex;
            flex-wrap: wrap;
            gap: 14px;
            align-items: center;
            justify-content: space-between;
        }

        .search-box {
            position: relative;
            flex: 1;
            min-width: 260px;
        }

        .search-box svg {
            position: absolute;
            left: 14px;
            top: 50%;
            transform: translateY(-50%);
            width: 18px;
            height: 18px;
            fill: var(--text-muted);
        }

        .search-box input {
            width: 100%;
            background: var(--bg-base);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-md);
            padding: 10px 14px 10px 42px;
            color: var(--text-main);
            font-size: 14px;
            outline: none;
            transition: border-color 0.2s, box-shadow 0.2s;
        }

        .search-box input:focus {
            border-color: var(--primary);
            box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
        }

        .filter-group {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-wrap: wrap;
        }

        .filter-btn {
            background: var(--bg-base);
            border: 1px solid var(--border-subtle);
            color: var(--text-secondary);
            padding: 8px 14px;
            border-radius: var(--radius-md);
            font-size: 13px;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.2s;
        }

        .filter-btn:hover {
            color: var(--text-main);
            border-color: var(--primary);
        }

        .filter-btn.active {
            background: var(--primary);
            color: #FFFFFF;
            border-color: var(--primary);
            box-shadow: 0 2px 8px rgba(99, 102, 241, 0.35);
        }

        .btn-group {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
        }

        .btn {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            padding: 9px 18px;
            font-size: 13px;
            font-weight: 600;
            border-radius: var(--radius-md);
            border: none;
            cursor: pointer;
            transition: all 0.2s;
            text-decoration: none;
        }

        .btn svg {
            width: 16px;
            height: 16px;
            fill: currentColor;
        }

        .btn-primary {
            background: var(--primary);
            color: #FFFFFF;
            box-shadow: 0 3px 10px rgba(99, 102, 241, 0.3);
        }

        .btn-primary:hover {
            background: var(--primary-hover);
            transform: translateY(-1px);
        }

        .btn-secondary {
            background: rgba(255, 255, 255, 0.06);
            color: var(--text-main);
            border: 1px solid var(--border-subtle);
        }

        .btn-secondary:hover {
            background: rgba(255, 255, 255, 0.1);
            border-color: rgba(255, 255, 255, 0.15);
        }

        .btn-emerald {
            background: #10B981;
            color: #FFFFFF;
            box-shadow: 0 3px 10px rgba(16, 185, 129, 0.3);
        }

        .btn-emerald:hover {
            background: #059669;
        }

        /* Table Card */
        .table-card {
            background: var(--bg-surface);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-lg);
            box-shadow: var(--shadow-card);
            overflow: hidden;
        }

        .table-header-title {
            padding: 18px 24px;
            border-bottom: 1px solid var(--border-subtle);
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .table-header-title h2 {
            font-size: 16px;
            font-weight: 700;
            color: #FFFFFF;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .table-wrapper {
            overflow-x: auto;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            text-align: left;
            font-size: 14px;
        }

        thead tr {
            background: #141D2E;
            border-bottom: 1px solid var(--border-subtle);
        }

        th {
            padding: 14px 20px;
            font-weight: 600;
            font-size: 12px;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--text-muted);
            white-space: nowrap;
        }

        tbody tr {
            border-bottom: 1px solid rgba(255, 255, 255, 0.04);
            transition: background-color 0.15s;
        }

        tbody tr:hover {
            background: rgba(255, 255, 255, 0.025);
        }

        td {
            padding: 16px 20px;
            vertical-align: middle;
            color: var(--text-main);
        }

        .item-id {
            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
            font-size: 13px;
            font-weight: 700;
            color: var(--cyan-text);
            background: rgba(6, 182, 212, 0.1);
            padding: 4px 8px;
            border-radius: 6px;
            border: 1px solid rgba(6, 182, 212, 0.25);
            display: inline-block;
        }

        .item-title-block {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .type-icon-circle {
            width: 34px;
            height: 34px;
            border-radius: 8px;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
        }

        .type-icon-circle svg {
            width: 18px;
            height: 18px;
        }

        .item-title {
            font-weight: 600;
            color: #FFFFFF;
            display: block;
        }

        .item-author {
            font-size: 12px;
            color: var(--text-secondary);
            margin-top: 2px;
        }

        /* Type Badges */
        .type-pill {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 12px;
            font-weight: 600;
        }

        .type-pill.book {
            background: var(--purple-bg);
            color: var(--purple-text);
            border: 1px solid rgba(168, 85, 247, 0.3);
        }

        .type-pill.journal {
            background: var(--amber-bg);
            color: var(--amber-text);
            border: 1px solid rgba(245, 158, 11, 0.3);
        }

        .type-pill.multimedia {
            background: var(--cyan-bg);
            color: var(--cyan-text);
            border: 1px solid rgba(6, 182, 212, 0.3);
        }

        /* Status Badges */
        .status-pill {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 5px 12px;
            border-radius: 9999px;
            font-size: 12px;
            font-weight: 700;
            letter-spacing: 0.02em;
        }

        .status-pill.available {
            background: var(--emerald-bg);
            color: var(--emerald-text);
            border: 1px solid var(--emerald-border);
        }

        .status-pill.issued {
            background: var(--rose-bg);
            color: var(--rose-text);
            border: 1px solid var(--rose-border);
        }

        .status-pill-dot {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: currentColor;
        }

        .fine-policy-text {
            font-size: 12px;
            color: var(--text-secondary);
            font-weight: 500;
        }

        .action-cell {
            white-space: nowrap;
        }

        .btn-sm {
            padding: 6px 12px;
            font-size: 12px;
            font-weight: 600;
            border-radius: 6px;
        }

        /* Empty State */
        .empty-state {
            padding: 48px 20px;
            text-align: center;
            color: var(--text-muted);
        }

        .empty-state svg {
            width: 48px;
            height: 48px;
            fill: var(--text-muted);
            margin-bottom: 12px;
            opacity: 0.5;
        }

        /* Modals */
        .modal-overlay {
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0, 0, 0, 0.75);
            backdrop-filter: blur(6px);
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 1000;
            padding: 16px;
        }

        .modal-overlay.open {
            display: flex;
        }

        .modal-card {
            background: #151D2C;
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-lg);
            width: 100%;
            max-width: 500px;
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.6);
            overflow: hidden;
            animation: modalFadeIn 0.2s ease-out;
        }

        @keyframes modalFadeIn {
            from { opacity: 0; transform: scale(0.96) translateY(-8px); }
            to { opacity: 1; transform: scale(1) translateY(0); }
        }

        .modal-header {
            padding: 18px 24px;
            border-bottom: 1px solid var(--border-subtle);
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .modal-header h3 {
            font-size: 18px;
            font-weight: 700;
            color: #FFFFFF;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .modal-close {
            background: none;
            border: none;
            color: var(--text-muted);
            cursor: pointer;
            padding: 4px;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 6px;
            transition: color 0.2s, background 0.2s;
        }

        .modal-close:hover {
            color: #FFFFFF;
            background: rgba(255, 255, 255, 0.08);
        }

        .modal-close svg {
            width: 20px;
            height: 20px;
            fill: currentColor;
        }

        .modal-body {
            padding: 24px;
        }

        .form-group {
            margin-bottom: 18px;
        }

        .form-group label {
            display: block;
            font-size: 13px;
            font-weight: 600;
            color: var(--text-secondary);
            margin-bottom: 8px;
        }

        .form-group select,
        .form-group input {
            width: 100%;
            background: var(--bg-base);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-md);
            padding: 11px 14px;
            color: var(--text-main);
            font-size: 14px;
            outline: none;
            transition: border-color 0.2s;
        }

        .form-group select:focus,
        .form-group input:focus {
            border-color: var(--primary);
            box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
        }

        .helper-text {
            font-size: 12px;
            color: var(--text-muted);
            margin-top: 6px;
        }

        .preview-box {
            background: var(--bg-base);
            border: 1px dashed var(--border-subtle);
            border-radius: var(--radius-md);
            padding: 14px;
            margin-top: 14px;
            font-size: 13px;
        }

        .preview-box .preview-row {
            display: flex;
            justify-content: space-between;
            margin-bottom: 6px;
        }

        .preview-box .preview-row:last-child {
            margin-bottom: 0;
        }

        .preview-box .label {
            color: var(--text-muted);
        }

        .preview-box .val {
            font-weight: 600;
            color: var(--text-main);
        }

        .calculator-preview {
            background: rgba(99, 102, 241, 0.08);
            border: 1px solid rgba(99, 102, 241, 0.25);
            border-radius: var(--radius-md);
            padding: 14px;
            margin-top: 14px;
        }

        .calc-header {
            font-size: 12px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.04em;
            color: #A5B4FC;
            margin-bottom: 8px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .calc-formula {
            font-size: 13px;
            color: var(--text-secondary);
            margin-bottom: 6px;
        }

        .calc-total {
            font-size: 18px;
            font-weight: 700;
            color: #FFFFFF;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .calc-total .fine-highlight {
            color: var(--rose-text);
        }

        .calc-total .fine-zero {
            color: var(--emerald-text);
        }

        .modal-footer {
            padding: 16px 24px;
            background: #111827;
            border-top: 1px solid var(--border-subtle);
            display: flex;
            justify-content: flex-end;
            gap: 12px;
        }

        /* Digital Receipt */
        .receipt-card {
            background: #0E1624;
            border: 2px dashed #2E3E5B;
            border-radius: var(--radius-lg);
            padding: 24px;
            position: relative;
        }

        .receipt-header {
            text-align: center;
            border-bottom: 1px dashed #2E3E5B;
            padding-bottom: 16px;
            margin-bottom: 18px;
        }

        .receipt-badge {
            display: inline-block;
            background: var(--primary-light);
            color: #A5B4FC;
            font-size: 11px;
            font-weight: 700;
            letter-spacing: 0.06em;
            text-transform: uppercase;
            padding: 4px 10px;
            border-radius: 9999px;
            margin-bottom: 8px;
        }

        .receipt-header h4 {
            font-size: 18px;
            font-weight: 800;
            color: #FFFFFF;
            letter-spacing: 0.02em;
        }

        .receipt-header .time {
            font-size: 12px;
            color: var(--text-muted);
            margin-top: 4px;
        }

        .receipt-body .row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
            font-size: 13px;
        }

        .receipt-body .row.fine-row {
            border-top: 1px dashed #2E3E5B;
            padding-top: 14px;
            margin-top: 14px;
        }

        .receipt-body .fine-label {
            font-size: 15px;
            font-weight: 700;
            color: #FFFFFF;
        }

        .receipt-body .fine-amount {
            font-size: 24px;
            font-weight: 800;
            color: var(--rose-text);
        }

        .receipt-body .fine-amount.zero {
            color: var(--emerald-text);
        }

        .receipt-footer-msg {
            text-align: center;
            font-size: 12px;
            color: var(--emerald-text);
            background: var(--emerald-bg);
            border: 1px solid var(--emerald-border);
            border-radius: 6px;
            padding: 8px;
            margin-top: 18px;
        }

        /* Policy Cards inside Policy Modal */
        .policy-grid {
            display: grid;
            gap: 12px;
        }

        .policy-item {
            background: var(--bg-base);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-md);
            padding: 14px;
            display: flex;
            align-items: flex-start;
            gap: 12px;
        }

        .policy-item h4 {
            font-size: 14px;
            font-weight: 700;
            color: #FFFFFF;
            margin-bottom: 4px;
        }

        .policy-item p {
            font-size: 13px;
            color: var(--text-secondary);
        }

        .policy-rate-tag {
            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
            font-size: 12px;
            font-weight: 700;
            color: #A5B4FC;
            background: rgba(99, 102, 241, 0.15);
            padding: 2px 6px;
            border-radius: 4px;
            display: inline-block;
            margin-top: 4px;
        }

        /* Toast Notifications */
        #toast-container {
            position: fixed;
            top: 24px;
            right: 24px;
            z-index: 2000;
            display: flex;
            flex-direction: column;
            gap: 10px;
            pointer-events: none;
        }

        .toast {
            pointer-events: auto;
            min-width: 300px;
            max-width: 420px;
            background: #182234;
            border-left: 4px solid var(--primary);
            border-radius: var(--radius-md);
            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.5);
            padding: 14px 18px;
            display: flex;
            align-items: flex-start;
            gap: 12px;
            color: #FFFFFF;
            font-size: 13px;
            animation: slideInRight 0.25s ease-out;
            border-top: 1px solid var(--border-subtle);
            border-right: 1px solid var(--border-subtle);
            border-bottom: 1px solid var(--border-subtle);
        }

        .toast.success {
            border-left-color: #10B981;
        }

        .toast.error {
            border-left-color: #EF4444;
        }

        @keyframes slideInRight {
            from { opacity: 0; transform: translateX(30px); }
            to { opacity: 1; transform: translateX(0); }
        }
    </style>
</head>
<body>

<div id="toast-container"></div>

<div class="container">
    <!-- Header -->
    <header>
        <div class="brand-container">
            <div class="brand-icon">
                <svg viewBox="0 0 24 24"><path d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H8V4h12v12z"/></svg>
            </div>
            <div class="brand-text">
                <h1>Library Management System</h1>
                
            </div>
        </div>

        <div style="display: flex; align-items: center; gap: 12px;">
            <div class="status-badge">
                <span class="pulse-dot"></span>
                <span>System Online (Port 8080)</span>
            </div>
            <button class="btn btn-secondary" onclick="fetchCatalog()" title="Refresh Catalog">
                <svg viewBox="0 0 24 24"><path d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/></svg>
                <span>Sync</span>
            </button>
        </div>
    </header>

    <!-- Metrics Cards -->
    <div class="metrics-grid">
        <div class="metric-card">
            <div class="metric-info">
                <h3>Total Catalog</h3>
                <div class="value" id="stat-total">0</div>
                <div class="subtext">In-Memory ArrayList items</div>
            </div>
            <div class="metric-icon-wrap" style="background: rgba(99, 102, 241, 0.15);">
                <svg fill="#818CF8" viewBox="0 0 24 24"><path d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H8V4h12v12z"/></svg>
            </div>
        </div>

        <div class="metric-card">
            <div class="metric-info">
                <h3>Available</h3>
                <div class="value" id="stat-available" style="color: var(--emerald-text);">0</div>
                <div class="subtext" id="stat-available-pct">Ready for checkout</div>
            </div>
            <div class="metric-icon-wrap" style="background: var(--emerald-bg);">
                <svg fill="var(--emerald-text)" viewBox="0 0 24 24"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>
            </div>
        </div>

        <div class="metric-card">
            <div class="metric-info">
                <h3>Issued / Loaned</h3>
                <div class="value" id="stat-issued" style="color: var(--rose-text);">0</div>
                <div class="subtext" id="stat-issued-pct">Currently checked out</div>
            </div>
            <div class="metric-icon-wrap" style="background: var(--rose-bg);">
                <svg fill="var(--rose-text)" viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 14h-2v-2h2v2zm0-4h-2V7h2v5z"/></svg>
            </div>
        </div>

        <div class="metric-card">
            <div class="metric-info">
                <h3>Late Fine Engine</h3>
                <div class="value" style="font-size: 20px; color: #A5B4FC; margin-top: 4px;">Polymorphic</div>
                <div class="subtext">Book • Journal • Multimedia</div>
            </div>
            <div class="metric-icon-wrap" style="background: var(--purple-bg);">
                <svg fill="var(--purple-text)" viewBox="0 0 24 24"><path d="M11.8 10.9c-2.27-.59-3-1.2-3-2.15 0-1.09 1.01-1.85 2.7-1.85 1.78 0 2.44.85 2.5 2.1h2.21c-.07-1.72-1.12-3.3-3.21-3.81V3h-3v2.16c-1.94.42-3.5 1.68-3.5 3.61 0 2.31 1.91 3.46 4.7 4.13 2.5.6 3 1.48 3 2.41 0 .69-.49 1.79-2.7 1.79-2.06 0-2.87-.92-2.98-2.1h-2.2c.12 2.19 1.76 3.42 3.68 3.83V21h3v-2.15c1.95-.37 3.5-1.5 3.5-3.55 0-2.84-2.43-3.81-4.7-4.4z"/></svg>
            </div>
        </div>
    </div>

    <!-- Action Toolbar -->
    <div class="toolbar">
        <div class="search-box">
            <svg viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>
            <input type="text" id="searchInput" placeholder="Search by title, author, or ID..." oninput="handleSearch()">
        </div>

        <div class="filter-group">
            <button class="filter-btn active" data-filter="all" onclick="setFilter('all', this)">All Items</button>
            <button class="filter-btn" data-filter="available" onclick="setFilter('available', this)">Available</button>
            <button class="filter-btn" data-filter="issued" onclick="setFilter('issued', this)">Issued</button>
            <button class="filter-btn" data-filter="Book" onclick="setFilter('Book', this)">Books</button>
            <button class="filter-btn" data-filter="Journal" onclick="setFilter('Journal', this)">Journals</button>
            <button class="filter-btn" data-filter="Multimedia" onclick="setFilter('Multimedia', this)">Multimedia</button>
        </div>

        <div class="btn-group">
            <button class="btn btn-primary" onclick="openIssueModal()">
                <svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
                <span>Issue Item</span>
            </button>
            <button class="btn btn-emerald" onclick="openReturnModal()">
                <svg viewBox="0 0 24 24"><path d="M9 14l-4-4 4-4v3h8c1.1 0 2 .9 2 2v2h-2v-2H9v3z"/></svg>
                <span>Return Item</span>
            </button>
            <button class="btn btn-secondary" onclick="openPolicyModal()">
                <svg viewBox="0 0 24 24"><path d="M11 7h2v2h-2zm0 4h2v6h-2zm1-9C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"/></svg>
                <span>Fine Policy</span>
            </button>
        </div>
    </div>

    <!-- Live Catalog Table -->
    <div class="table-card">
        <div class="table-header-title">
            <h2>
                <svg width="20" height="20" fill="var(--primary)" viewBox="0 0 24 24"><path d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H8V4h12v12z"/></svg>
                <span>Live In-Memory Catalog</span>
            </h2>
            <span id="filtered-count-badge" style="font-size: 13px; color: var(--text-muted);">Showing 5 items</span>
        </div>

        <div class="table-wrapper">
            <table>
                <thead>
                    <tr>
                        <th style="width: 100px;">Item ID</th>
                        <th>Title & Information</th>
                        <th>Type</th>
                        <th>Late Fine Rule</th>
                        <th>Live Status</th>
                        <th style="text-align: right; width: 140px;">Action</th>
                    </tr>
                </thead>
                <tbody id="catalogTableBody">
                    <!-- Populated via JavaScript -->
                </tbody>
            </table>
            <div id="emptyCatalogState" class="empty-state" style="display: none;">
                <svg viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>
                <h3>No Library Items Found</h3>
                <p>No items matched your current search or filter query.</p>
            </div>
        </div>
    </div>
</div>

<!-- ================= MODALS ================= -->

<!-- Issue Item Modal -->
<div class="modal-overlay" id="issueModal">
    <div class="modal-card">
        <div class="modal-header">
            <h3>
                <svg width="20" height="20" fill="var(--primary)" viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
                <span>Issue Library Item</span>
            </h3>
            <button class="modal-close" onclick="closeModal('issueModal')">
                <svg viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
            </button>
        </div>
        <div class="modal-body">
            <div class="form-group">
                <label for="issueItemSelect">Select an Available Item</label>
                <select id="issueItemSelect" onchange="onIssueSelectChange()">
                    <!-- Options populated via JS -->
                </select>
            </div>

            <div class="form-group">
                <label for="issueManualId">Or Enter Item ID Directly</label>
                <input type="text" id="issueManualId" placeholder="e.g. B101, J201, M301" oninput="onIssueInputManual()">
                <div class="helper-text">Lookup is case-insensitive.</div>
            </div>

            <div class="preview-box" id="issuePreviewBox" style="display: none;">
                <div class="preview-row"><span class="label">Title:</span> <span class="val" id="prevIssueTitle">-</span></div>
                <div class="preview-row"><span class="label">Author:</span> <span class="val" id="prevIssueAuthor">-</span></div>
                <div class="preview-row"><span class="label">Type:</span> <span class="val" id="prevIssueType">-</span></div>
                <div class="preview-row"><span class="label">Late Fine Policy:</span> <span class="val" id="prevIssuePolicy">-</span></div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-secondary" onclick="closeModal('issueModal')">Cancel</button>
            <button class="btn btn-primary" onclick="submitIssue()">Confirm Issue</button>
        </div>
    </div>
</div>

<!-- Return Item Modal -->
<div class="modal-overlay" id="returnModal">
    <div class="modal-card">
        <div class="modal-header">
            <h3>
                <svg width="20" height="20" fill="var(--emerald-text)" viewBox="0 0 24 24"><path d="M9 14l-4-4 4-4v3h8c1.1 0 2 .9 2 2v2h-2v-2H9v3z"/></svg>
                <span>Return Library Item</span>
            </h3>
            <button class="modal-close" onclick="closeModal('returnModal')">
                <svg viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
            </button>
        </div>
        <div class="modal-body">
            <div class="form-group">
                <label for="returnItemSelect">Select Issued Item</label>
                <select id="returnItemSelect" onchange="onReturnSelectChange()">
                    <!-- Options populated via JS -->
                </select>
            </div>

            <div class="form-group">
                <label for="returnManualId">Or Enter Item ID Directly</label>
                <input type="text" id="returnManualId" placeholder="e.g. B101" oninput="onReturnInputManual()">
            </div>

            <div class="form-group">
                <label for="daysOverdueInput">Days Overdue (0 if returned on time)</label>
                <input type="number" id="daysOverdueInput" value="0" min="0" max="365" oninput="updateLiveFineEstimate()">
                <div class="helper-text">Applies polymorphic late fine calculation based on item type.</div>
            </div>

            <!-- Dynamic Live Fine Preview Box -->
            <div class="calculator-preview" id="calcPreviewBox">
                <div class="calc-header">
                    <span>Live Fine Estimator</span>
                    <span id="calcPreviewTypeBadge" class="type-pill book">Book</span>
                </div>
                <div class="calc-formula" id="calcPreviewFormula">Rule: $12.00 / day</div>
                <div class="calc-total">
                    <span>Estimated Fine Due:</span>
                    <span id="calcPreviewTotal" class="fine-zero">$0.00</span>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-secondary" onclick="closeModal('returnModal')">Cancel</button>
            <button class="btn btn-emerald" onclick="submitReturn()">Process Return</button>
        </div>
    </div>
</div>

<!-- Receipt Modal -->
<div class="modal-overlay" id="receiptModal">
    <div class="modal-card" style="max-width: 460px;">
        <div class="modal-body">
            <div class="receipt-card">
                <div class="receipt-header">
                    <span class="receipt-badge" id="rcptBadge">RETURN RECEIPT</span>
                    <h4 id="rcptTitle">Library Transaction</h4>
                    <div class="time" id="rcptTime">Timestamp</div>
                </div>

                <div class="receipt-body">
                    <div class="row">
                        <span class="label">Item ID:</span>
                        <span class="val item-id" id="rcptItemId">-</span>
                    </div>
                    <div class="row">
                        <span class="label">Item Title:</span>
                        <span class="val" id="rcptItemTitle" style="font-weight: 600;">-</span>
                    </div>
                    <div class="row">
                        <span class="label">Type / Category:</span>
                        <span class="val" id="rcptItemType">-</span>
                    </div>
                    <div class="row">
                        <span class="label">Author / Creator:</span>
                        <span class="val" id="rcptAuthor">-</span>
                    </div>
                    <div class="row">
                        <span class="label">Days Overdue:</span>
                        <span class="val" id="rcptDaysOverdue">0 day(s)</span>
                    </div>
                    <div class="row">
                        <span class="label">Applied Policy:</span>
                        <span class="val" id="rcptPolicy">-</span>
                    </div>
                    <div class="row fine-row">
                        <span class="fine-label">TOTAL FINE DUE:</span>
                        <span class="fine-amount" id="rcptFineDue">$0.00</span>
                    </div>
                </div>

                <div class="receipt-footer-msg">
                    ✓ Item status successfully restored to AVAILABLE in catalog.
                </div>
            </div>
        </div>
        <div class="modal-footer" style="justify-content: center;">
            <button class="btn btn-primary" style="width: 100%; justify-content: center;" onclick="closeModal('receiptModal')">Done / Close Receipt</button>
        </div>
    </div>
</div>

<!-- Fine Policy Modal -->
<div class="modal-overlay" id="policyModal">
    <div class="modal-card">
        <div class="modal-header">
            <h3>
                <svg width="20" height="20" fill="var(--purple-text)" viewBox="0 0 24 24"><path d="M11 7h2v2h-2zm0 4h2v6h-2zm1-9C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"/></svg>
                <span>Polymorphic Fine Schedule</span>
            </h3>
            <button class="modal-close" onclick="closeModal('policyModal')">
                <svg viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
            </button>
        </div>
        <div class="modal-body">
            <div class="policy-grid">
                <div class="policy-item">
                    <div class="type-icon-circle" style="background: var(--purple-bg);">
                        <svg fill="var(--purple-text)" viewBox="0 0 24 24"><path d="M18 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 4h5v8l-2.5-1.5L6 12V4z"/></svg>
                    </div>
                    <div>
                        <h4>Book Policy</h4>
                        <p>Standard rate applied per single day overdue with zero base penalty.</p>
                        <div class="policy-rate-tag">Formula: days × $12.00</div>
                    </div>
                </div>

                <div class="policy-item">
                    <div class="type-icon-circle" style="background: var(--amber-bg);">
                        <svg fill="var(--amber-text)" viewBox="0 0 24 24"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z"/></svg>
                    </div>
                    <div>
                        <h4>Journal Policy</h4>
                        <p>High-value academic and research journals with an accelerated overdue rate.</p>
                        <div class="policy-rate-tag">Formula: days × $40.00</div>
                    </div>
                </div>

                <div class="policy-item">
                    <div class="type-icon-circle" style="background: var(--cyan-bg);">
                        <svg fill="var(--cyan-text)" viewBox="0 0 24 24"><path d="M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-9 9H3V5h9v7zM5 19l3.5-4.5 2.5 3.01L14.5 13l4.5 6H5z"/></svg>
                    </div>
                    <div>
                        <h4>Multimedia Policy</h4>
                        <p>Equipment and masterclass video media with a fixed base charge plus daily fee.</p>
                        <div class="policy-rate-tag">Formula: $100.00 base + (days × $15.00)</div>
                    </div>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-primary" onclick="closeModal('policyModal')">Got It</button>
        </div>
    </div>
</div>

<script>
    let catalogData = [];
    let currentFilter = 'all';

    // SVG icon templates
    function getIconSvg(type) {
        if (type === 'Book') {
            return '<svg fill="var(--purple-text)" viewBox="0 0 24 24"><path d="M18 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 4h5v8l-2.5-1.5L6 12V4z"/></svg>';
        } else if (type === 'Journal') {
            return '<svg fill="var(--amber-text)" viewBox="0 0 24 24"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z"/></svg>';
        } else {
            return '<svg fill="var(--cyan-text)" viewBox="0 0 24 24"><path d="M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-9 9H3V5h9v7zM5 19l3.5-4.5 2.5 3.01L14.5 13l4.5 6H5z"/></svg>';
        }
    }

    // Fetch live catalog from Java backend
    async function fetchCatalog() {
        try {
            const res = await fetch('/catalog');
            if (!res.ok) throw new Error('HTTP ' + res.status);
            catalogData = await res.json();
            renderDashboard();
        } catch (err) {
            showToast('Failed to fetch catalog: ' + err.message, 'error');
        }
    }

    // Render stats & table
    function renderDashboard() {
        const total = catalogData.length;
        const issuedCount = catalogData.filter(i => i.isIssued).length;
        const availableCount = total - issuedCount;

        document.getElementById('stat-total').textContent = total;
        document.getElementById('stat-available').textContent = availableCount;
        document.getElementById('stat-issued').textContent = issuedCount;

        const availPct = total > 0 ? Math.round((availableCount / total) * 100) : 0;
        const issuedPct = total > 0 ? Math.round((issuedCount / total) * 100) : 0;
        document.getElementById('stat-available-pct').textContent = availPct + '% of total collection';
        document.getElementById('stat-issued-pct').textContent = issuedPct + '% in circulation';

        renderTable();
    }

    function renderTable() {
        const tbody = document.getElementById('catalogTableBody');
        const emptyState = document.getElementById('emptyCatalogState');
        const searchVal = document.getElementById('searchInput').value.trim().toLowerCase();

        let filtered = catalogData.filter(item => {
            // Category filter
            if (currentFilter === 'available' && item.isIssued) return false;
            if (currentFilter === 'issued' && !item.isIssued) return false;
            if (['Book', 'Journal', 'Multimedia'].includes(currentFilter) && item.type !== currentFilter) return false;

            // Search filter
            if (searchVal) {
                const matchId = item.id.toLowerCase().includes(searchVal);
                const matchTitle = item.title.toLowerCase().includes(searchVal);
                const matchAuthor = item.author.toLowerCase().includes(searchVal);
                const matchType = item.type.toLowerCase().includes(searchVal);
                return matchId || matchTitle || matchAuthor || matchType;
            }
            return true;
        });

        document.getElementById('filtered-count-badge').textContent = 'Showing ' + filtered.length + ' of ' + catalogData.length + ' items';

        if (filtered.length === 0) {
            tbody.innerHTML = '';
            emptyState.style.display = 'block';
            return;
        }

        emptyState.style.display = 'none';

        tbody.innerHTML = filtered.map(item => {
            const typeClass = item.type.toLowerCase();
            const iconBg = item.type === 'Book' ? 'var(--purple-bg)' : (item.type === 'Journal' ? 'var(--amber-bg)' : 'var(--cyan-bg)');
            const statusHtml = item.isIssued
                ? '<span class="status-pill issued"><span class="status-pill-dot"></span> Issued</span>'
                : '<span class="status-pill available"><span class="status-pill-dot"></span> Available</span>';

            const actionBtn = item.isIssued
                ? `<button class="btn btn-sm btn-emerald" onclick="openReturnModal('${item.id}')">Return</button>`
                : `<button class="btn btn-sm btn-primary" onclick="openIssueModal('${item.id}')">Issue</button>`;

            return `
                <tr>
                    <td><span class="item-id">${escapeHtml(item.id)}</span></td>
                    <td>
                        <div class="item-title-block">
                            <div class="type-icon-circle" style="background: ${iconBg};">
                                ${getIconSvg(item.type)}
                            </div>
                            <div>
                                <span class="item-title">${escapeHtml(item.title)}</span>
                                <span class="item-author">by ${escapeHtml(item.author)}</span>
                            </div>
                        </div>
                    </td>
                    <td>
                        <span class="type-pill ${typeClass}">${escapeHtml(item.type)}</span>
                    </td>
                    <td>
                        <span class="fine-policy-text">${escapeHtml(item.finePolicy)}</span>
                    </td>
                    <td>${statusHtml}</td>
                    <td class="action-cell" style="text-align: right;">${actionBtn}</td>
                </tr>
            `;
        }).join('');
    }

    function setFilter(filter, btn) {
        currentFilter = filter;
        document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
        if (btn) btn.classList.add('active');
        renderTable();
    }

    function handleSearch() {
        renderTable();
    }

    // Modal Utilities
    function openModal(id) {
        document.getElementById(id).classList.add('open');
    }

    function closeModal(id) {
        document.getElementById(id).classList.remove('open');
    }

    // ISSUE MODAL LOGIC
    function openIssueModal(preselectedId = null) {
        const select = document.getElementById('issueItemSelect');
        const manualInput = document.getElementById('issueManualId');
        const availableItems = catalogData.filter(i => !i.isIssued);

        if (availableItems.length === 0 && !preselectedId) {
            showToast('All items are currently issued!', 'error');
            return;
        }

        select.innerHTML = '<option value="">-- Choose an available item --</option>' +
            availableItems.map(i => `<option value="${i.id}">[${i.id}] ${i.title} (${i.type})</option>`).join('');

        if (preselectedId) {
            manualInput.value = preselectedId;
            select.value = preselectedId;
        } else if (availableItems.length > 0) {
            select.value = availableItems[0].id;
            manualInput.value = availableItems[0].id;
        } else {
            manualInput.value = '';
        }

        updateIssuePreview();
        openModal('issueModal');
    }

    function onIssueSelectChange() {
        const val = document.getElementById('issueItemSelect').value;
        if (val) {
            document.getElementById('issueManualId').value = val;
        }
        updateIssuePreview();
    }

    function onIssueInputManual() {
        const val = document.getElementById('issueManualId').value.trim().toUpperCase();
        document.getElementById('issueItemSelect').value = val;
        updateIssuePreview();
    }

    function updateIssuePreview() {
        const id = document.getElementById('issueManualId').value.trim();
        const box = document.getElementById('issuePreviewBox');
        const item = catalogData.find(i => i.id.toUpperCase() === id.toUpperCase());

        if (item) {
            box.style.display = 'block';
            document.getElementById('prevIssueTitle').textContent = item.title;
            document.getElementById('prevIssueAuthor').textContent = item.author;
            document.getElementById('prevIssueType').textContent = item.type;
            document.getElementById('prevIssuePolicy').textContent = item.finePolicy;
        } else {
            box.style.display = 'none';
        }
    }

    async function submitIssue() {
        const id = document.getElementById('issueManualId').value.trim();
        if (!id) {
            showToast('Please select or enter an Item ID.', 'error');
            return;
        }

        try {
            const res = await fetch('/issue', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: id })
            });
            const data = await res.json();
            if (data.success) {
                closeModal('issueModal');
                showToast(data.message, 'success');
                fetchCatalog();
            } else {
                showToast(data.message, 'error');
            }
        } catch (err) {
            showToast('Error issuing item: ' + err.message, 'error');
        }
    }

    // RETURN MODAL LOGIC
    function openReturnModal(preselectedId = null) {
        const select = document.getElementById('returnItemSelect');
        const manualInput = document.getElementById('returnManualId');
        const daysInput = document.getElementById('daysOverdueInput');
        const issuedItems = catalogData.filter(i => i.isIssued);

        select.innerHTML = '<option value="">-- Choose an issued item --</option>' +
            issuedItems.map(i => `<option value="${i.id}">[${i.id}] ${i.title} (${i.type})</option>`).join('');

        daysInput.value = 0;

        if (preselectedId) {
            manualInput.value = preselectedId;
            select.value = preselectedId;
        } else if (issuedItems.length > 0) {
            select.value = issuedItems[0].id;
            manualInput.value = issuedItems[0].id;
        } else {
            manualInput.value = '';
        }

        updateLiveFineEstimate();
        openModal('returnModal');
    }

    function onReturnSelectChange() {
        const val = document.getElementById('returnItemSelect').value;
        if (val) {
            document.getElementById('returnManualId').value = val;
        }
        updateLiveFineEstimate();
    }

    function onReturnInputManual() {
        const val = document.getElementById('returnManualId').value.trim().toUpperCase();
        document.getElementById('returnItemSelect').value = val;
        updateLiveFineEstimate();
    }

    function updateLiveFineEstimate() {
        const id = document.getElementById('returnManualId').value.trim();
        const days = Math.max(0, parseInt(document.getElementById('daysOverdueInput').value, 10) || 0);
        const item = catalogData.find(i => i.id.toUpperCase() === id.toUpperCase());

        const typeBadge = document.getElementById('calcPreviewTypeBadge');
        const formulaEl = document.getElementById('calcPreviewFormula');
        const totalEl = document.getElementById('calcPreviewTotal');

        if (!item) {
            typeBadge.textContent = 'Unknown';
            typeBadge.className = 'type-pill book';
            formulaEl.textContent = 'Enter a valid item ID to compute fine';
            totalEl.textContent = '$0.00';
            totalEl.className = 'fine-zero';
            return;
        }

        typeBadge.textContent = item.type;
        typeBadge.className = 'type-pill ' + item.type.toLowerCase();

        let fine = 0;
        let formulaText = '';

        if (item.type === 'Book') {
            fine = days * 12.0;
            formulaText = `${days} day(s) overdue × $12.00 / day`;
        } else if (item.type === 'Journal') {
            fine = days * 40.0;
            formulaText = `${days} day(s) overdue × $40.00 / day`;
        } else if (item.type === 'Multimedia') {
            fine = days > 0 ? (100.0 + (days * 15.0)) : 0.0;
            formulaText = days > 0
                ? `$100.00 base + (${days} day(s) × $15.00 / day)`
                : `Returned on time ($0.00)`;
        }

        formulaEl.textContent = 'Formula: ' + formulaText;
        totalEl.textContent = '$' + fine.toFixed(2);
        if (fine > 0) {
            totalEl.className = 'fine-highlight';
        } else {
            totalEl.className = 'fine-zero';
        }
    }

    async function submitReturn() {
        const id = document.getElementById('returnManualId').value.trim();
        const days = Math.max(0, parseInt(document.getElementById('daysOverdueInput').value, 10) || 0);

        if (!id) {
            showToast('Please select or enter an Item ID.', 'error');
            return;
        }

        try {
            const res = await fetch('/return', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: id, daysOverdue: days })
            });
            const data = await res.json();
            if (data.success) {
                closeModal('returnModal');
                fetchCatalog();

                // Open digital receipt
                showReceipt(data);
            } else {
                showToast(data.message, 'error');
            }
        } catch (err) {
            showToast('Error returning item: ' + err.message, 'error');
        }
    }

    function showReceipt(data) {
        const isOverdue = data.daysOverdue > 0;
        document.getElementById('rcptBadge').textContent = isOverdue ? 'DELAYED RETURN RECEIPT' : 'ON-TIME RETURN RECEIPT';
        document.getElementById('rcptBadge').style.color = isOverdue ? 'var(--rose-text)' : 'var(--emerald-text)';
        document.getElementById('rcptBadge').style.background = isOverdue ? 'var(--rose-bg)' : 'var(--emerald-bg)';

        document.getElementById('rcptTitle').textContent = data.item ? data.item.title : 'Library Return';
        document.getElementById('rcptTime').textContent = new Date().toLocaleString();

        document.getElementById('rcptItemId').textContent = data.item.id;
        document.getElementById('rcptItemTitle').textContent = data.item.title;
        document.getElementById('rcptItemType').textContent = data.item.type;
        document.getElementById('rcptAuthor').textContent = data.item.author;
        document.getElementById('rcptDaysOverdue').textContent = data.daysOverdue + (data.daysOverdue === 1 ? ' day' : ' days');
        document.getElementById('rcptPolicy').textContent = data.finePolicy;

        const fineEl = document.getElementById('rcptFineDue');
        fineEl.textContent = data.formattedFine;
        if (data.fine > 0) {
            fineEl.className = 'fine-amount';
        } else {
            fineEl.className = 'fine-amount zero';
        }

        openModal('receiptModal');
    }

    function openPolicyModal() {
        openModal('policyModal');
    }

    // Helper: Toast Notifications
    function showToast(msg, type = 'info') {
        const container = document.getElementById('toast-container');
        const toast = document.createElement('div');
        toast.className = 'toast ' + type;
        toast.innerHTML = `
            <div style="flex: 1;">${escapeHtml(msg)}</div>
        `;
        container.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(20px)';
            toast.style.transition = 'all 0.25s ease-out';
            setTimeout(() => toast.remove(), 250);
        }, 3500);
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // Initial load
    fetchCatalog();
</script>

</body>
</html>
""";
}
