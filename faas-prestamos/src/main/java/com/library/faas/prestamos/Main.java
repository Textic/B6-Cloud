package com.library.faas.prestamos;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {
        int puerto = Integer.parseInt(System.getenv().getOrDefault("PUERTO", "8082"));
        HttpServer server = HttpServer.create(new InetSocketAddress(puerto), 0);
        server.createContext("/prestamos", new PrestamosHandler());
        server.setExecutor(null);
        System.out.println("Función de Préstamos iniciada en el puerto: " + puerto);
        server.start();
    }

    static class PrestamosHandler implements HttpHandler {
        private final String dbUrl = System.getenv("DB_URL");
        private final String dbUser = System.getenv("DB_USER");
        private final String dbPass = System.getenv("DB_PASS");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            try {
                if ("GET".equalsIgnoreCase(method)) {
                    List<String> prestamos = listarPrestamos();
                    responder(exchange, 200, "[" + String.join(",", prestamos) + "]");
                } else if ("POST".equalsIgnoreCase(method)) {
                    String body = leerBody(exchange.getRequestBody());
                    crearPrestamo(body);
                    responder(exchange, 201, "{\"mensaje\":\"Préstamo registrado exitosamente\"}");
                } else {
                    responder(exchange, 405, "{\"error\":\"Método no permitido\"}");
                }
            } catch (Exception e) {
                responder(exchange, 500, "{\"error\":\"Error interno del servidor: " + e.getMessage() + "\"}");
            }
        }

        private List<String> listarPrestamos() throws SQLException {
            List<String> lista = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM Prestamos")) {
                while (rs.next()) {
                    lista.add(String.format("{\"id\":%d,\"id_usuario\":%d,\"id_libro\":%d,\"fecha\":\"%s\",\"estado\":\"%s\"}",
                            rs.getInt("id"), rs.getInt("id_usuario"), rs.getInt("id_libro"), rs.getString("fecha_prestamo"), rs.getString("estado")));
                }
            }
            return lista;
        }

        private void crearPrestamo(String json) throws SQLException {
            int idUsuario = Integer.parseInt(buscarValorSimple(json, "id_usuario"));
            int idLibro = Integer.parseInt(buscarValorSimple(json, "id_libro"));
            String estado = buscarValorString(json, "estado");
            String sql = "INSERT INTO Prestamos (id_usuario, id_libro, estado) VALUES (?, ?, ?)";
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, idUsuario);
                pstmt.setInt(2, idLibro);
                pstmt.setString(3, estado);
                pstmt.executeUpdate();
            }
        }

        private String buscarValorSimple(String json, String key) {
            int start = json.indexOf("\"" + key + "\"") + key.length() + 3;
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            return json.substring(start, end).trim();
        }

        private String buscarValorString(String json, String key) {
            int start = json.indexOf("\"" + key + "\"") + key.length() + 4;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }

        private String leerBody(InputStream is) throws IOException {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        }

        private void responder(HttpExchange exchange, int code, String response) throws IOException {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
