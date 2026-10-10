package app.fiber;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Excluded: Spring would otherwise create a demo in-memory user and log its generated
// password at startup. Fiber has no users until real accounts arrive in Phase 5.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class FiberApplication {

	public static void main(String[] args) {
		SpringApplication.run(FiberApplication.class, args);
	}

}
