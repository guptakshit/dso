/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright notice.  All rights reserved.
 */
package com.tc.statistics;

import com.tc.config.schema.NewStatisticsConfig;
import com.tc.exception.TCRuntimeException;
import com.tc.statistics.beans.impl.StatisticsEmitterImpl;
import com.tc.statistics.beans.StatisticsEmitterMBean;
import com.tc.statistics.beans.StatisticsMBeanNames;
import com.tc.statistics.beans.impl.StatisticsManagerImpl;
import com.tc.statistics.beans.StatisticsManagerMBean;
import com.tc.statistics.buffer.StatisticsBuffer;
import com.tc.statistics.buffer.exceptions.TCStatisticsBufferException;
import com.tc.statistics.buffer.h2.H2StatisticsBufferImpl;
import com.tc.statistics.config.StatisticsConfig;
import com.tc.statistics.config.impl.StatisticsConfigImpl;
import com.tc.statistics.retrieval.StatisticsRetrievalRegistry;
import com.tc.statistics.retrieval.impl.StatisticsRetrievalRegistryImpl;
import com.tc.logging.TCLogger;
import com.tc.logging.CustomerLogging;

import java.io.File;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;

public class StatisticsSubSystem {
  private final static TCLogger logger        = CustomerLogging.getDSOGenericLogger();
  private final static TCLogger consoleLogger = CustomerLogging.getConsoleLogger();

  private StatisticsBuffer            statisticsBuffer;
  private StatisticsEmitterMBean      statisticsEmitterMBean;
  private StatisticsManagerMBean      statisticsManagerMBean;
  private StatisticsRetrievalRegistry statisticsRetrievalRegistry;

  private boolean active = false;

  public boolean isActive() {
    return active;
  }

  public void setDefaultAgentIp(final String defaultAgentIp) {
    if (null == statisticsBuffer) throw new AssertionError("The statistics subsystem has to be setup before.");
    statisticsBuffer.setDefaultAgentIp(defaultAgentIp);
  }

  public void setDefaultAgentDifferentiator(final String defaultAgentDifferentiator) {
    if (null == statisticsBuffer) throw new AssertionError("The statistics subsystem has to be setup before.");
    statisticsBuffer.setDefaultAgentDifferentiator(defaultAgentDifferentiator);
  }

  public boolean setup(final NewStatisticsConfig config) {
    StatisticsConfig globalStatisticsConfig = new StatisticsConfigImpl();
    
    // create the statistics buffer
    File statPath = config.statisticsPath().getFile();
    try {
      statPath.mkdirs();
    } catch (Exception e) {
      // TODO: needs to be properly written and put in a properties file
      String msg =
        "\n**************************************************************************************\n"
        + "Unable to create the directory '" + statPath.getAbsolutePath() + "' for the statistics buffer.\n"
        + "The CVT system will not be active for this node.\n"
        + "**************************************************************************************\n";
      consoleLogger.error(msg);
      logger.error(msg, e);
      return false;
    }
    try {
      statisticsBuffer = new H2StatisticsBufferImpl(globalStatisticsConfig, statPath);
      statisticsBuffer.open();
    } catch (TCStatisticsBufferException e) {
      // TODO: needs to be properly written and put in a properties file
      String msg =
        "\n**************************************************************************************\n"
        + "The statistics buffer couldn't be opened at \n"
        + "'" + statPath.getAbsolutePath() + "'.\n"
        + "The CVT system will not be active for this node.\n"
        + "\n"
        + "A common reason for this is that you're launching several Terracotta L1\n"
        + "clients on the same machine. The default directory for the statistics buffer\n"
        + "uses the IP address of the machine that it runs on as the identifier.\n"
        + "When several clients are being executed on the same machine, a typical solution\n"
        + "to properly separate these directories is by using a JVM property at startup\n"
        + "that is unique for each client.\n"
        + "\n"
        + "For example:\n"
        + "  dso-java.sh -Dtc.node-name=node1 your.main.Class\n"
        + "\n"
        + "You can then adapt the tc-config.xml file so that this JVM property is picked\n"
        + "up when the statistics directory is configured by using %(tc.node-name) in the\n"
        + "statistics path.\n"
        + "**************************************************************************************\n";
      consoleLogger.error(msg);
      logger.error(msg, e);
      return false;
    }
    String infoMsg = "Statistics buffer: '" + statPath.getAbsolutePath() + "'.";
    consoleLogger.info(infoMsg);
    logger.info(infoMsg);

    // create the statistics emitter mbean
    try {
      statisticsEmitterMBean = new StatisticsEmitterImpl(globalStatisticsConfig, statisticsBuffer);
    } catch (NotCompliantMBeanException e) {
      throw new TCRuntimeException("Unable to construct the " + StatisticsEmitterImpl.class.getName()
                                   + " MBean; this is a programming error. Please go fix that class.", e);
    }

    // setup an empty statistics retrieval registry
    statisticsRetrievalRegistry = new StatisticsRetrievalRegistryImpl();
    try {
      statisticsManagerMBean = new StatisticsManagerImpl(globalStatisticsConfig, statisticsRetrievalRegistry, statisticsBuffer);
    } catch (NotCompliantMBeanException e) {
      throw new TCRuntimeException("Unable to construct the " + StatisticsManagerImpl.class.getName()
                                   + " MBean; this is a programming error. Please go fix that class.", e);
    }

    active = true;
    return true;
  }

  public void registerMBeans(MBeanServer mBeanServer) throws MBeanRegistrationException, NotCompliantMBeanException, InstanceAlreadyExistsException {
    mBeanServer.registerMBean(statisticsEmitterMBean, StatisticsMBeanNames.STATISTICS_EMITTER);
    mBeanServer.registerMBean(statisticsManagerMBean, StatisticsMBeanNames.STATISTICS_MANAGER);
  }

  public void unregisterMBeans(MBeanServer mBeanServer) throws InstanceNotFoundException, MBeanRegistrationException {
    mBeanServer.unregisterMBean(StatisticsMBeanNames.STATISTICS_EMITTER);
    mBeanServer.unregisterMBean(StatisticsMBeanNames.STATISTICS_MANAGER);
  }

  public void disableJMX() throws Exception {
    if (statisticsEmitterMBean != null) {
      statisticsEmitterMBean.disable();
    }
  }

  public void cleanup() throws Exception {
    statisticsBuffer.close();
  }

  public StatisticsBuffer getStatisticsBuffer() {
    return statisticsBuffer;
  }

  public StatisticsEmitterMBean getStatisticsEmitterMBean() {
    return statisticsEmitterMBean;
  }

  public StatisticsManagerMBean getStatisticsManagerMBean() {
    return statisticsManagerMBean;
  }

  public StatisticsRetrievalRegistry getStatisticsRetrievalRegistry() {
    return statisticsRetrievalRegistry;
  }
}