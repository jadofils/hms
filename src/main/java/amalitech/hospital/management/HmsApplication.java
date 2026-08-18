package amalitech.hospital.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class HmsApplication {
    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(HmsApplication.class, args);

        System.out.println("Beans in IoC container:");
        for (String name : ctx.getBeanDefinitionNames()) {
            System.out.println(name);
        }
    }
}

