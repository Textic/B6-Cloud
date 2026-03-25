package com.library.faas.prestamos;

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

public class PrestamosFunction {

    private final String dbUrl = System.getenv("DB_URL");
    private final String dbUser = System.getenv("DB_USER");
    private final String dbPass = System.getenv("DB_PASS");

    @FunctionName("prestamos")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS, route = "prestamos") 
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        String method = request.getHttpMethod().toString();

        try {
            if (method.equalsIgnoreCase("GET")) {
                List<String> prestamos = listarPrestamos();
                return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body("[" + String.join(",", prestamos) + "]")
                    .build();
            } else if (method.equalsIgnoreCase("POST")) {
                String body = request.getBody().orElse("");
                crearPrestamo(body);
                return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body("{\"mensaje\":\"Préstamo registrado exitosamente en Azure\"}")
                    .build();
            }
            return request.createResponseBuilder(HttpStatus.METHOD_NOT_ALLOWED).build();
        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\":\"Error interno del servidor en Azure: " + e.getMessage() + "\"}")
                .build();
        }
    }

    private List<String> listarPrestamos() throws SQLException {
        List<String> lista = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM PRESTAMOS")) {
            while (rs.next()) {
                lista.add(String.format("{\"id\":%d,\"id_usuario\":%d,\"id_libro\":%d,\"fecha\":\"%s\",\"estado\":\"%s\"}",
                        rs.getInt("id"), rs.getInt("id_usuario"), rs.getInt("id_libro"), rs.getString("fecha_prestamo"), rs.getString("estado")));
            }
        }
        return lista;
    }

    private void crearPrestamo(String json) throws SQLException {
        int idUsuario = Integer.parseInt(extraerDatoSimple(json, "id_usuario"));
        int idLibro = Integer.parseInt(extraerDatoSimple(json, "id_libro"));
        String estado = extraerDatoString(json, "estado");
        String sql = "INSERT INTO PRESTAMOS (id_usuario, id_libro, estado) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            pstmt.setInt(2, idLibro);
            pstmt.setString(3, estado);
            pstmt.executeUpdate();
        }
    }

    private String extraerDatoString(String json, String llave) {
        String busqueda = "\"" + llave + "\"";
        int posLlave = json.indexOf(busqueda);
        int posDosPuntos = json.indexOf(":", posLlave + busqueda.length());
        int posInicio = json.indexOf("\"", posDosPuntos) + 1;
        int posFin = json.indexOf("\"", posInicio);
        return json.substring(posInicio, posFin);
    }

    private String extraerDatoSimple(String json, String llave) {
        String busqueda = "\"" + llave + "\"";
        int posLlave = json.indexOf(busqueda);
        int posDosPuntos = json.indexOf(":", posLlave + busqueda.length());
        int posInicio = posDosPuntos + 1;
        int posFin = json.indexOf(",", posInicio);
        if (posFin == -1) posFin = json.indexOf("}", posInicio);
        return json.substring(posInicio, posFin).trim();
    }
}
