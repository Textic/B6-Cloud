package com.library.faas.usuarios;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.EventGridTrigger;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.azure.messaging.eventgrid.EventGridPublisherClientBuilder;
import com.azure.core.util.BinaryData;
import com.azure.core.credential.AzureKeyCredential;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UsuariosFunction {

    private final String dbUrl = System.getenv("DB_URL");
    private final String dbUser = System.getenv("DB_USER");
    private final String dbPass = System.getenv("DB_PASS");
    private final String eventGridEndpoint = System.getenv("EVENT_GRID_TOPIC_ENDPOINT");
    private final String eventGridKey = System.getenv("EVENT_GRID_TOPIC_KEY");

    @FunctionName("notificarPrestamo")
    public void notificarPrestamo(
            @EventGridTrigger(name = "event") EventGridEvent event,
            final ExecutionContext context) {
        
        context.getLogger().info("Evento recibido de Event Grid!");
        
        if ("Biblioteca.Prestamo.Creado".equals(event.getEventType())) {
            String payload = event.getData().toString();
            context.getLogger().info("Procesando préstamo: " + payload);
            
            String idUsuario = extraerDatoGenerico(payload, "id_usuario");
            String idLibro = extraerDatoGenerico(payload, "id_libro");
            
            context.getLogger().info("SIMULACIÓN: Enviando correo al usuario " + idUsuario + 
                " por el préstamo del libro " + idLibro);
        }
    }

    private String extraerDatoGenerico(String json, String llave) {
        try {
            String busqueda = "\"" + llave + "\"";
            int posLlave = json.indexOf(busqueda);
            if (posLlave == -1) return "Desconocido";
            int posDosPuntos = json.indexOf(":", posLlave + busqueda.length());
            int posInicio = posDosPuntos + 1;
            int posFin = json.indexOf(",", posInicio);
            if (posFin == -1) posFin = json.indexOf("}", posInicio);
            return json.substring(posInicio, posFin).replace("\"", "").trim();
        } catch (Exception e) {
            return "Error";
        }
    }

    @FunctionName("usuarios")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE}, authLevel = AuthorizationLevel.ANONYMOUS, route = "usuarios") 
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        String method = request.getHttpMethod().toString();
        String idParam = request.getQueryParameters().get("id");

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
            } else if (method.equalsIgnoreCase("DELETE") && idParam != null) {
                eliminarUsuario(Integer.parseInt(idParam), context);
                return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body("{\"mensaje\":\"Usuario eliminado y evento de limpieza enviado\"}")
                    .build();
            }
            return request.createResponseBuilder(HttpStatus.METHOD_NOT_ALLOWED).build();
        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\":\"Error interno del servidor en Azure: " + e.getMessage() + "\"}")
                .build();
        }
    }

    private void eliminarUsuario(int id, ExecutionContext context) throws SQLException {
        String sql = "DELETE FROM USUARIOS WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                enviarEventoEliminacion(id, context);
            }
        }
    }

    private void enviarEventoEliminacion(int idUsuario, ExecutionContext context) {
        try {
            EventGridPublisherClient<EventGridEvent> client = new EventGridPublisherClientBuilder()
                .endpoint(eventGridEndpoint)
                .credential(new AzureKeyCredential(eventGridKey))
                .buildEventGridEventPublisherClient();

            EventGridEvent event = new EventGridEvent(
                "Biblioteca/Usuarios",
                "Biblioteca.Usuario.Eliminado",
                BinaryData.fromString("{\"id_usuario\":" + idUsuario + "}"),
                "1.0"
            );

            client.sendEvent(event);
            context.getLogger().info("Evento enviado: Biblioteca.Usuario.Eliminado para ID " + idUsuario);
        } catch (Exception e) {
            context.getLogger().severe("Error al enviar evento de eliminación: " + e.getMessage());
        }
    }

    private List<String> listarUsuarios() throws SQLException {
        List<String> lista = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM USUARIOS")) {
            while (rs.next()) {
                lista.add(String.format("{\"id\":%d,\"nombre\":\"%s\",\"rut\":\"%s\",\"correo\":\"%s\"}",
                        rs.getInt("id"), rs.getString("nombre"), rs.getString("rut"), rs.getString("correo")));
            }
        }
        return lista;
    }

    private void crearUsuario(String json) throws SQLException {
        String nombre = extraerDato(json, "nombre");
        String rut = extraerDato(json, "rut");
        String correo = extraerDato(json, "correo");
        String sql = "INSERT INTO USUARIOS (nombre, rut, correo) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombre);
            pstmt.setString(2, rut);
            pstmt.setString(3, correo);
            pstmt.executeUpdate();
        }
    }

    private String extraerDato(String json, String llave) {
        String busqueda = "\"" + llave + "\"";
        int posLlave = json.indexOf(busqueda);
        int posDosPuntos = json.indexOf(":", posLlave + busqueda.length());
        int posInicio = json.indexOf("\"", posDosPuntos) + 1;
        int posFin = json.indexOf("\"", posInicio);
        return json.substring(posInicio, posFin);
    }
}
