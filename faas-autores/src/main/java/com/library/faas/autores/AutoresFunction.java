package com.library.faas.autores;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
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

public class AutoresFunction {

    private final String dbUrl = System.getenv("DB_URL");
    private final String dbUser = System.getenv("DB_USER");
    private final String dbPass = System.getenv("DB_PASS");

    private static GraphQL graphQL;

    public AutoresFunction() {
        if (graphQL == null) {
            initGraphQL();
        }
    }

    // Configuración del esquema GraphQL de forma programática usando SDL
    private void initGraphQL() {
        String schema = "type Query { listarAutores: [Autor] }" +
                        "type Mutation { crearAutor(nombre: String!, nacionalidad: String): Autor }" +
                        "type Autor { id: Int, nombre: String, nacionalidad: String }";

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeRegistry = schemaParser.parse(schema);

        RuntimeWiring wiring = newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("listarAutores", env -> listarAutores()))
                .type("Mutation", builder -> builder.dataFetcher("crearAutor", env -> {
                    String nombre = env.getArgument("nombre");
                    String nacionalidad = env.getArgument("nacionalidad");
                    return crearAutor(nombre, nacionalidad);
                }))
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
        graphQL = GraphQL.newGraphQL(graphQLSchema).build();
    }

    @FunctionName("autores")
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
            
            // Retornamos el resultado como JSON
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

    private List<Map<String, Object>> listarAutores() throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM AUTORES")) {
            while (rs.next()) {
                Map<String, Object> autor = new HashMap<>();
                autor.put("id", rs.getInt("id"));
                autor.put("nombre", rs.getString("nombre"));
                autor.put("nacionalidad", rs.getString("nacionalidad"));
                lista.add(autor);
            }
        }
        return lista;
    }

    private Map<String, Object> crearAutor(String nombre, String nacionalidad) throws SQLException {
        String sql = "INSERT INTO AUTORES (nombre, nacionalidad) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             PreparedStatement pstmt = conn.prepareStatement(sql, new String[]{"ID"})) {
            pstmt.setString(1, nombre);
            pstmt.setString(2, nacionalidad);
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    Map<String, Object> autor = new HashMap<>();
                    autor.put("id", generatedKeys.getInt(1));
                    autor.put("nombre", nombre);
                    autor.put("nacionalidad", nacionalidad);
                    return autor;
                }
            }
        }
        return null;
    }

    // Método manual para extraer la query del JSON sin librerías externas
    private String extraerQuery(String json) {
        String busqueda = "\"query\"";
        int posLlave = json.indexOf(busqueda);
        int posDosPuntos = json.indexOf(":", posLlave + busqueda.length());
        int posInicio = json.indexOf("\"", posDosPuntos) + 1;
        int posFin = json.lastIndexOf("\"");
        // Manejo básico de escape de comillas si vienen en la query
        return json.substring(posInicio, posFin).replace("\\\"", "\"").replace("\\n", "\n");
    }

    // Conversión manual básica de Map a JSON para mantener la función ligera
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
