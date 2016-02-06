/**
 * Copyright 2011-2016 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.greenbus.msg.amqp.japi;


import io.greenbus.msg.amqp.japi.util.PropertyLoading;
import io.greenbus.msg.amqp.japi.util.PropertyReader;

import java.io.IOException;
import java.util.Dictionary;

/**
 * Settings class that encapsulates the settings for connection to the AMQP broker.
 */
public class AmqpSettings
{
    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String virtualHost;

    private final int heartbeatTimeSeconds;

    private final int TTL_DEFAULT = 5000;
    private final int ttlMilliseconds;

    private final boolean ssl;
    private final String trustStore;
    private final String trustStorePassword;
    private final String keyStore;
    private final String keyStorePassword;

    /**
     * non-ssl overload
     */
    public AmqpSettings(String host, int port, String user, String password, String virtualHost, int heartbeatTimeSeconds)
    {
        this( host, port, user, password, virtualHost, heartbeatTimeSeconds, false, null, null, null, null );
    }

    /**
     * ssl overload
     */
    public AmqpSettings(String host, int port, String user, String password, String virtualHost, int heartbeatTimeSeconds, boolean ssl,
                        String trustStore, String trustStorePassword)
    {
        this( host, port, user, password, virtualHost, heartbeatTimeSeconds, ssl, trustStore, trustStorePassword, null, null );
    }

    /**
     * @param host        The IP address or DNS name of AMQP broker
     * @param port        The TCP port that the broker is listening on (default 5672)
     * @param user        The username for the connection
     * @param password    The password for the connection
     * @param virtualHost The virtual host to use, default is '/'
     * @param heartbeatTimeSeconds heartbeat time in seconds, 0 disables heartbeats
     * @param ssl         If connection is encrypted using SSL
     * @param trustStore  Relative path to trustStore file (trust-store.jks)
     * @param trustStorePassword Used to verify trustStore integrity, actually closer to a checksum than password
     * @param keyStore    Relative path to keyStore file (key-store.jks) (only used with SASL_EXTERNAL)
     * @param keyStorePassword Used to verify keyStore integrity, actually closer to a checksum than password
     */
    public AmqpSettings(String host, int port, String user, String password, String virtualHost, int heartbeatTimeSeconds, boolean ssl,
                        String trustStore, String trustStorePassword, String keyStore, String keyStorePassword)
    {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.virtualHost = virtualHost;
        this.heartbeatTimeSeconds = heartbeatTimeSeconds;
        this.ttlMilliseconds = TTL_DEFAULT;
        this.ssl = ssl;
        this.trustStore = trustStore;
        this.trustStorePassword = trustStorePassword;
        this.keyStore = keyStore;
        this.keyStorePassword = keyStorePassword;
    }

    /**
     * @param host        The IP address or DNS name of AMQP broker
     * @param port        The TCP port that the broker is listening on (default 5672)
     * @param user        The username for the connection
     * @param password    The password for the connection
     * @param virtualHost The virtual host to use, default is '/'
     * @param heartbeatTimeSeconds heartbeat time in seconds, 0 disables heartbeats
     * @param ttlMilliseconds ttl time for published messages before they are dropped by broker, 0 disables
     * @param ssl         If connection is encrypted using SSL
     * @param trustStore  Relative path to trustStore file (trust-store.jks)
     * @param trustStorePassword Used to verify trustStore integrity, actually closer to a checksum than password
     * @param keyStore    Relative path to keyStore file (key-store.jks) (only used with SASL_EXTERNAL)
     * @param keyStorePassword Used to verify keyStore integrity, actually closer to a checksum than password
     */
    public AmqpSettings(String host, int port, String user, String password, String virtualHost, int heartbeatTimeSeconds, int ttlMilliseconds,
                        boolean ssl, String trustStore, String trustStorePassword, String keyStore, String keyStorePassword)
    {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.virtualHost = virtualHost;
        this.heartbeatTimeSeconds = heartbeatTimeSeconds;
        this.ttlMilliseconds = ttlMilliseconds;
        this.ssl = ssl;
        this.trustStore = trustStore;
        this.trustStorePassword = trustStorePassword;
        this.keyStore = keyStore;
        this.keyStorePassword = keyStorePassword;
    }

