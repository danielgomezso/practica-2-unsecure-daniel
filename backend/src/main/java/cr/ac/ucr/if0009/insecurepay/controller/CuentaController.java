package cr.ac.ucr.if0009.insecurepay.controller;

import cr.ac.ucr.if0009.insecurepay.dto.CuentaDto;
import cr.ac.ucr.if0009.insecurepay.service.CuentaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/cuentas")
public class CuentaController {
    private final CuentaService cuentaService;

    public CuentaController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @GetMapping
    public ResponseEntity<List<CuentaDto>> listarTodas(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ResponseEntity.ok(cuentaService.listarTodas(authHeader));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CuentaDto> obtenerPorId(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ResponseEntity.ok(cuentaService.obtenerCuentaSegura(id, authHeader));
    }

    @GetMapping("/{id}/saldo")
    public ResponseEntity<SaldoResponse> obtenerSaldo(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        CuentaDto cuenta = cuentaService.obtenerCuentaSegura(id, authHeader);
        return ResponseEntity.ok(new SaldoResponse(cuenta.getId(), cuenta.getSaldo(), cuenta.getMoneda()));
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<CuentaDto>> obtenerPorUsuario(@PathVariable Long usuarioId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ResponseEntity.ok(cuentaService.obtenerPorUsuario(usuarioId, authHeader));
    }

    public record SaldoResponse(Long cuentaId, BigDecimal saldo, String moneda) {}
}
