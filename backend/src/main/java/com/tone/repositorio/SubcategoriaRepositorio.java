package com.tone.repositorio;

import com.tone.modelo.Subcategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubcategoriaRepositorio extends JpaRepository<Subcategoria, Long> {
    boolean existsByNomeIgnoreCaseAndCategoriaId(String nome, Long categoriaId);
}
