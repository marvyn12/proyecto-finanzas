package mx.ipn.finanzas.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

import java.sql.SQLException;
import java.util.Map;

/**
 * Manejo de errores para toda la aplicación: en lugar de la página blanca de Spring ("Whitelabel Error Page")
 * se muestra un mensaje entendible. Las validaciones normales (IllegalArgumentException) NO llegan aquí:
 * las atrapa cada método del controller y las muestra como aviso rojo.
 */
@ControllerAdvice
public class ManejoDeErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejoDeErrores.class);

    /** Un id que no es número, p. ej. /saldos/abc/eliminar */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView parametroInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String mensaje = "El dato \"" + ex.getName() + "\" no es válido (" + ex.getValue() + ").";
        if (esApi(request)) return json(HttpStatus.BAD_REQUEST, mensaje);
        request.getSession().setAttribute(BalanceController.AVISO_PENDIENTE, Map.of("error", mensaje));
        return new ModelAndView("redirect:/");
    }

    /** Cualquier otro error inesperado (p. ej. se cayó MySQL). */
    @ExceptionHandler(Exception.class)
    public ModelAndView inesperado(Exception ex, HttpServletRequest request) throws Exception {
        // 404, 405, etc.: que Spring los maneje como siempre (muestra templates/error.html)
        if (ex instanceof ErrorResponse) throw ex;

        log.error("Error inesperado en {}", request.getRequestURI(), ex);
        boolean baseDeDatos = esDeBaseDeDatos(ex);
        String titulo = baseDeDatos ? "No hay conexión con la base de datos" : "Algo salió mal";
        String mensaje = baseDeDatos
                ? "No se pudo leer o guardar en MySQL. Revisa que el servidor de MySQL esté encendido, que exista la base "
                  + "\"balance_general\" y que el usuario y la contraseña de application.properties sean correctos."
                : "Ocurrió un error que el sistema no esperaba. Regresa al inicio e inténtalo otra vez.";

        if (esApi(request)) return json(HttpStatus.INTERNAL_SERVER_ERROR, titulo + ". " + mensaje);
        ModelAndView mv = new ModelAndView("error");
        mv.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        mv.addObject("status", 500);
        mv.addObject("titulo", titulo);
        mv.addObject("mensaje", mensaje);
        mv.addObject("detalle", ex.getClass().getSimpleName() + (ex.getMessage() != null ? ": " + ex.getMessage() : ""));
        return mv;
    }

    private static boolean esApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    private static ModelAndView json(HttpStatus status, String mensaje) {
        ModelAndView mv = new ModelAndView(new MappingJackson2JsonView());
        mv.setStatus(status);
        mv.addObject("ok", false);
        mv.addObject("mensaje", mensaje);
        return mv;
    }

    /** ¿El error viene de la base de datos? Revisa toda la cadena de causas. */
    static boolean esDeBaseDeDatos(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException) return true;
            String n = t.getClass().getName();
            if (n.contains("DataAccess") || n.contains("JDBC") || n.contains("Jdbc")
                    || n.contains("CannotCreateTransaction") || n.contains("CommunicationsException")) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }
}
