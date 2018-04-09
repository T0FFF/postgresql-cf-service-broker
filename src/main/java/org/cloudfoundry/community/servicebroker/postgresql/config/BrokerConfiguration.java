/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cloudfoundry.community.servicebroker.postgresql.config;


import org.cloudfoundry.community.servicebroker.postgresql.service.PostgreSQLDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.servicebroker.config.BrokerApiVersionConfig;
import org.springframework.cloud.servicebroker.model.Catalog;
import org.springframework.cloud.servicebroker.model.Plan;
import org.springframework.cloud.servicebroker.model.ServiceDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ComponentScan(basePackages = "org.cloudfoundry.community.servicebroker",
        excludeFilters = { @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = BrokerApiVersionConfig.class) })
public class BrokerConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(BrokerConfiguration.class);

    @Value("${MASTER_JDBC_URL}")
    private String jdbcUrl;

//	@Value("${service_id}")
//	private String serviceId;
//
//	@Value("${plan_id}")
//	private String planId;

    @Bean
    public JdbcTemplate jdbcTemplate(){
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(this.jdbcUrl);
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public PostgreSQLDatabase postgreSQLDatabase(JdbcTemplate jdbcTemplate){
        return new PostgreSQLDatabase(jdbcTemplate);
    }

    @Bean
    public Catalog catalog() throws IOException {
        ServiceDefinition serviceDefinition = new ServiceDefinition(
				getEnvOrDefault("SERVICE_ID","postgresql-service-broker"), //env variable
				getEnvOrDefault("SERVICE_NAME","postgresql"), //env variable
				getEnvOrDefault("SERVICE_MARKETPLACE_DESCRIPTION","PostgreSQL databases on demand."),
                true, false, getPlans(), getTags(), getServiceDefinitionMetadata(), Arrays.asList("syslog_drain"), null);
        return new Catalog(Arrays.asList(serviceDefinition));
    }

    private static List<String> getTags() {
        return Arrays.asList("postgresql", "relational");
    }

    private static Map<String, Object> getServiceDefinitionMetadata() {
        Map<String, Object> sdMetadata = new HashMap<>();
        sdMetadata.put("displayName", getEnvOrDefault("SERVICE_MARKETPLACE_LABEL","PostgreSQL"));
        sdMetadata.put("imageUrl", "https://wiki.postgresql.org/images/3/30/PostgreSQL_logo.3colors.120x120.png");
        sdMetadata.put("longDescription", "Provisioning a service instance creates a PostgreSQL database. Binding applications to the instance creates unique credentials for each application to access the database.");
        sdMetadata.put("providerDisplayName", getEnvOrDefault("SERVICE_PROVIDER_DISPLAY_NAME","Open Source, Orange Cloud Foundry Platform"));
        sdMetadata.put("documentationUrl", "https://www.postgresql.org/?cm_sp=IBMCode-_-run-gitlab-kubernetes-_-included_components-_-postgresql");
        sdMetadata.put("supportUrl", "https://www.postgresql.org/support/");
        return sdMetadata;
    }

    private List<Plan> getPlans() {
        Plan basic = new Plan(getEnvOrDefault("SERVICE_PLAN_ID","postgresql-plan"),
        		getEnvOrDefault("SERVICE_PLAN_NAME","Default"),
                "This is a default PostgreSQL plan.  All services are created equally.", getBasicPlanMetadata(), true);
        return Arrays.asList(basic);
    }

    private static Map<String, Object> getBasicPlanMetadata() {
        Map<String, Object> planMetadata = new HashMap<>();
        planMetadata.put("bullets", getBasicPlanBullets());
        return planMetadata;
    }

    private static List<String> getBasicPlanBullets() {
        return Arrays.asList("HA database", "Shared instance");
    }
    
	private static String getEnvOrDefault(final String variable, final String defaultValue){
		String value = System.getenv(variable);
		if(value != null){
			return value;
		}
		else{
			return defaultValue;
		}
	}
}