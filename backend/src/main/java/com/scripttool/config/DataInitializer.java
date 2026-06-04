package com.scripttool.config;

import com.scripttool.model.entity.User;
import com.scripttool.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = new User(
                    "admin",
                    passwordEncoder.encode("123456"),
                    "管理员"
            );
            userRepository.save(admin);
            System.out.println("==============================================");
            System.out.println("  测试账号已创建");
            System.out.println("  用户名: admin");
            System.out.println("  密码:   123456");
            System.out.println("==============================================");
        }
    }
}
