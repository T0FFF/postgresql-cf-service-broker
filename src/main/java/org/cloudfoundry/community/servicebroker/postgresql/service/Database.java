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
package org.cloudfoundry.community.servicebroker.postgresql.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.cloudfoundry.community.servicebroker.model.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

@Component
public class Database {

    private static final Logger logger = LoggerFactory.getLogger(Database.class);
    private final Connection conn;

    @Autowired
    public Database(Connection conn) {
        this.conn = conn;
    }

    public void createDatabaseForInstance(String instanceId, String planId, String organizationGuid, String spaceGuid) throws SQLException {
        checkValidUUID(instanceId);

        Statement createDatabase = this.conn.createStatement();
        Statement makePrivate = this.conn.createStatement();

        PreparedStatement insertService = this.conn.prepareStatement("INSERT INTO service (serviceinstanceid, servicedefinitionid, organizationguid, spaceguid) VALUES (?, ?, ?, ?)");
        insertService.setString(1, instanceId);
        insertService.setString(2, planId);
        insertService.setString(3, organizationGuid);
        insertService.setString(4, spaceGuid);

        try {
            createDatabase.execute("CREATE DATABASE \"" + instanceId + "\" ENCODING 'UTF8'");
            makePrivate.execute("REVOKE all on database \"" + instanceId + "\" from public");
            insertService.executeUpdate();
        } catch (SQLException e) {
            logger.warn(e.getMessage());
        } finally {
            createDatabase.close();
            makePrivate.close();
            insertService.close();
        }
    }

    public void deleteDatabase(String instanceId) throws SQLException {
        checkValidUUID(instanceId);

        Statement deleteDatabase = this.conn.createStatement();
        PreparedStatement deleteService = this.conn.prepareStatement("DELETE FROM service WHERE serviceinstanceid=?");
        deleteService.setString(1, instanceId);

        try {
            deleteDatabase.execute("DROP DATABASE \"" + instanceId + "\"");
            deleteService.executeUpdate();
        } catch (SQLException e) {
            logger.warn(e.getMessage());
        } finally {
            deleteDatabase.close();
            deleteService.close();
        }
    }

    public ServiceInstance findServiceInstance(String instanceId) throws SQLException {
        checkValidUUID(instanceId);

        PreparedStatement findDatabase = this.conn.prepareStatement("SELECT datname FROM pg_database WHERE datname = \"?\"");
        findDatabase.setString(1, instanceId);
        ResultSet result = null;
        try {
            result = findDatabase.executeQuery();
            if (result.next())
                return null;
        } catch (SQLException e) {
            logger.warn(e.getMessage());
        } finally {
            if (result != null)
                result.close();
        }
        return null;
    }

    public List<ServiceInstance> getAllServiceInstances() {
        List<ServiceInstance> serviceInstances = Lists.newArrayList();
        return serviceInstances;
    }

    private ServiceInstance createServiceInstance(String instanceId) {
        return null;
    }

    private void checkValidUUID(String instanceId) throws SQLException{
        UUID uuid = UUID.fromString(instanceId);

        if(!instanceId.equals(uuid.toString())) {
            throw new SQLException("UUID '" + instanceId + "' is not an UUID.");
        }
    }
}
