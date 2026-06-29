package com.tone.controlador;

import com.tone.controlador.dto.CategoriaRequest;
import com.tone.controlador.dto.CategoriaResponse;
import com.tone.servico.CategoriaServico;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaControlador {

    private final CategoriaServico categoriaServico;

    public CategoriaControlador(CategoriaServico categoriaServico) {
        this.categoriaServico = categoriaServico;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoriaResponse> criarCategoria(@Valid @RequestBody CategoriaRequest request) {
        CategoriaResponse response = categoriaServico.criarCategoria(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CategoriaResponse>> listarCategorias() {
        return ResponseEntity.ok(categoriaServico.listarTodas());
    }
}
