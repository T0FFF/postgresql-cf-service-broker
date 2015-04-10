package org.cloudfoundry.community.servicebroker.postgresql.service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudfoundry.community.servicebroker.model.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

@Component
public class Database {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    public void createDatabaseForInstance(String instanceId, String serviceId, String planId, String organizationGuid, String spaceGuid) throws SQLException {
        Utils.checkValidUUID(instanceId);
        Utils.executeUpdate("CREATE DATABASE \"" + instanceId + "\" ENCODING 'UTF8'");
        Utils.executeUpdate("REVOKE all on database \"" + instanceId + "\" from public");

        Map<Integer, String> parameterMap = new HashMap<Integer, String>();
        parameterMap.put(1, instanceId);
        parameterMap.put(2, serviceId);
        parameterMap.put(3, planId);
        parameterMap.put(4, organizationGuid);
        parameterMap.put(5, spaceGuid);

        Utils.executePreparedUpdate("INSERT INTO service (serviceinstanceid, servicedefinitionid, planid, organizationguid, spaceguid) VALUES (?, ?, ?, ?, ?)", parameterMap);
    }

    public void deleteDatabase(String instanceId) throws SQLException {
        Utils.checkValidUUID(instanceId);

        Map<Integer, String> parameterMap = new HashMap<Integer, String>();
        parameterMap.put(1, instanceId);

        try {
            Map<String, String> result = Utils.executePreparedSelect("SELECT current_user", null);
            String currentUser = null;

            if(result != null) {
                currentUser = result.get("current_user");
            }

            if(currentUser == null) {
                logger.warn("Current user could not be found?");
            }

            Utils.executePreparedUpdate("SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = ? AND pid <> pg_backend_pid()", parameterMap);
            Utils.executeUpdate("ALTER DATABASE \"" + instanceId + "\" OWNER TO \"" + currentUser + "\"");
            Utils.executeUpdate("DROP DATABASE \"" + instanceId + "\"");
            Utils.executePreparedUpdate("DELETE FROM service WHERE serviceinstanceid=?", parameterMap);
        } catch (SQLException e) {
            logger.warn(e.getMessage());
        }
    }

    public ServiceInstance findServiceInstance(String instanceId) throws SQLException {
        Utils.checkValidUUID(instanceId);

        Map<Integer, String> parameterMap = new HashMap<Integer, String>();
        parameterMap.put(1, instanceId);

        Map<String, String> result = Utils.executePreparedSelect("SELECT * FROM service WHERE serviceinstanceid = ?", parameterMap);

        String serviceDefinitionId = result.get("servicedefinitionid");
        String organizationGuid = result.get("organizationguid");
        String planId = result.get("planid");
        String spaceGuid = result.get("spaceguid");

        return new ServiceInstance(instanceId, serviceDefinitionId, planId, organizationGuid, spaceGuid, null);
    }

    public List<ServiceInstance> getAllServiceInstances() {
        List<ServiceInstance> serviceInstances = Lists.newArrayList();
        return serviceInstances;
    }
}
