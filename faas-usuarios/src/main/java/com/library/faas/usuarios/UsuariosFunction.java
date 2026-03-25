package com.library.faas.usuarios;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UsuariosFunction {

    private final String dbUrl = System.getenv("DB_URL");
    private final String dbUser = System.getenv("DB_USER");
    private final String dbPass = System.getenv("DB_PASS");

    @FunctionName("usuarios")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS, route = "usuarios") 
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        String method = request.getHttpMethod().toString();
        context.getLogger().info("Ejecutando función de usuarios: " + method);

        try {
            if (method.equalsIgnoreCase("GET")) {
                List<String> usuarios = listarUsuarios();
                return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body("[" + String.join(",", usuarios) + "]")
                    .build();
            } else if (method.equalsIgnoreCase("POST")) {
                String body = request.getBody().orElse("");
                crearUsuario(body);
                return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body("{\"mensaje\":\"Usuario creado exitosamente en Azure\"}")
                    .build();
            }
            return request.createResponseBuilder(HttpStatus.METHOD_NOT_ALLOWED).build();
        } catch (Exception e) {
            context.getLogger().severe("Error procesando usuarios: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\":\"Error interno del servidor en Azure: " + e.getMessage() + "\"}")
                .build();
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
}
