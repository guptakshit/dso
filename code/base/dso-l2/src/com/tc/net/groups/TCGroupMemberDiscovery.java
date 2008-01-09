/*
 * All content copyright (c) 2003-2007 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.net.groups;

public interface TCGroupMemberDiscovery {
  
  public Node[] getAllNodes();
  
  public void setTCGroupManager(TCGroupManager manager);
  
  public void start();
  
  public void stop();
  
  public void setLocalNode(Node local);
  
  public Node getLocalNode();

}