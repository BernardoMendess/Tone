package com.tone.servico;

import com.tone.controlador.dto.CategoriaRequest;
import com.tone.controlador.dto.CategoriaResponse;
import com.tone.controlador.dto.SubcategoriaRequest;
import com.tone.controlador.dto.SubcategoriaResponse;
import com.tone.controlador.excecao.CategoriaJaCadastradaException;
import com.tone.modelo.Categoria;
import com.tone.modelo.Subcategoria;
import com.tone.repositorio.CategoriaRepositorio;
import com.tone.repositorio.SubcategoriaRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CategoriaServico {

    private final CategoriaRepositorio categoriaRepositorio;
    private final SubcategoriaRepositorio subcategoriaRepositorio;

    public CategoriaServico(CategoriaRepositorio categoriaRepositorio, SubcategoriaRepositorio subcategoriaRepositorio) {
        this.categoriaRepositorio = categoriaRepositorio;
        this.subcategoriaRepositorio = subcategoriaRepositorio;
    }

    @Transactional
    public CategoriaResponse criarCategoria(CategoriaRequest request) {
        if (categoriaRepositorio.existsByNomeIgnoreCase(request.nome())) {
            throw new CategoriaJaCadastradaException("Categoria já cadastrada com o nome: " + request.nome());
        }

        Categoria categoria = new Categoria(request.nome());
        categoria = categoriaRepositorio.save(categoria);

        List<SubcategoriaResponse> subcategoriasResponse = new ArrayList<>();

        if (request.subcategorias() != null && !request.subcategorias().isEmpty()) {
            for (SubcategoriaRequest subReq : request.subcategorias()) {
                if (subcategoriaRepositorio.existsByNomeIgnoreCaseAndCategoriaId(subReq.nome(), categoria.getId())) {
                    throw new CategoriaJaCadastradaException("Subcategoria '" + subReq.nome() + "' já cadastrada para esta categoria.");
                }
                Subcategoria subcategoria = new Subcategoria(subReq.nome(), categoria);
                subcategoria = subcategoriaRepositorio.save(subcategoria);
                subcategoriasResponse.add(new SubcategoriaResponse(subcategoria.getId(), subcategoria.getNome()));
            }
        }

        return new CategoriaResponse(categoria.getId(), categoria.getNome(), subcategoriasResponse);
    }
}
