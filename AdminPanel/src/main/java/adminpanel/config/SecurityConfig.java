package adminpanel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Отключаем CSRF, чтобы гостевой микросервис мог слать POST-запросы без токенов
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // Открываем эндпоинты синхронизации чатов для гостевого сервиса
                        .requestMatchers("/admin/chats/**").permitAll()

                        // Все остальные запросы (твоя будущая или текущая админка) пока тоже откроем,
                        // чтобы у тебя ничего не сломалось в процессе разработки
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}