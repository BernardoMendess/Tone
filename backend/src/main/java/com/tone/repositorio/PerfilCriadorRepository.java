package com.tone.repositorio;

import com.tone.modelo.PerfilCriador;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PerfilCriadorRepository extends JpaRepository<PerfilCriador, Long> {

    Optional<PerfilCriador> findByUsuarioId(Long usuarioId);
}
