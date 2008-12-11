/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tctest.server.appserver.unit;

import com.meterware.httpunit.WebConversation;
import com.tc.test.server.util.TcConfigBuilder;
import com.tc.util.Assert;
import com.tc.util.concurrent.ThreadUtil;
import com.tctest.webapp.servlets.SessionLockingDeadlockServlet;

import junit.framework.Test;

public class DeadlockTestWithSessionLocking extends DeadlockTestBase {

  public DeadlockTestWithSessionLocking() {
    //
  }

  public static Test suite() {
    return new DeadlockTestWithSessionLockingSetup();
  }

  public void testSessionLocking() throws Exception {
    WebConversation conversation = new WebConversation();
    Thread requestSessionThenGlobalThread = new Thread(
                                                       new ParamBasedRequestRunner(
                                                                                   server0,
                                                                                   conversation,
                                                                                   CONTEXT,
                                                                                   "cmd="
                                                                                       + SessionLockingDeadlockServlet.LOCK_SESSION_THEN_GLOBAL));
    Thread requestGlobalThenSessionThread = new Thread(
                                                       new ParamBasedRequestRunner(
                                                                                   server0,
                                                                                   conversation,
                                                                                   CONTEXT,
                                                                                   "cmd="
                                                                                       + SessionLockingDeadlockServlet.LOCK_GLOBAL_THEN_SESSION));
    super.testSessionLocking(conversation, requestSessionThenGlobalThread, requestGlobalThenSessionThread);

    int waitTimeMillis = 30 * 1000;
    ThreadUtil.reallySleep(waitTimeMillis);

    if (!requestSessionThenGlobalThread.isAlive() || !requestGlobalThenSessionThread.isAlive()) {
      Assert.fail("Requests are NOT deadlocked. Requests are supposed to be deadlocked with session-locking=true");
    }
    debug("Test passed");
  }

  private static class DeadlockTestWithSessionLockingSetup extends DeadlockTestSetupBase {

    public DeadlockTestWithSessionLockingSetup() {
      super(DeadlockTestWithSessionLocking.class, CONTEXT);
    }

    protected void configureTcConfig(TcConfigBuilder tcConfigBuilder) {
      tcConfigBuilder.addWebApplicationWithSessionLocking(CONTEXT);
    }
  }
}