    /**
     * loads the connection settings from a properties object. Properties can be loaded using any of the standard
     * java methods, example below.
     * <pre>
     *   Properties props = new Properties();
     *   try
     *   {
     *     FileInputStream fis = new FileInputStream( "../org.totalgrid.reef.test.cfg" );
     *     props.load( fis );
     *     fis.close();
     *   }
     *   catch ( IOException e )
     *   {
     *     e.printStackTrace();
     *     // we'll then throw an exception when trying to load from emtpy properties file
     *   }
     *   new AmqpSettings( props );
     * </pre>
     *
     * @param props properties object loaded with appropriate org.totalgrid.reef.amqp settings
     * @throws IllegalArgumentException if needed entries are missing
     */
    public AmqpSettings(Dictionary<Object, Object> props) throws IllegalArgumentException
    {
        host = PropertyLoading.getString("io.greenbus.msg.amqp.host", props);
        port = PropertyLoading.getInt( "io.greenbus.msg.amqp.port", props );
        user = PropertyLoading.getString( "io.greenbus.msg.amqp.user", props );
        password = PropertyLoading.getString( "io.greenbus.msg.amqp.password", props );
        virtualHost = PropertyLoading.getString( "io.greenbus.msg.amqp.virtualHost", props );
        heartbeatTimeSeconds = PropertyLoading.getInt( "io.greenbus.msg.amqp.heartbeatTimeSeconds", props );
        ttlMilliseconds = PropertyLoading.getInt( "io.greenbus.msg.amqp.ttl", props, TTL_DEFAULT );
        ssl = PropertyLoading.getBoolean( "io.greenbus.msg.amqp.ssl", props, false );
        trustStore = PropertyLoading.getString( "io.greenbus.msg.amqp.trustStore", props, "" );
        trustStorePassword = PropertyLoading.getString( "io.greenbus.msg.amqp.trustStorePassword", props, "" );
        keyStore = PropertyLoading.getString( "io.greenbus.msg.amqp.keyStore", props, "" );
        keyStorePassword = PropertyLoading.getString( "io.greenbus.msg.amqp.keyStorePassword", props, "" );
    }

    /**
     * loads the AmqpSettings object from a cfg text file.
     *
     * Equivalent to:
     * <pre>
     *     new AmqpSettings(PropertyReader.readFromFile( fileName ));
     * </pre>
     * @param fileName name of the cfg file to use.
     * @throws IllegalArgumentException
     * @throws java.io.IOException
     */
    public AmqpSettings(String fileName) throws IllegalArgumentException, IOException
    {
        this( PropertyReader.readFromFile(fileName) );
    }

    @Override
    public String toString()
    {
        return ssl ? "amqps:/" + user + "@" + host + ":" + port + "/" + virtualHost + "{" + trustStore + "}" : "amqp:/" + user + "@" + host + ":"
            + port + "/" + virtualHost;
    }

    /**
     * @return host name or ip address
     */
    public String getHost()
    {
        return host;
    }

    /**
     * @return TCP port
     */
    public int getPort()
    {
        return port;
    }

    /**
     * @return username
     */
    public String getUser()
    {
        return user;
    }

    /**
     * @return password
     */
    public String getPassword()
    {
        return password;
    }

    /**
     * @return virtual host
     */
    public String getVirtualHost()
    {
        return virtualHost;
    }

    /**
     * @return heartbeat time in seconds, 0 disables
     */
    public int getHeartbeatTimeSeconds()
    {
        return heartbeatTimeSeconds;
    }

    /**
     * @return ttl time for published messages before they are dropped by broker, 0 disables
     */
    public int getTtlMilliseconds()
    {
        return ttlMilliseconds;
    }

    /**
     * @return whether connection is using ssl
     */
    public boolean getSsl()
    {
        return ssl;
    }

    /**
     * @return path of trust store file
     */
    public String getTrustStore()
    {
        return trustStore;
    }

    /**
     * @return password for trust store password
     */
    public String getTrustStorePassword()
    {
        return trustStorePassword;
    }

    /**
     * @return path of key store file
     */
    public String getKeyStore()
    {
        return keyStore;
    }

    /**
     * @return password for key store password
     */
    public String getKeyStorePassword()
    {
        return keyStorePassword;
    }

}
