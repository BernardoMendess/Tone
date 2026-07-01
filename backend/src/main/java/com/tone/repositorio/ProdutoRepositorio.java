package com.tone.repositorio;

import com.tone.modelo.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProdutoRepositorio extends JpaRepository<Produto, UUID> {
}
