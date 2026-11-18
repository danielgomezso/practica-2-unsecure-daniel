package cr.ac.ucr.if0009.insecurepay.exception;

public class NoAutenticadoException extends RuntimeException {
    public NoAutenticadoException(String mensaje) {
        super(mensaje);
    }
}
