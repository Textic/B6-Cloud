package com.library.faas.usuarios;

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
        int puerto = Integer.parseInt(System.getenv().getOrDefault("PUERTO", "8081"));
        HttpServer server = HttpServer.create(new InetSocketAddress(puerto), 0);
        server.createContext("/usuarios", new UsuariosHandler());
        server.setExecutor(null);
        System.out.println("Función de Usuarios iniciada en el puerto: " + puerto);
        server.start();
    }

    static class UsuariosHandler implements HttpHandler {
        private final String dbUrl = System.getenv("DB_URL");
        private final String dbUser = System.getenv("DB_USER");
        private final String dbPass = System.getenv("DB_PASS");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            try {
                if ("GET".equalsIgnoreCase(method)) {
                    List<String> usuarios = listarUsuarios();
                    responder(exchange, 200, "[" + String.join(",", usuarios) + "]");
                } else if ("POST".equalsIgnoreCase(method)) {
                    String body = leerBody(exchange.getRequestBody());
                    crearUsuario(body);
                    responder(exchange, 201, "{\"mensaje\":\"Usuario creado exitosamente\"}");
                } else {
                    responder(exchange, 405, "{\"error\":\"Método no permitido\"}");
                }
            } catch (Exception e) {
                responder(exchange, 500, "{\"error\":\"Error interno del servidor: " + e.getMessage() + "\"}");
            }
        }

        private List<String> listarUsuarios() throws SQLException {
            List<String> lista = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM Usuarios")) {
                while (rs.next()) {
                    lista.add(String.format("{\"id\":%d,\"nombre\":\"%s\",\"rut\":\"%s\",\"correo\":\"%s\"}",
                            rs.getInt("id"), rs.getString("nombre"), rs.getString("rut"), rs.getString("correo")));
                }
            }
            return lista;
        }

        private void crearUsuario(String json) throws SQLException {
            String nombre = buscarValor(json, "nombre");
            String rut = buscarValor(json, "rut");
            String correo = buscarValor(json, "correo");
            String sql = "INSERT INTO Usuarios (nombre, rut, correo) VALUES (?, ?, ?)";
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, nombre);
                pstmt.setString(2, rut);
                pstmt.setString(3, correo);
                pstmt.executeUpdate();
            }
        }

        private String buscarValor(String json, String key) {
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
