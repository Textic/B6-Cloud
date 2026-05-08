package com.library.faas.libros;

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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;

public class LibrosFunction {

    private final String dbUrl = System.getenv("DB_URL");
    private final String dbUser = System.getenv("DB_USER");
    private final String dbPass = System.getenv("DB_PASS");

    private static GraphQL graphQL;

    public LibrosFunction() {
        if (graphQL == null) {
            initGraphQL();
        }
    }

    private void initGraphQL() {
        String schema = "type Query { listarLibros: [Libro] }" +
                        "type Mutation { " +
                        "  crearLibro(titulo: String!, id_autor: Int!, disponibilidad: Int): Libro, " +
                        "  actualizarDisponibilidad(id: Int!, disponibilidad: Int!): Libro " +
                        "}" +
                        "type Libro { id: Int, titulo: String, id_autor: Int, disponibilidad: Int }";

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeRegistry = schemaParser.parse(schema);

        RuntimeWiring wiring = newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("listarLibros", env -> listarLibros()))
                .type("Mutation", builder -> builder
                    .dataFetcher("crearLibro", env -> {
                        String titulo = env.getArgument("titulo");
                        Integer idAutor = env.getArgument("id_autor");
                        Integer disponibilidad = env.getArgument("disponibilidad");
                        return crearLibro(titulo, idAutor, disponibilidad != null ? disponibilidad : 10);
                    })
                    .dataFetcher("actualizarDisponibilidad", env -> {
                        int id = env.getArgument("id");
                        int disp = env.getArgument("disponibilidad");
                        return actualizarLibroDisp(id, disp);
                    }))
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
        graphQL = GraphQL.newGraphQL(graphQLSchema).build();
    }

    @FunctionName("libros")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS, route = "graphql") 
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        try {
            String body = request.getBody().orElse("{}");
            String query = extraerQuery(body);

            ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                    .query(query)
                    .build();

            ExecutionResult executionResult = graphQL.execute(executionInput);
            Map<String, Object> resultMap = executionResult.toSpecification();
            String jsonResult = mapToJson(resultMap);

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body(jsonResult)
                    .build();

        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"errors\":[{\"message\":\"" + e.getMessage() + "\"}]}")
                    .build();
        }
    }

    @FunctionName("procesarPrestamo")
    public void procesarPrestamo(
            @EventGridTrigger(name = "event") EventGridEvent event,
            final ExecutionContext context) {
        
        if ("Biblioteca.Prestamo.Creado".equals(event.getEventType())) {
            String payload = event.getData().toString();
            String idLibro = extraerDatoGenerico(payload, "id_libro");
            context.getLogger().info("Restando disponibilidad para libro ID: " + idLibro);
            
            String sql = "UPDATE LIBROS SET disponibilidad = disponibilidad - 1 WHERE id = ?";
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, Integer.parseInt(idLibro));
                pstmt.executeUpdate();
            } catch (Exception e) {
                context.getLogger().severe("Error al actualizar disponibilidad: " + e.getMessage());
            }
        }
    }

    private String extraerDatoGenerico(String json, String llave) {
        try {
            String busqueda = "\"" + llave + "\"";
            int posLlave = json.indexOf(busqueda);
            if (posLlave == -1) return "0";
            int posDosPuntos = json.indexOf(":", posLlave + busqueda.length());
            int posInicio = posDosPuntos + 1;
            int posFin = json.indexOf(",", posInicio);
            if (posFin == -1) posFin = json.indexOf("}", posInicio);
            return json.substring(posInicio, posFin).replace("\"", "").trim();
        } catch (Exception e) {
            return "0";
        }
    }

    private List<Map<String, Object>> listarLibros() throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM LIBROS")) {
            while (rs.next()) {
                Map<String, Object> libro = new HashMap<>();
                libro.put("id", rs.getInt("id"));
                libro.put("titulo", rs.getString("titulo"));
                libro.put("id_autor", rs.getInt("id_autor"));
                libro.put("disponibilidad", rs.getInt("disponibilidad"));
                lista.add(libro);
            }
        }
        return lista;
    }

    private Map<String, Object> actualizarLibroDisp(int id, int disp) throws SQLException {
        String sql = "UPDATE LIBROS SET disponibilidad = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, disp);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
            
            Map<String, Object> libro = new HashMap<>();
            libro.put("id", id);
            libro.put("disponibilidad", disp);
            return libro;
        }
    }

    private Map<String, Object> crearLibro(String titulo, Integer idAutor, Integer disponibilidad) throws SQLException {
        String sql = "INSERT INTO LIBROS (titulo, id_autor, disponibilidad) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             PreparedStatement pstmt = conn.prepareStatement(sql, new String[]{"ID"})) {
            pstmt.setString(1, titulo);
            pstmt.setInt(2, idAutor);
            pstmt.setInt(3, disponibilidad);
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    Map<String, Object> libro = new HashMap<>();
                    libro.put("id", generatedKeys.getInt(1));
                    libro.put("titulo", titulo);
                    libro.put("id_autor", idAutor);
                    libro.put("disponibilidad", disponibilidad);
                    return libro;
                }
            }
        }
        return null;
    }

    private String extraerQuery(String json) {
        String busqueda = "\"query\"";
        int posLlave = json.indexOf(busqueda);
        int posDosPuntos = json.indexOf(":", posLlave + busqueda.length());
        int posInicio = json.indexOf("\"", posDosPuntos) + 1;
        int posFin = json.lastIndexOf("\"");
        return json.substring(posInicio, posFin).replace("\\\"", "\"").replace("\\n", "\n");
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof Map) {
                sb.append(mapToJson((Map<String, Object>) value));
            } else if (value instanceof List) {
                sb.append(listToJson((List<Object>) value));
            } else if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value == null) {
                sb.append("null");
            } else {
                sb.append(value);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String listToJson(List<Object> list) {
        StringBuilder sb = new StringBuilder("[");
        for (Object item : list) {
            if (sb.length() > 1) sb.append(",");
            if (item instanceof Map) {
                sb.append(mapToJson((Map<String, Object>) item));
            } else if (item instanceof String) {
                sb.append("\"").append(item).append("\"");
            } else {
                sb.append(item);
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
