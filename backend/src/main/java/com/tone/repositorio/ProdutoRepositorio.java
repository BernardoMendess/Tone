package com.tone.repositorio;

import com.tone.modelo.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import com.tone.modelo.enums.StatusProduto;
import java.util.List;

@Repository
public interface ProdutoRepositorio extends JpaRepository<Produto, UUID> {
    List<Produto> findByCriadorIdAndStatus(Long criadorId, StatusProduto status);
}
