package cr.ac.ucr.if0009.insecurepay;

import com.fasterxml.jackson.databind.ObjectMapper;
import cr.ac.ucr.if0009.insecurepay.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:control_acceso_test;DB_CLOSE_DELAY=-1",
        "jwt.secret=ClaveSoloParaPruebasAutomatizadas1234567890"
})
@AutoConfigureMockMvc
class ControlAccesoTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtUtil jwt;

    private String login(String username, String password) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("username", username, "password", password));
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void bloqueaCuentaAjenaSinFiltrarDatos() throws Exception {
        String auth = login("carlos_mora", "carlos123");
        for (String endpoint : new String[]{"/api/cuentas/3", "/api/cuentas/3/saldo"}) {
            mvc.perform(get(endpoint).header("Authorization", auth))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Acceso no autorizado a recurso ajeno."))
                    .andExpect(jsonPath("$.type").value("about:blank"))
                    .andExpect(jsonPath("$.detail").isString())
                    .andExpect(jsonPath("$.saldo").doesNotExist())
                    .andExpect(jsonPath("$.trace").doesNotExist())
                    .andExpect(content().string(not(containsString("4850000"))))
                    .andExpect(content().string(not(containsString("maria_rojas"))));
        }
    }

    @Test
    void permiteCuentaPropiaYSaldo() throws Exception {
        String auth = login("carlos_mora", "carlos123");
        mvc.perform(get("/api/cuentas/2").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(3));
        mvc.perform(get("/api/cuentas/2/saldo").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cuentaId").value(2))
                .andExpect(jsonPath("$.saldo").value(250000)).andExpect(jsonPath("$.moneda").value("CRC"));
    }

    @Test
    void permiteAdministrador() throws Exception {
        mvc.perform(get("/api/cuentas/3/saldo").header("Authorization", login("admin", "admin123")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(4850000));
    }

    @Test
    void auditorNoEsAdministrador() throws Exception {
        mvc.perform(get("/api/cuentas/3/saldo")
                        .header("Authorization", login("auditor_seguridad", "auditor2026")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rechazaTokenAusenteOMalformado() throws Exception {
        mvc.perform(get("/api/cuentas/2/saldo"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
        for (String header : new String[]{"Basic abc", "Bearer ", "Bearer no-es-jwt"}) {
            mvc.perform(get("/api/cuentas/2").header("Authorization", header))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
    }

    @Test
    void rechazaFirmaAjena() throws Exception {
        String token = Jwts.builder().subject("admin").expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Jwts.SIG.HS256.key().build()).compact();
        mvc.perform(get("/api/cuentas/3/saldo").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaTokenExpiradoOSinExpiracion() throws Exception {
        var key = Keys.hmacShaKeyFor("ClaveSoloParaPruebasAutomatizadas1234567890".getBytes(StandardCharsets.UTF_8));
        for (String token : new String[]{
                Jwts.builder().subject("carlos_mora").expiration(new Date(1000)).signWith(key).compact(),
                Jwts.builder().subject("carlos_mora").signWith(key).compact()}) {
            mvc.perform(get("/api/cuentas/2/saldo").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void rolEIdentidadProcedenDelUsuarioReal() throws Exception {
        String token = jwt.generateToken("carlos_mora", "ADMIN", 1L);
        mvc.perform(get("/api/cuentas/3/saldo").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/cuentas/2/saldo")
                        .header("Authorization", "Bearer " + jwt.generateToken("CARLOS_MORA", "CLIENTE", 3L)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cuentas/2/saldo")
                        .header("Authorization", "Bearer " + jwt.generateToken("inexistente", "ADMIN", 1L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cuentaInexistenteRetorna404TrasAutenticar() throws Exception {
        mvc.perform(get("/api/cuentas/999/saldo").header("Authorization", login("carlos_mora", "carlos123")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/api/cuentas/999/saldo")).andExpect(status().isUnauthorized());
    }

    @Test
    void listadosNoPermitenEvitarControlDePropiedad() throws Exception {
        String auth = login("carlos_mora", "carlos123");
        mvc.perform(get("/api/cuentas").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/cuentas/usuario/4").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/cuentas/usuario/3").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(2));
    }

    @Test
    void credencialesInvalidasRetornan401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carlos_mora\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.trace").doesNotExist());
    }
}
