package edu.cit.loquillano;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Root application class. Lives in the shared parent package (edu.cit.loquillano)
 * so component scanning picks up both edu.cit.loquillano.shop (Order module)
 * and edu.cit.loquillano.inventory (Inventory module).
 */
@SpringBootApplication
public class ShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
