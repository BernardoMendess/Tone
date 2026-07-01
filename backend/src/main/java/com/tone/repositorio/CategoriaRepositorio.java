package com.tone.repositorio;

import com.tone.modelo.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoriaRepositorio extends JpaRepository<Categoria, Long> {
    boolean existsByNomeIgnoreCase(String nome);
}
