package amalitech.hospital.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ApplicationContext;

/**
 * Extends {@link SpringBootServletInitializer} so this same WAR (see pom.xml's
 * {@code <packaging>war</packaging>} and its "provided"-scope
 * {@code spring-boot-starter-tomcat}) can be deployed to an external servlet container
 * (e.g. dropped into an external Tomcat's {@code webapps/}) — a container that supports
 * the Servlet 3.0+ {@code ServletContainerInitializer} SPI finds this class and calls
 * {@link #configure} to bootstrap the Spring application, instead of relying on this
 * class's own {@code main} method. Running via {@code mvn spring-boot:run} or
 * {@code java -jar} is unaffected — both still go through {@code main} exactly as
 * before, with an embedded Tomcat.
 */
@SpringBootApplication
public class HmsApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(HmsApplication.class);
    }

    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(HmsApplication.class, args);

        System.out.println("Beans in IoC container:");
        for (String name : ctx.getBeanDefinitionNames()) {
            System.out.println(name);
        }
    }
}