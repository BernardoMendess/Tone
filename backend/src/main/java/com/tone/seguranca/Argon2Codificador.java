package com.tone.seguranca;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Implementação do PasswordEncoder do Spring Security utilizando Argon2id.
 * Parâmetros: iterations=3, memory=65536 (64MB), parallelism=1.
 */
@Component
@Primary
public class Argon2Codificador implements PasswordEncoder {

    private static final int ITERACOES = 3;
    private static final int MEMORIA_KB = 65536;
    private static final int PARALELISMO = 1;

    private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    @Override
    public String encode(CharSequence senhaBruta) {
        return argon2.hash(ITERACOES, MEMORIA_KB, PARALELISMO, senhaBruta.toString().toCharArray());
    }

    @Override
    public boolean matches(CharSequence senhaBruta, String senhaHash) {
        if (senhaBruta == null || senhaHash == null) {
            return false;
        }
        return argon2.verify(senhaHash, senhaBruta.toString().toCharArray());
    }
}
