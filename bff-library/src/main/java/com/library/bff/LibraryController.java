package com.library.bff;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api")
public class LibraryController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${USERS_AZURE_URL}")
    private String usersUrl;

    @Value("${LOANS_AZURE_URL}")
    private String loansUrl;

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
}
