package cr.ac.ucr.if0009.insecurepay.service;

import cr.ac.ucr.if0009.insecurepay.dto.CuentaDto;
import cr.ac.ucr.if0009.insecurepay.exception.*;
import cr.ac.ucr.if0009.insecurepay.model.*;
import cr.ac.ucr.if0009.insecurepay.repository.CuentaRepository;
import cr.ac.ucr.if0009.insecurepay.repository.UsuarioRepository;
import cr.ac.ucr.if0009.insecurepay.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CuentaService {
    private final CuentaRepository cuentaRepository;
    private final UsuarioRepository usuarioRepository;
    private final JwtUtil jwtUtil;

    public CuentaService(CuentaRepository cuentaRepository, UsuarioRepository usuarioRepository, JwtUtil jwtUtil) {
        this.cuentaRepository = cuentaRepository;
        this.usuarioRepository = usuarioRepository;
        this.jwtUtil = jwtUtil;
    }

    public CuentaDto obtenerCuentaSegura(Long cuentaId, String authHeader) {
        Usuario usuario = autenticar(authHeader);
        CuentaBancaria cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La cuenta solicitada no existe."));
        if (!cuenta.getUsuario().getUsername().equals(usuario.getUsername()) && usuario.getRol() != Rol.ADMIN) {
            throw new AccesoDenegadoException("No tiene permiso para acceder a la cuenta solicitada.");
        }
        return convertirADto(cuenta);
    }

    public List<CuentaDto> listarTodas(String authHeader) {
        Usuario usuario = autenticar(authHeader);
        if (usuario.getRol() != Rol.ADMIN) {
            throw new AccesoDenegadoException("No tiene permiso para listar todas las cuentas.");
        }
        return cuentaRepository.findAll().stream().map(this::convertirADto).toList();
    }

    public List<CuentaDto> obtenerPorUsuario(Long usuarioId, String authHeader) {
        Usuario usuario = autenticar(authHeader);
        if (!usuario.getId().equals(usuarioId) && usuario.getRol() != Rol.ADMIN) {
            throw new AccesoDenegadoException("No tiene permiso para consultar las cuentas solicitadas.");
        }
        return cuentaRepository.findByUsuarioId(usuarioId).stream().map(this::convertirADto).toList();
    }

    private Usuario autenticar(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ") || authHeader.substring(7).isBlank()) {
            throw new NoAutenticadoException("Se requiere un token Bearer valido.");
        }
        String username;
        try {
            var claims = jwtUtil.getClaims(authHeader.substring(7));
            username = claims.getSubject();
            if (username == null || username.isBlank() || claims.getExpiration() == null) {
                throw new NoAutenticadoException("La sesion no es valida.");
            }
        } catch (JwtException | IllegalArgumentException ex) {
            throw new NoAutenticadoException("La sesion no es valida o ha expirado.");
        }
        return usuarioRepository.findByUsername(username)
                .filter(usuario -> usuario.getUsername().equals(username))
                .orElseThrow(() -> new NoAutenticadoException("La sesion no es valida."));
    }

    private CuentaDto convertirADto(CuentaBancaria cuenta) {
        CuentaDto dto = new CuentaDto();
        dto.setId(cuenta.getId());
        dto.setUsuarioId(cuenta.getUsuario().getId());
        dto.setTitular(cuenta.getUsuario().getNombreCompleto());
        dto.setNumeroCuenta(cuenta.getNumeroCuenta());
        dto.setTipoCuenta(cuenta.getTipoCuenta().name());
        dto.setSaldo(cuenta.getSaldo());
        dto.setMoneda(cuenta.getMoneda());
        dto.setActiva(cuenta.getActiva());
        return dto;
    }
}
