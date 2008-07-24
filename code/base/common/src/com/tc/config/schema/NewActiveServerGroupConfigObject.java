/*
 * All content copyright (c) 2003-2007 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.config.schema;

import org.apache.xmlbeans.XmlObject;

import com.tc.config.schema.context.ConfigContext;
import com.tc.config.schema.defaults.DefaultValueProvider;
import com.tc.config.schema.repository.ChildBeanFetcher;
import com.tc.config.schema.repository.ChildBeanRepository;
import com.tc.config.schema.repository.MutableBeanRepository;
import com.tc.config.schema.setup.ConfigurationSetupException;
import com.tc.config.schema.setup.StandardL2TVSConfigurationSetupManager;
import com.terracottatech.config.ActiveServerGroup;
import com.terracottatech.config.Ha;
import com.terracottatech.config.Members;
import com.terracottatech.config.Server;
import com.terracottatech.config.Servers;

public class NewActiveServerGroupConfigObject extends BaseNewConfigObject implements NewActiveServerGroupConfig {

  // TODO: the defaultValueProvider is not implemented to fetch default values for attributes... possibly fix this and
  // use the commented code to set defaultId:
  // int defaultId = ((XmlInteger) defaultValueProvider.defaultFor(serversBeanRepository.rootBeanSchemaType(),
  // "active-server-groups/active-server-group[@id]")).getBigIntegerValue().intValue();
  public static final int        defaultGroupId = 0;

  private final int              groupId;
  private final NewHaConfig      haConfig;
  private final NewMembersConfig membersConfig;

  public NewActiveServerGroupConfigObject(ConfigContext context, StandardL2TVSConfigurationSetupManager setupManager)
      throws ConfigurationSetupException {
    super(context);
    context.ensureRepositoryProvides(ActiveServerGroup.class);
    ActiveServerGroup group = (ActiveServerGroup) context.bean();

    groupId = group.getId();
    checkNonNegative(groupId);

    membersConfig = new NewMembersConfigObject(createContext(setupManager, true, group));
    haConfig = new NewHaConfigObject(createContext(setupManager, false, group));
  }

  private void checkNonNegative(int id) throws ConfigurationSetupException {
    if (id < 0) { throw new ConfigurationSetupException(
        "Active-server-group id must be a non-negative integer:  group with id{" + id + "} should be modified."); }
  }

  public NewHaConfig getHa() {
    return this.haConfig;
  }

  public NewMembersConfig getMembers() {
    return this.membersConfig;
  }

  public int getId() {
    return this.groupId;
  }

  private final ConfigContext createContext(StandardL2TVSConfigurationSetupManager setupManager, boolean isMembers,
                                            final ActiveServerGroup group) {
    if (isMembers) {
      ChildBeanRepository beanRepository = new ChildBeanRepository(setupManager.serversBeanRepository(), Members.class,
          new ChildBeanFetcher() {
            public XmlObject getChild(XmlObject parent) {
              return group.getMembers();
            }
          });
      return setupManager.createContext(beanRepository, setupManager.getConfigFilePath());
    } else {
      ChildBeanRepository beanRepository = new ChildBeanRepository(setupManager.serversBeanRepository(), Ha.class,
          new ChildBeanFetcher() {
            public XmlObject getChild(XmlObject parent) {
              return group.getHa();
            }
          });
      return setupManager.createContext(beanRepository, setupManager.getConfigFilePath());
    }
  }

  public static ActiveServerGroup getDefaultActiveServerGroup(DefaultValueProvider defaultValueProvider,
                                                              MutableBeanRepository serversBeanRepository, Ha commonHa) {
    ActiveServerGroup asg = ActiveServerGroup.Factory.newInstance();
    asg.setId(defaultGroupId);
    asg.setHa(commonHa);
    Members members = asg.addNewMembers();
    Server[] serverArray = ((Servers) serversBeanRepository.bean()).getServerArray();

    for (int i = 0; i < serverArray.length; i++) {
      // name for each server should exist
      String name = serverArray[i].getName();
      if (name == null || name.equals("")) { throw new AssertionError("server's name not defined... name=[" + name
          + "] serverDsoPort=[" + serverArray[i].getDsoPort() + "]"); }
      members.insertMember(i, serverArray[i].getName());
    }

    return asg;
  }

}
