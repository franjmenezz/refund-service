package com.comercia.refundservice.config;

import com.comercia.refundservice.domain.Payment;
import com.comercia.refundservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Siembra un par de pago de ejemplo al arrancar, para poder probar el flujo de 
 * devoluviones sin necesidad de un endpoint de creación de pagos (fuera de alcance).
 * Los IDs son fijos y deterministas para facilitar las pruebas manuales (ver README.md).
 */

@Component
public class DataSeeder implements CommandLineRunner {
    
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    // IDs fijos (no aleatorios) a propósito: asi siempre se sabe de antemano que payment_id usar
    // en las pruebas manuales con curl o Postman, sin tener que consultar la BD primero.
    private static final UUID PAYMENT_ID_1 = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PAYMENT_ID_2 = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final PaymentRepository paymentRepository;

    public DataSeeder(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    // CommadnLineRunner.run() lo ejecuta Spring Boot al arrancar automáticamente justo después de arrancar
    // (una vez el contexto de la aplicación está completamente inicializado), sin que tengamos que llamarlo nosotros desde ningún sitio.

    @Override
    public void run(String... args) {
        paymentRepository.save(new Payment(PAYMENT_ID_1, new BigDecimal("100.00"), "EUR"));
        paymentRepository.save(new Payment(PAYMENT_ID_2, new BigDecimal("50.00"), "USD"));
        log.info("Datos de demo cargados: 2 pagos disponibles para pruebas de refund.");
    }
}