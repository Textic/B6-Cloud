package com.library.bff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LibraryController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${USERS_AZURE_URL}")
    private String usersUrl;

    @Value("${LOANS_AZURE_URL}")
    private String loansUrl;

    @Value("${AUTHORS_AZURE_URL}")
    private String authorsUrl;

    @Value("${BOOKS_AZURE_URL}")
    private String booksUrl;

    @GetMapping("/usuarios")
    public ResponseEntity<String> obtenerUsuarios() {
        try {
            return restTemplate.getForEntity(usersUrl + "/api/usuarios", String.class);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"Falla al obtener usuarios de Azure: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/usuarios")
    public ResponseEntity<String> crearUsuario(@RequestBody String usuarioJson) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(usuarioJson, headers);
            return restTemplate.postForEntity(usersUrl + "/api/usuarios", entity, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"Falla al crear usuario en Azure: " + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/prestamos")
    public ResponseEntity<String> obtenerPrestamos() {
        try {
            return restTemplate.getForEntity(loansUrl + "/api/prestamos", String.class);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"Falla al obtener préstamos de Azure: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/prestamos")
    public ResponseEntity<String> crearPrestamo(@RequestBody String prestamoJson) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(prestamoJson, headers);
            return restTemplate.postForEntity(loansUrl + "/api/prestamos", entity, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"Falla al registrar préstamo en Azure: " + e.getMessage() + "\"}");
        }
    }

    // --- NUEVOS ENDPOINTS PARA AUTORES (GRAPHQL) ---

    @GetMapping("/autores")
    public ResponseEntity<String> obtenerAutores() {
        try {
            String gqlQuery = "{\"query\": \"{ listarAutores { id nombre nacionalidad } }\"}";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(gqlQuery, headers);
            return restTemplate.postForEntity(authorsUrl + "/api/graphql", entity, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"Falla al obtener autores de Azure (GraphQL): " + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/autores")
    public ResponseEntity<String> crearAutor(@RequestBody String autorJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(autorJson);
            String nombre = rootNode.get("nombre").asText();
            String nacionalidad = rootNode.has("nacionalidad") ? rootNode.get("nacionalidad").asText() : "";
            
            String gqlMutation = String.format(
                "{\"query\": \"mutation { crearAutor(nombre: \\\"%s\\\", nacionalidad: \\\"%s\\\") { id nombre nacionalidad } }\"}",
                nombre, nacionalidad
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(gqlMutation, headers);
            return restTemplate.postForEntity(authorsUrl + "/api/graphql", entity, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"Falla al crear autor en Azure (GraphQL): " + e.getMessage() + "\"}");
        }
    }

    // --- NUEVOS ENDPOINTS PARA LIBROS (GRAPHQL) ---

    @GetMapping("/libros")
    public ResponseEntity<String> obtenerLibros() {
        try {
            String gqlQuery = "{\"query\": \"{ listarLibros { id titulo id_autor } }\"}";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(gqlQuery, headers);
            return restTemplate.postForEntity(booksUrl + "/api/graphql", entity, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"Falla al obtener libros de Azure (GraphQL): " + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/libros")
    public ResponseEntity<String> crearLibro(@RequestBody String libroJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(libroJson);
            String titulo = rootNode.get("titulo").asText();
            int idAutor = rootNode.get("id_autor").asInt();
            
            String gqlMutation = String.format(
                "{\"query\": \"mutation { crearLibro(titulo: \\\"%s\\\", id_autor: %d) { id titulo id_autor } }\"}",
                titulo, idAutor
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(gqlMutation, headers);
            return restTemplate.postForEntity(booksUrl + "/api/graphql", entity, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"Falla al crear libro en Azure (GraphQL): " + e.getMessage() + "\"}");
        }
    }
}
