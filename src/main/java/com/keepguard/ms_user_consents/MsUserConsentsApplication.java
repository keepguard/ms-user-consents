package com.keepguard.ms_user_consents;

import com.keepguard.lib_common.config.MetricsConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.keepguard.ms_user_consents", "com.keepguard.lib_common"})
@EnableJpaRepositories(basePackages = "com.keepguard.ms_user_consents.infrastructure.persistence.spring")
@Import(MetricsConfig.class)
public class MsUserConsentsApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsUserConsentsApplication.class, args);
	}

}

