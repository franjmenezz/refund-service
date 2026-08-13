package com.comercia.refundservice.util;
import java.util.regex.Pattern;

/**
 * Control PCI-DSS: detecta patrones que parecen un PAN (número de tarjeta) o u CVV en campos texto libre (p.ej. 'reason'), para evitar que datoes de tarjeta viaje en campos no autorizados del payload.
 * 
 * IMPORTANTE: esta clase NUNCA debe loguear el valor evaluado, solo el resultado boolenao
 */

public class PciSanitizer {

    // 13 a 19 digitos consecutivos, pemitiendo espacios oguines cada 4 (formato habitual de tarjeta)

    private static final Pattern PAN_PATTERN = 
        Pattern.compile("(?:\\d[ -]?){13,19}");

    // Palabra clave de CVV/CVC seguida (a poca distancia) de 3-4 dígitos
    private static final Pattern CVV_PATTERN = 
        Pattern.compile("(?i)\\b(cvv|cvc|cv2)\\b\\D{0,5}\\d{3,4}");

     // Constructor privado: esta clase es solo un contenedor de métodos estáticos (utility class),
    // no tiene sentido crear instancias de ella.
    private PciSanitizer() {
    }

    /**
     * @return true si el texto contiene un patrón sospechoso de PAN o CVV.
     */
    public static boolean containsSensitiveCardData(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        // Nos quedamos solo con los dígitos del texto, ignorando espacios/guiones,
        // para poder medir cuántos dígitos hay en total (independientemente del formato).
        String digitsOnly = text.replaceAll("[^0-9]", "");

        // Comprobación de PAN: hace falta que (a) el patrón de "dígitos con separadores opcionales" encaje
        // Y (b) que el total de dígitos esté en el rango típico de un número de tarjeta (13-19).
        // Las dos condiciones a la vez reducen falsos positivos con textos que solo tienen algún número suelto.
        if (digitsOnly.length() >= 13 && PAN_PATTERN.matcher(text).find() && hasLuhnLikeLength(digitsOnly)) {
            return true;
        }

        // Comprobación de CVV: buscamos la palabra clave (cvv/cvc/cv2) cerca de 3-4 dígitos.
        return CVV_PATTERN.matcher(text).find();
    }

    private static boolean hasLuhnLikeLength(String digitsOnly) {
        return digitsOnly.length() >= 13 && digitsOnly.length() <= 19;
    }
}