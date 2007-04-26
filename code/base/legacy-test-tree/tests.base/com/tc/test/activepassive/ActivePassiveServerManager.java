/*
 * All content copyright (c) 2003-2007 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.test.activepassive;

import com.tc.config.schema.setup.TestTVSConfigurationSetupManagerFactory;
import com.tc.management.beans.L2MBeanNames;
import com.tc.management.beans.TCServerInfoMBean;
import com.tc.objectserver.control.ExtraProcessServerControl;
import com.tc.objectserver.control.ServerControl;
import com.tc.properties.TCPropertiesImpl;
import com.tc.util.PortChooser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class ActivePassiveServerManager {
  private static final String                    HOST             = "localhost";
  private static final String                    SERVER_NAME      = "testserver";
  private static final String                    CONFIG_FILE_NAME = "active-passive-server-config.xml";
  private static final boolean                   DEBUG            = false;
  private static final int                       NULL_VAL         = -1;

  private final File                             tempDir;
  private final PortChooser                      portChooser;
  private final String                           configModel;
  private final ActivePassiveTestSetupManager    setupManger;
  private final long                             startTimeout;

  private final int                              serverCount;
  private final String                           serverCrashMode;
  private final long                             serverCrashWaitTimeInSec;
  private final String                           serverPersistence;
  private final boolean                          serverNetworkShare;
  private final ActivePassiveServerConfigCreator serverConfigCreator;
  private final String                           configFileLocation;
  private final File                             configFile;

  private final ServerInfo[]                     servers;
  private final int[]                            dsoPorts;
  private final int[]                            jmxPorts;
  private final String[]                         serverNames;

  private final List                             errors;

  private int                                    activeIndex      = NULL_VAL;
  private int                                    lastCrashedIndex = NULL_VAL;
  private ActivePassiveServerCrasher             serverCrasher;
  private int                                    maxCrashCount;
  private final TestState                        testState;

  public ActivePassiveServerManager(boolean isActivePassiveTest, File tempDir, PortChooser portChooser,
                                    String configModel, ActivePassiveTestSetupManager setupManger, long startTimeout)
      throws Exception {
    if (!isActivePassiveTest) { throw new AssertionError("A non-ActivePassiveTest is trying to use this class."); }

    this.setupManger = setupManger;

    serverCount = this.setupManger.getServerCount();

    if (serverCount < 2) { throw new AssertionError("Active-passive tests involve 2 or more DSO servers: serverCount=["
                                                    + serverCount + "]"); }

    this.tempDir = tempDir;
    configFileLocation = this.tempDir + File.separator + CONFIG_FILE_NAME;
    configFile = new File(configFileLocation);

    this.portChooser = portChooser;
    this.configModel = configModel;
    this.startTimeout = startTimeout * 2;

    serverCrashMode = this.setupManger.getServerCrashMode();
    serverCrashWaitTimeInSec = this.setupManger.getServerCrashWaitTimeInSec();
    maxCrashCount = this.setupManger.getMaxCrashCount();
    serverPersistence = this.setupManger.getServerPersistenceMode();
    serverNetworkShare = this.setupManger.isNetworkShare();

    servers = new ServerInfo[this.serverCount];
    dsoPorts = new int[this.serverCount];
    jmxPorts = new int[this.serverCount];
    serverNames = new String[this.serverCount];
    createServers();

    serverConfigCreator = new ActivePassiveServerConfigCreator(this.serverCount, dsoPorts, jmxPorts, serverNames,
                                                               serverPersistence, serverNetworkShare, this.configModel,
                                                               configFile, this.tempDir);
    serverConfigCreator.writeL2Config();

    errors = new ArrayList();
    testState = new TestState();
  }

  private void resetActiveIndex() {
    activeIndex = NULL_VAL;
  }

  private void resetLastCrashedIndex() {
    lastCrashedIndex = NULL_VAL;
  }

  private void createServers() throws FileNotFoundException {
    int startIndex = 0;

    if (DEBUG) {
      dsoPorts[0] = 8510;
      jmxPorts[0] = 8520;
      serverNames[0] = SERVER_NAME + 0;
      servers[0] = new ServerInfo(HOST, serverNames[0], dsoPorts[0], jmxPorts[0], getServerControl(dsoPorts[0],
                                                                                                   jmxPorts[0],
                                                                                                   serverNames[0]));
      dsoPorts[1] = 7510;
      jmxPorts[1] = 7520;
      serverNames[1] = SERVER_NAME + 1;
      servers[1] = new ServerInfo(HOST, serverNames[1], dsoPorts[1], jmxPorts[1], getServerControl(dsoPorts[1],
                                                                                                   jmxPorts[1],
                                                                                                   serverNames[1]));
      if (dsoPorts.length > 2) {
        dsoPorts[2] = 6510;
        jmxPorts[2] = 6520;
        serverNames[2] = SERVER_NAME + 2;
        servers[2] = new ServerInfo(HOST, serverNames[2], dsoPorts[2], jmxPorts[2], getServerControl(dsoPorts[2],
                                                                                                     jmxPorts[2],
                                                                                                     serverNames[2]));
      }

      startIndex = 3;
    }

    for (int i = startIndex; i < dsoPorts.length; i++) {
      dsoPorts[i] = getUnusedPort("dso");
      jmxPorts[i] = getUnusedPort("jmx");
      serverNames[i] = SERVER_NAME + i;
      servers[i] = new ServerInfo(HOST, serverNames[i], dsoPorts[i], jmxPorts[i], getServerControl(dsoPorts[i],
                                                                                                   jmxPorts[i],
                                                                                                   serverNames[i]));
    }
  }

  private int getUnusedPort(String type) {
    if (type == null || (!type.equalsIgnoreCase("dso") && !type.equalsIgnoreCase("jmx"))) { throw new AssertionError(
                                                                                                                     "Unrecognizable type=["
                                                                                                                         + type
                                                                                                                         + "]"); }
    int port = -1;
    while (port < 0) {
      int newPort = portChooser.chooseRandomPort();
      boolean used = false;
      for (int i = 0; i < dsoPorts.length; i++) {
        if (dsoPorts[i] == newPort) {
          used = true;
        }
      }
      if (used) {
        continue;
      }
      for (int i = 0; i < jmxPorts.length; i++) {
        if (jmxPorts[i] == newPort) {
          used = true;
        }
      }
      if (!used) {
        port = newPort;
      }
    }
    return port;
  }

  private ServerControl getServerControl(int dsoPort, int jmxPort, String serverName) throws FileNotFoundException {
    List jvmArgs = new ArrayList();
    if (serverNetworkShare) {
      jvmArgs.add("-D" + TCPropertiesImpl.SYSTEM_PROP_PREFIX + ".l2.ha.network.enabled=true");
    }
    return new ExtraProcessServerControl(HOST, dsoPort, jmxPort, configFileLocation, true, serverName, jvmArgs);
  }

  public void startServers() throws Exception {
    if (activeIndex >= 0) { throw new AssertionError("Server(s) has/have been already started"); }

    activeIndex = 0;

    startActive();
    startPassives();

    if (serverNetworkShare) {
      debugPrintln("***** startServers():  about to search for active  threadId=[" + Thread.currentThread().getName()
                   + "]");
      activeIndex = getActiveIndex();
    } else {
      activeIndex = 0;
    }

    if (serverCrashMode.equals(ActivePassiveCrashMode.CONTINUOUS_ACTIVE_CRASH)) {
      startContinuousCrash();
    }
  }

  private void startContinuousCrash() {
    serverCrasher = new ActivePassiveServerCrasher(this, serverCrashWaitTimeInSec, maxCrashCount);
    new Thread(serverCrasher).start();
  }

  public void storeErrors(Exception e) {
    if (e != null) {
      synchronized (errors) {
        errors.add(e);
      }
    }
  }

  public List getErrors() {
    synchronized (errors) {
      List l = new ArrayList();
      l.addAll(errors);
      return l;
    }
  }

  private int getActiveIndex() throws Exception {
    int index = -1;
    while (index < 0) {
      System.out.println("Searching for active server... ");
      for (int i = 0; i < jmxPorts.length; i++) {
        if (i != lastCrashedIndex) {
          JMXConnector jmxConnector = getJMXConnector(jmxPorts[i]);
          MBeanServerConnection mbs = jmxConnector.getMBeanServerConnection();
          TCServerInfoMBean mbean = (TCServerInfoMBean) MBeanServerInvocationHandler
              .newProxyInstance(mbs, L2MBeanNames.TC_SERVER_INFO, TCServerInfoMBean.class, true);

          debugPrintln("********  index=[" + index + "]  i=[" + i + "] active=[" + mbean.isActive() + "]  threadId=["
                       + Thread.currentThread().getName() + "]");

          if (mbean.isActive()) {
            if (index < 0) {
              index = i;
              debugPrintln("***** active found index=[" + index + "]");
            } else {
              jmxConnector.close();
              throw new Exception("More than one active server found.");
            }
          }

          jmxConnector.close();
        }
      }
      Thread.sleep(1000);
    }
    return index;
  }

  private void debugPrintln(String s) {
    if (DEBUG) {
      System.err.println(s);
    }
  }

  private void waitForPassive() throws Exception {
    while (true) {
      System.out.println("Searching for appropriate passive server(s)... ");
      for (int i = 0; i < jmxPorts.length; i++) {
        if (i != activeIndex) {
          JMXConnector jmxConnector = null;
          try {
            jmxConnector = getJMXConnector(jmxPorts[i]);
            MBeanServerConnection mbs = jmxConnector.getMBeanServerConnection();
            TCServerInfoMBean mbean = (TCServerInfoMBean) MBeanServerInvocationHandler
                .newProxyInstance(mbs, L2MBeanNames.TC_SERVER_INFO, TCServerInfoMBean.class, true);
            if (serverNetworkShare && mbean.isPassiveStandby()) {
              return;
            } else if (!serverNetworkShare && mbean.isStarted()) { return; }
          } catch (Exception e) {
            throw e;
          } finally {
            if (jmxConnector != null) {
              jmxConnector.close();
            }
          }
        }
        Thread.sleep(1000);
      }
    }
  }

  private JMXConnector getJMXConnector(int jmxPort) throws IOException {
    String url = "service:jmx:rmi:///jndi/rmi://" + HOST + ":" + jmxPort + "/jmxrmi";
    JMXServiceURL jmxServerUrl = new JMXServiceURL(url);
    JMXConnector jmxConnector = JMXConnectorFactory.newJMXConnector(jmxServerUrl, null);
    jmxConnector.connect();
    return jmxConnector;
  }

  public void stopAllServers() throws Exception {
    debugPrintln("***** setting TestState to STOPPING");
    testState.setTestState(TestState.STOPPING);

    if (serverCrasher != null) {
      debugPrintln("***** stopping server crasher");
      serverCrasher.stop();
    }

    for (int i = 0; i < serverCount; i++) {
      debugPrintln("***** stopping server=[" + servers[i].getDsoPort() + "]");
      ServerControl sc = servers[i].getServerControl();
      if (sc.isRunning()) {
        if (i == activeIndex) {
          sc.shutdown();
        } else {
          try {
            sc.crash();
          } catch (Exception e) {
            if (DEBUG) {
              e.printStackTrace();
            }
          }
        }
      }
    }
  }

  private void startActive() throws Exception {
    servers[activeIndex].getServerControl().start(startTimeout);
    Thread.sleep(500);
  }

  private void startPassives() throws Exception {
    for (int i = 0; i < servers.length; i++) {
      if (i != activeIndex) {
        servers[i].getServerControl().start(startTimeout);
      }
    }
    Thread.sleep(500 * (servers.length - 1));
  }

  public void crashActive() throws Exception {
    if (!testState.isRunning()) {
      debugPrintln("***** test state is not running ... skipping crash active");
      return;
    }

    debugPrintln("***** Crashing active server ");

    if (activeIndex < 0) { throw new AssertionError("Active index was not set."); }

    debugPrintln("***** wait to find an appropriate passive server.");
    waitForPassive();
    debugPrintln("***** finished waiting to find an appropriate passive server.");

    ServerControl server = servers[activeIndex].getServerControl();
    server.crash();
    debugPrintln("***** Sleeping after crashing active server ");
    waitForServerCrash(server);
    debugPrintln("***** Done sleeping after crashing active server ");

    lastCrashedIndex = activeIndex;
    resetActiveIndex();
    debugPrintln("***** lastCrashedIndex[" + lastCrashedIndex + "] ");

    debugPrintln("***** about to search for active  threadId=[" + Thread.currentThread().getName() + "]");
    activeIndex = getActiveIndex();
    debugPrintln("***** activeIndex[" + activeIndex + "] ");
  }

  private void waitForServerCrash(ServerControl server) throws Exception {
    long duration = 5000;
    long startTime = System.currentTimeMillis();
    while (duration > (System.currentTimeMillis() - startTime)) {
      if (server.isRunning()) {
        Thread.sleep(1000);
      } else {
        return;
      }
    }
    throw new Exception("Server crash did not complete.");
  }

  public void restartLastCrashedServer() throws Exception {
    if (!testState.isRunning()) {
      debugPrintln("***** test state is not running ... skipping restart");
      return;
    }

    debugPrintln("*****  restarting crashed server");

    if (lastCrashedIndex >= 0) {
      servers[lastCrashedIndex].getServerControl().start(startTimeout);
      resetLastCrashedIndex();
    } else {
      throw new AssertionError("No crashed servers to restart.");
    }
  }

  public int getServerCount() {
    return serverCount;
  }

  public int[] getDsoPorts() {
    return dsoPorts;
  }

  public int[] getJmxPorts() {
    return jmxPorts;
  }

  public boolean crashActiveServerAfterMutate() {
    if (serverCrashMode.equals(ActivePassiveCrashMode.MUTATE_VALIDATE)) { return true; }
    return false;
  }

  public void addServersToL1Config(TestTVSConfigurationSetupManagerFactory configFactory) {
    for (int i = 0; i < serverCount; i++) {

      debugPrintln("******* adding to L1 config: serverName=[" + serverNames[i] + "] dsoPort=[" + dsoPorts[i]
                   + "] jmxPort=[" + jmxPorts[i] + "]");

      configFactory.addServerToL1Config(serverNames[i], dsoPorts[i], jmxPorts[i]);
    }
  }

  /*
   * Server inner class
   */
  private static class ServerInfo {
    private final String        server_host;
    private final String        server_name;
    private final int           server_dsoPort;
    private final int           server_jmxPort;
    private final ServerControl serverControl;
    private String              dataLocation;
    private String              logLocation;

    ServerInfo(String host, String name, int dsoPort, int jmxPort, ServerControl serverControl) {
      this.server_host = host;
      this.server_name = name;
      this.server_dsoPort = dsoPort;
      this.server_jmxPort = jmxPort;
      this.serverControl = serverControl;
    }

    public String getHost() {
      return server_host;
    }

    public String getName() {
      return server_name;
    }

    public int getDsoPort() {
      return server_dsoPort;
    }

    public int getJmxPort() {
      return server_jmxPort;
    }

    public ServerControl getServerControl() {
      return serverControl;
    }

    public void setDataLocation(String location) {
      dataLocation = location;
    }

    public String getDataLocation() {
      return dataLocation;
    }

    public void setLogLocation(String location) {
      logLocation = location;
    }

    public String getLogLocation() {
      return logLocation;
    }
  }

  /*
   * State inner class
   */
  private static class TestState {
    public static final int RUNNING  = 0;
    public static final int STOPPING = 1;
    private int             state;

    public TestState() {
      state = RUNNING;
    }

    public synchronized void setTestState(int state) {
      this.state = state;
    }

    public synchronized boolean isRunning() {
      return state == RUNNING;
    }
  }

}
