package com.tone.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class ConfiguracaoSeguranca {

    private final JwtFiltroAutenticacao jwtFiltro;

    public ConfiguracaoSeguranca(JwtFiltroAutenticacao jwtFiltro) {
        this.jwtFiltro = jwtFiltro;
    }

    @Bean
    public SecurityFilterChain filtroSeguranca(HttpSecurity http) throws Exception {
        http
                // CSRF desabilitado: tokens são transmitidos via cookie HttpOnly com SameSite=Strict,
                // que já previne CSRF em si mesmo. Caso mude para SameSite=Lax ou None, reabilitar.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos listados explicitamente — sem wildcard
                        .requestMatchers(
                                "/api/auth/cadastrar",
                                "/api/auth/login",
                                "/api/auth/verificar-email",
                                "/api/auth/logout"
                        ).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/categorias").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder codificadorSenha(Argon2Codificador argon2Codificador) {
        return argon2Codificador;
    }
}
