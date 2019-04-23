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
package com.dimanex.spring.session.dynamodb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.util.IOUtils;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DynamoDBSessionRepository implements SessionRepository<DynamoDBSessionRepository.DynamoDBSession> {

    private static final String ITEM_SESSION_ID_ATTRIBUTE_NAME = "id";
    private static final String ITEM_SESSION_EXPIRATION_TIME_ATTRIBUTE_NAME = "expires";
    private static final String ITEM_SESSION_DATA_ATTRIBUTE_NAME = "data";

    private final DynamoDB dynamoDB;
    private final int maxInactiveIntervalInSeconds;
    private final String sessionsTableName;

    public DynamoDBSessionRepository(DynamoDB dynamoDB, int maxInactiveIntervalInSeconds, String sessionsTableName) {
        this.dynamoDB = dynamoDB;
        this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
        this.sessionsTableName = sessionsTableName;
    }

    @Override
    public DynamoDBSession createSession() {
        return new DynamoDBSession(UUID.randomUUID().toString(), this.maxInactiveIntervalInSeconds);
    }

    @Override
    public void save(DynamoDBSession session) {
        try {
            this.dynamoDB.getTable(this.sessionsTableName).putItem(toDynamoDBItem(session));
        } catch (IOException e) {
            log.error(e);
        }
    }
    
    
    @Override
	public DynamoDBSession findById(String id) {
    	try {
             Item sessionItem = this.dynamoDB.getTable(this.sessionsTableName).getItem(ITEM_SESSION_ID_ATTRIBUTE_NAME,
                     id);
             if (sessionItem == null) {
                 return null;
             }
             DynamoDBSession session = toSession(sessionItem);
             if (session.isExpired()) {
                 log.info("Session: '{}' has expired. It will be deleted.", id);
                 deleteById(id);
                 return null;
             }
             session.setLastAccessedTime(Instant.now());
             return session;
         } catch (IOException | ClassNotFoundException e) {
             log.error(e);
         }
         return null;
	}

	@Override
	public void deleteById(String id) {
		this.dynamoDB.getTable(this.sessionsTableName).deleteItem(ITEM_SESSION_ID_ATTRIBUTE_NAME, id);
	}


    public static class DynamoDBSession implements Session, Serializable {

        private static final long serialVersionUID = 6459851973327402721L;

        private String id;
        private long creationTime;
        private long lastAccessedTime;
        private long maxInactiveIntervalSeconds;
        private Map<String, Object> attributes;
        private Date expireAt;

       public DynamoDBSession(String id, long maxInactiveIntervalSeconds) {
            this.id = id;
            this.creationTime = System.currentTimeMillis();
            this.lastAccessedTime = this.creationTime;
            this.maxInactiveIntervalSeconds = maxInactiveIntervalSeconds;
            attributes = new HashMap<>();
        }
       
        @Override
		public String changeSessionId() {
        	String changedId = UUID.randomUUID().toString();
    		this.id = changedId;
    		return changedId;
		}

		@Override
        public Instant getCreationTime() {
            return Instant.ofEpochMilli(this.creationTime);
        }

        @Override
        public void setLastAccessedTime(Instant lastAccessedTime) {
            this.lastAccessedTime = lastAccessedTime.toEpochMilli();
            this.expireAt = Date.from(lastAccessedTime.plus(Duration.ofSeconds(this.maxInactiveIntervalSeconds)));
        }

        @Override
        public Instant getLastAccessedTime() {
            return Instant.ofEpochMilli(lastAccessedTime);
        }

        @Override
        public void setMaxInactiveInterval(Duration interval) {
            this.maxInactiveIntervalSeconds = interval.getSeconds();
        }

     
        @Override
        public Duration getMaxInactiveInterval() {
            return Duration.ofSeconds(this.maxInactiveIntervalSeconds);
        }

        @Override
        public boolean isExpired() {
        	return this.maxInactiveIntervalSeconds >= 0 && new Date().after(this.expireAt);
        }

        @Override
        public String getId() {
            return this.id;
        }

        @SuppressWarnings("unchecked")
		@Override
        public <T> T getAttribute(String attributeName) {
            return (T) this.attributes.get(attributeName);
        }

        @Override
        public Set<String> getAttributeNames() {
            return this.attributes.keySet().stream()
            		.collect(Collectors.toSet());
        }

        @Override
        public void setAttribute(String attributeName, Object attributeValue) {
            if (attributeValue == null) {
                removeAttribute(attributeName);
            } else {
                this.attributes.put(attributeName, attributeValue);
            }
        }

        @Override
        public void removeAttribute(String attributeName) {
            this.attributes.remove(attributeName);
        }

    }

    private Item toDynamoDBItem(DynamoDBSession session) throws IOException {
        ObjectOutputStream oos = null;
        try {
            Item item = new Item().withPrimaryKey(ITEM_SESSION_ID_ATTRIBUTE_NAME, session.getId());
            updateTimeToLive(item, session);
            ByteArrayOutputStream fos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(fos);
            oos.writeObject(session);
            oos.close();
            item.withBinary(ITEM_SESSION_DATA_ATTRIBUTE_NAME, ByteBuffer.wrap(fos.toByteArray()));
            return item;
        } finally {
            IOUtils.closeQuietly(oos, null);
        }
    }

    private DynamoDBSession toSession(Item item) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = null;
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(item.getBinary(ITEM_SESSION_DATA_ATTRIBUTE_NAME));
            ois = new ObjectInputStream(bis);
            DynamoDBSession session = (DynamoDBSession) ois.readObject();
            return session;
        } finally {
            IOUtils.closeQuietly(ois, null);
        }
    }

    private void updateTimeToLive(Item item, DynamoDBSession session) {
        if (session.getMaxInactiveInterval().getSeconds() >= 0) {
            final long lastAccessTimeSeconds = 
            		TimeUnit.MILLISECONDS.toSeconds(session.getLastAccessedTime().toEpochMilli());
        	
        	final long ttlInSeconds = lastAccessTimeSeconds + session.getMaxInactiveInterval().getSeconds();
            item.withNumber(ITEM_SESSION_EXPIRATION_TIME_ATTRIBUTE_NAME, ttlInSeconds);
        }
    }
}
