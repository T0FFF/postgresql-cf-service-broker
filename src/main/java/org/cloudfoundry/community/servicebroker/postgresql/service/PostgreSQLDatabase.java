package org.cloudfoundry.community.servicebroker.postgresql.service;

import org.cloudfoundry.community.servicebroker.postgresql.model.PGServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PostgreSQLDatabase {

    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLDatabase.class);

    private JdbcTemplate jdbcTemplate;
    
    @Value("${MASTER_JDBC_URL}")
    private String jdbcUrl;


    @Autowired
    public PostgreSQLDatabase(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init(){
        String serviceTable = "CREATE TABLE IF NOT EXISTS service (serviceinstanceid varchar(200) not null default '',"
                + " servicedefinitionid varchar(200) not null default '',"
                + " planid varchar(200) not null default '',"
                + " organizationguid varchar(200) not null default '',"
                + " spaceguid varchar(200) not null default '',"
                + " creds varchar(32) not null default '')";

        jdbcTemplate.execute(serviceTable);
    }

    public void createDatabaseForInstance(String instanceId, String serviceId,
                                          String planId, String organizationGuid, String spaceGuid) throws SQLException {
        Utils.checkValidUUID(instanceId);
        executeUpdate("CREATE DATABASE \"" + instanceId + "\" ENCODING 'UTF8'");
        executeUpdate("REVOKE all on database \"" + instanceId + "\" from public");

        SecureRandom random = new SecureRandom();
        String passwd = new BigInteger(130, random).toString(32);
        createRoleForInstance(instanceId);
        executeUpdate("ALTER ROLE \"" + instanceId + "\" LOGIN password '" + passwd + "'");

        Map<Integer, String> parameterMap = new HashMap<Integer, String>();
        parameterMap.put(1, instanceId);
        parameterMap.put(2, serviceId);
        parameterMap.put(3, planId);
        parameterMap.put(4, organizationGuid);
        parameterMap.put(5, spaceGuid);
        parameterMap.put(6, passwd);
        
        executePreparedUpdate("INSERT INTO service (serviceinstanceid, servicedefinitionid, planid, " +
                "organizationguid, spaceguid, creds) VALUES (?, ?, ?, ?, ?, ?)", parameterMap);
    }

    public void deleteDatabase(String instanceId) throws SQLException {
        Utils.checkValidUUID(instanceId);

        Map<Integer, String> parameterMap = new HashMap<Integer, String>();
        parameterMap.put(1, instanceId);

        Map<String, String> result = executeSelect("SELECT current_user");
        String currentUser = null;

        if(result != null) {
            currentUser = result.get("current_user");
        }

        if(currentUser == null) {
            logger.error("Current user for instance '" + instanceId + "' could not be found");
        }

        executePreparedSelect("SELECT pg_terminate_backend(pg_stat_activity.pid) " +
                "FROM pg_stat_activity WHERE pg_stat_activity.datname = ? AND pid <> pg_backend_pid()", parameterMap);
        executeUpdate("ALTER DATABASE \"" + instanceId + "\" OWNER TO \"" + currentUser + "\"");
        executeUpdate("DROP DATABASE IF EXISTS \"" + instanceId + "\"");
        executePreparedUpdate("DELETE FROM service WHERE serviceinstanceid=?", parameterMap);
    }

    public PGServiceInstance findServiceInstance(String instanceId) throws SQLException {
        Utils.checkValidUUID(instanceId);

        Map<Integer, String> parameterMap = new HashMap<Integer, String>();
        parameterMap.put(1, instanceId);

        Map<String, String> result = executePreparedSelect("SELECT * FROM service WHERE serviceinstanceid = ?", parameterMap);

        String serviceDefinitionId = result.get("servicedefinitionid");
        String organizationGuid = result.get("organizationguid");
        String planId = result.get("planid");
        String spaceGuid = result.get("spaceguid");
        PGServiceInstance serviceInstance = new PGServiceInstance();
        serviceInstance.setServiceInstanceId(serviceDefinitionId);
        serviceInstance.setOrganizationGuid(organizationGuid);
        serviceInstance.setPlanId(planId);
        serviceInstance.setSpaceGuid(spaceGuid);
        serviceInstance.setCredentials(result.get("creds"));
        return serviceInstance;
    }

    // TODO needs to be implemented
    public List<PGServiceInstance> getAllServiceInstances() {
        return Collections.emptyList();
    }

    public void createRoleForInstance(String instanceId) throws SQLException {
        Utils.checkValidUUID(instanceId);
        executeUpdate("CREATE ROLE \"" + instanceId + "\"");
        //executeUpdate("GRANT  \"" + instanceId + "\" to \"pgadmin\"");
        executeUpdate("GRANT ALL ON DATABASE \"" + instanceId + "\" TO \"" + instanceId + "\"");        

      // fork a connection
      SingleConnectionDataSource forkdataSource = new SingleConnectionDataSource();
      //jdbc:postgresql://10.0.18.14:5432/sandbox?user=pgadmin&password=password
      String[] parts = jdbcUrl.split("\\?");
      String[] partsOther = parts[0].split("/");
      String newconnection = partsOther[0]+"//"+partsOther[2]+"/"+instanceId+"?"+parts[1];
      forkdataSource.setUrl(newconnection);
      JdbcTemplate tpl= new JdbcTemplate(forkdataSource);
      tpl.execute("GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \"" + instanceId + "\"");
      //tpl.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON tables TO \"" + instanceId + "\"");
      tpl.execute("ALTER DEFAULT PRIVILEGES FOR ROLE \""+instanceId+"\" IN SCHEMA public GRANT ALL ON TABLES TO \"" + instanceId + "\"");
      tpl.execute("ALTER DEFAULT PRIVILEGES FOR ROLE \""+instanceId+"\" IN SCHEMA public GRANT ALL ON SEQUENCES TO \"" + instanceId + "\"");
      tpl.execute("ALTER DEFAULT PRIVILEGES FOR ROLE \""+instanceId+"\" IN SCHEMA public GRANT ALL ON FUNCTIONS TO \"" + instanceId + "\"");      
      forkdataSource.destroy();
    
      executeUpdate("ALTER DATABASE \"" + instanceId + "\" OWNER TO \"" + instanceId + "\"");

    
    }

    
//    public void createRoleForInstance(String instanceId, String bindingId) throws SQLException {
//        Utils.checkValidUUID(instanceId);
//        Utils.checkValidUUID(bindingId);
//        executeUpdate("CREATE ROLE \"" + bindingId + "\"");
//        //executeUpdate("ALTER DATABASE \"" + instanceId + "\" OWNER TO \"" + instanceId + "\"");
//        executeUpdate("GRANT ALL ON DATABASE \"" + instanceId + "\" TO \"" + bindingId + "\"");
//        
//        // fork a connection
//        //DriverManagerDataSource dataSource = new DriverManagerDataSource();
//        SingleConnectionDataSource forkdataSource = new SingleConnectionDataSource();
//        //jdbc:postgresql://10.0.18.14:5432/sandbox?user=pgadmin&password=password
//        String[] parts = jdbcUrl.split("\\?");
//        String[] partsOther = parts[0].split("/");
//        String newconnection = partsOther[0]+"//"+partsOther[2]+"/"+instanceId+"?"+parts[1];
//        System.err.println(newconnection);
//        forkdataSource.setUrl(newconnection);
//        JdbcTemplate tpl= new JdbcTemplate(forkdataSource);
//        tpl.execute("GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \"" + bindingId + "\"");
//        forkdataSource.destroy();
//        System.err.println("DONE with fork grant " + bindingId);
//        
//    }

    public void deleteRole(String bindingId) throws SQLException {
        Utils.checkValidUUID(bindingId);
        executeUpdate("DROP ROLE IF EXISTS \"" + bindingId + "\"");
    }

    /**
     *  Binds role to database and returns URI for app connection.
     * @param serviceInstanceId
     * @return
     * @throws Exception
     */
    public String bindRoleToDatabase(String serviceInstanceId, String bindingId) throws Exception {
        Utils.checkValidUUID(serviceInstanceId);
        Utils.checkValidUUID(bindingId);

        
        // hack - the user was already created for multi binding
//        SecureRandom random = new SecureRandom();
//        String passwd = new BigInteger(130, random).toString(32);
//
//        executeUpdate("CREATE USER \"" + bindingId + "\"");
//        executeUpdate("ALTER USER \"" + bindingId + "\" LOGIN password '" + passwd + "'");
//        executeUpdate("GRANT \"" + serviceInstanceId + "\" TO \"" + bindingId + "\"");// GRANT ROLE TO USER
//        executeUpdate("ALTER ROLE \"" + bindingId + "\" INHERIT");
//
//        executeUpdate("GRANT ALL ON DATABASE \"" + serviceInstanceId + "\" TO \"" + bindingId + "\""); // will grant CONNECT
//        
//        // fork
//        SingleConnectionDataSource forkdataSource = new SingleConnectionDataSource();
//        //jdbc:postgresql://10.0.18.14:5432/sandbox?user=pgadmin&password=password
//        String[] parts = jdbcUrl.split("\\?");
//        String[] partsOther = parts[0].split("/");
//        String newconnection = partsOther[0]+"//"+partsOther[2]+"/"+serviceInstanceId+"?user="+bindingId+"&password="+passwd;
//        System.err.println(newconnection);
//        forkdataSource.setUrl(newconnection);
//        JdbcTemplate tpl= new JdbcTemplate(forkdataSource);
//        tpl.execute("GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \"" + bindingId + "\"");
//        //FOR ROLE \""+serviceInstanceId+"\" 
//        tpl.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO \"" + bindingId + "\"");
//        tpl.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO \"" + bindingId + "\"");
//        tpl.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO \"" + bindingId + "\"");
//        forkdataSource.destroy();
        
        
        //executeUpdate("ALTER DEFAULT PRIVILEGES FOR ROLE \""+serviceInstanceId+"\" IN SCHEMA public GRANT ALL ON TABLES TO \"" + bindingId + "\"");
        //executeUpdate("ALTER DEFAULT PRIVILEGES FOR ROLE \""+serviceInstanceId+"\" IN SCHEMA public GRANT ALL ON SEQUENCES TO \"" + bindingId + "\"");
        //executeUpdate("ALTER DEFAULT PRIVILEGES FOR ROLE \""+serviceInstanceId+"\" IN SCHEMA public GRANT ALL ON FUNCTIONS TO \"" + bindingId + "\"");

        // Support multi-proxy
        String uri = jdbcTemplate.getDataSource().getConnection().getMetaData().getURL().replace("jdbc:postgresql://", "").split("/")[0];
        String dbURL = String.format("postgres://%s:%s@%s/%s",serviceInstanceId, findServiceInstance(serviceInstanceId).getCredentials(), uri , serviceInstanceId);
        /*
        String dbURL = String.format("postgres://%s:%s@%s:%d/%s",
                // hack for multibinding
        		serviceInstanceId,
        		findServiceInstance(serviceInstanceId).getCredentials(),
        		//bindingId, passwd,
                uri.getHost(), uri.getPort() == -1 ? 5432 : uri.getPort(), serviceInstanceId);
        
        */
        return dbURL;
    }

    public void unBindRoleFromDatabase(String dbInstanceId, String bindingId) throws SQLException{
        Utils.checkValidUUID(dbInstanceId);
        Utils.checkValidUUID(bindingId);
        executeUpdate("ALTER USER \"" + bindingId + "\" NOLOGIN");
        executeUpdate("REVOKE \"" + dbInstanceId + "\" FROM \"" + bindingId + "\"");// REVOKE ROLE FROM USER
        //executeUpdate("REVOKE ALL ON  DATABASE \"" + dbInstanceId + "\" FROM \"" + bindingId + "\"");
        
        //TODO remove USER
    }
    /**
     *
     * @param query
     * @throws SQLException
     */
    public void executeUpdate(String query) throws SQLException {

        try {

          jdbcTemplate.execute(query);

        } catch (Exception e) {
            logger.error("Error while executing SQL UPDATE query '" + query + "'", e);
            
            System.err.println("Error while executing SQL UPDATE query '" + query + "'");
            e.printStackTrace();
        }

    }

    /**
     *
     * @param query
     * @return
     * @throws SQLException
     */
    public Map<String, String> executeSelect(String query) throws SQLException {

           return jdbcTemplate.query(query, new String[]{}, resultSet -> {
               return processResultSet(resultSet);
           });

    }

    /**
     *
     * @param query
     * @param parameterMap
     * @throws SQLException
     */
    public void executePreparedUpdate(String query, @NotNull Map<Integer, String> parameterMap) throws SQLException {

//        if(parameterMap == null) throw new IllegalStateException("parameterMap cannot be null");

        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            for(Map.Entry<Integer, String> parameter : parameterMap.entrySet()) {
                preparedStatement.setString(parameter.getKey(), parameter.getValue());
            }
            return preparedStatement;
        });

    }

    /**
     *
     * @param query
     * @param parameterMap
     * @return
     * @throws SQLException
     */
    public Map<String, String> executePreparedSelect(String query, @NotNull Map<Integer, String> parameterMap)
            throws SQLException {

//        if(parameterMap == null)             throw new IllegalStateException("parameterMap cannot be null");


        return jdbcTemplate.query(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            for(Map.Entry<Integer, String> parameter : parameterMap.entrySet()) {
                preparedStatement.setString(parameter.getKey(), parameter.getValue());
            }
            return preparedStatement;
        }, resultSet -> {
            return processResultSet(resultSet);
        });

    }

    /**
     *
     * @param resultSet
     * @return
     * @throws SQLException
     */
    private Map<String, String> processResultSet(ResultSet resultSet) throws SQLException{

        ResultSetMetaData resultMetaData = resultSet.getMetaData();
        int columns = resultMetaData.getColumnCount();

        Map<String, String> resultMap = new HashMap<>(columns);

        if(resultSet.next()) {
            for(int i = 1; i <= columns; i++) {
                resultMap.put(resultMetaData.getColumnName(i), resultSet.getString(i));
            }
        }
        return resultMap;

    }

}
