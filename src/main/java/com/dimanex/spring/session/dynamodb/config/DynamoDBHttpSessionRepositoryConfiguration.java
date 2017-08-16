/*
 * Copyright 2017 DiManEx B.V. . All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.dimanex.spring.session.dynamodb.config;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.dimanex.spring.session.dynamodb.DynamoDBSessionRepository;
import com.dimanex.spring.session.dynamodb.config.annotation.EnableDynamoDBHttpSession;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;

// TODO: Clean this code from Spring Boot dependencay and Apply Auto-Configuration techniques in separate module
@Configuration
@ComponentScan("com.dimanex.spring.session.dynamodb")
public class DynamoDBHttpSessionRepositoryConfiguration extends SpringHttpSessionConfiguration implements ImportAware {

    public static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 20 * 60;

    public static final String SESSIONS_TABLE_NAME = "Sessions";

    private int maxInactiveIntervalInSeconds;

    private String sessionsTableName;

    @Bean
    public DynamoDBSessionRepository createDynamoDBSessionRepository(/* DynamoDB dyamoDB */
                                                                     DynamoDBSpringSessionConfiguration springDynamoDBSessionConfiguration) {

        // TODO: Apply DynamoDB client injection please
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion("us-east-1")
                .build();
        return new DynamoDBSessionRepository(new DynamoDB(client), maxInactiveIntervalInSeconds, sessionsTableName);
    }

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(importMetadata.getAnnotationAttributes
                (EnableDynamoDBHttpSession.class.getName()));

        this.maxInactiveIntervalInSeconds = attributes.getNumber("maxInactiveIntervalInSeconds");
        this.sessionsTableName = attributes.getString("sesstionsTableName");
    }

    public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
        this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
    }

    public void setSessionsTableName(String sessionsTableName) {
        this.sessionsTableName = sessionsTableName;
    }

}
