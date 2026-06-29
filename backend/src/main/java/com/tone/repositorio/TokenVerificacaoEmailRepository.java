package com.tone.repositorio;

import com.tone.modelo.TokenVerificacaoEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TokenVerificacaoEmailRepository extends JpaRepository<TokenVerificacaoEmail, Long> {

    @Query("SELECT t FROM TokenVerificacaoEmail t WHERE t.token = :token")
    Optional<TokenVerificacaoEmail> encontrarPorToken(@Param("token") String token);
}
