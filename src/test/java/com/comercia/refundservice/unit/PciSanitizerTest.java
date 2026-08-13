package com.comercia.refundservice.unit;

import com.comercia.refundservice.util.PciSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Nombres de método largos y descriptivos en español: cuando un test falla, el nombre del método
// aparece en el resultado del build, así que sirve como documentación de qué caso concreto se rompió.
class PciSanitizerTest {

    @Test
    void detectaNumeroDeTarjetaConEspacios() {
        assertTrue(PciSanitizer.containsSensitiveCardData("Mi tarjeta 4111 1111 1111 1111 fue rechazada"));
    }

    @Test
    void detectaNumeroDeTarjetaSinSeparadores() {
        assertTrue(PciSanitizer.containsSensitiveCardData("4111111111111111"));
    }

    @Test
    void detectaCvvConPalabraClave() {
        assertTrue(PciSanitizer.containsSensitiveCardData("El CVV 123 no coincide"));
    }

    @Test
    void noDetectaTextoNormalSinDatosSensibles() {
        assertFalse(PciSanitizer.containsSensitiveCardData("Producto defectuoso, cliente insatisfecho"));
    }

    // Este test es importante: comprueba que un número corto (no parece PAN) NO dispara un falso
    // positivo. Sin este caso, podríamos "arreglar" el regex para ser demasiado agresivo y bloquear
    // reasons legítimos que simplemente mencionan cualquier número.
    @Test
    void noDetectaFalsosPositivosConNumerosCortos() {
        assertFalse(PciSanitizer.containsSensitiveCardData("Pedido número 12345"));
    }

    // Comprobamos los casos límite (null, string vacío) por separado: son los que más fácil
    // rompen un método con una NullPointerException si alguien lo modifica sin cuidado en el futuro.
    @Test
    void textoNuloOVacioNoLanzaExcepcion() {
        assertFalse(PciSanitizer.containsSensitiveCardData(null));
        assertFalse(PciSanitizer.containsSensitiveCardData(""));
    }
}