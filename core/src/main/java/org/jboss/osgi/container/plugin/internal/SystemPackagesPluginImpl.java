/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.container.plugin.internal;

//$Id$

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.FrameworkState;
import org.jboss.osgi.container.plugin.AbstractPlugin;
import org.jboss.osgi.container.plugin.SystemPackagesPlugin;
import org.osgi.framework.Constants;

/**
 * A plugin manages the Framework's system packages.
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class SystemPackagesPluginImpl extends AbstractPlugin implements SystemPackagesPlugin
{
   // Provide logging
   final Logger log = Logger.getLogger(SystemPackagesPluginImpl.class);

   // The derived combination of all system packages 
   private List<String> allPackages = new ArrayList<String>();
   // The derived combination of all system packages without version specifier 
   private List<String> allPackageNames = new ArrayList<String>();
   // The boot delegation packages 
   private List<String> bootDelegationPackages = new ArrayList<String>();

   public SystemPackagesPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
   }

   private void initSystemPackages()
   {
      FrameworkState frameworkState = getBundleManager().getFrameworkState();
      String systemPackages = frameworkState.getProperty(Constants.FRAMEWORK_SYSTEMPACKAGES);

      if (systemPackages != null)
      {
         allPackages.addAll(packagesAsList(systemPackages));
      }
      else
      {
         // The default system packages
         allPackages.add("javax.imageio");
         allPackages.add("javax.imageio.stream");

         allPackages.add("javax.management");
         allPackages.add("javax.management.loading");
         allPackages.add("javax.management.modelmbean");
         allPackages.add("javax.management.monitor");
         allPackages.add("javax.management.openmbean");
         allPackages.add("javax.management.relation");
         allPackages.add("javax.management.remote");
         allPackages.add("javax.management.remote.rmi");
         allPackages.add("javax.management.timer");

         allPackages.add("javax.naming");
         allPackages.add("javax.naming.event");
         allPackages.add("javax.naming.spi");

         allPackages.add("javax.net");
         allPackages.add("javax.net.ssl");

         allPackages.add("javax.security.cert");

         allPackages.add("javax.xml.datatype");
         allPackages.add("javax.xml.namespace");
         allPackages.add("javax.xml.parsers");
         allPackages.add("javax.xml.validation");
         allPackages.add("javax.xml.transform");
         allPackages.add("javax.xml.transform.dom");
         allPackages.add("javax.xml.transform.sax");
         allPackages.add("javax.xml.transform.stream");

         allPackages.add("org.jboss.osgi.deployment.deployer");
         allPackages.add("org.jboss.osgi.deployment.interceptor");
         allPackages.add("org.jboss.osgi.modules");

         allPackages.add("org.osgi.framework;version=1.5");
         allPackages.add("org.osgi.framework.hooks;version=1.0");
         allPackages.add("org.osgi.framework.hooks.service;version=1.0");
         allPackages.add("org.osgi.framework.launch;version=1.0");
         allPackages.add("org.osgi.service.condpermadmin;version=1.1");
         allPackages.add("org.osgi.service.packageadmin;version=1.2");
         allPackages.add("org.osgi.service.permissionadmin;version=1.2");
         allPackages.add("org.osgi.service.startlevel;version=1.1");
         allPackages.add("org.osgi.service.url;version=1.0");

         allPackages.add("org.w3c.dom");
         allPackages.add("org.w3c.dom.bootstrap");
         allPackages.add("org.w3c.dom.ls");
         allPackages.add("org.w3c.dom.events");
         allPackages.add("org.w3c.dom.ranges");
         allPackages.add("org.w3c.dom.views");
         allPackages.add("org.w3c.dom.traversal");

         allPackages.add("org.xml.sax");
         allPackages.add("org.xml.sax.ext");
         allPackages.add("org.xml.sax.helpers");

         String asString = packagesAsString(allPackages);
         frameworkState.setProperty(Constants.FRAMEWORK_SYSTEMPACKAGES, asString);
      }

      // Add the extra system packages
      String extraPackages = frameworkState.getProperty(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA);
      if (extraPackages != null)
      {
         allPackages.addAll(packagesAsList(extraPackages));
      }

      Collections.sort(allPackages);

      // Initialize the package names (without the version)
      for (String name : allPackages)
      {
         log.debug("   " + name);
         int semiIndex = name.indexOf(';');
         if (semiIndex > 0)
            name = name.substring(0, semiIndex);

         allPackageNames.add(name);
      }
      
      // Initialize the boot delegation package names
      String bootDelegationProp = frameworkState.getProperty(Constants.FRAMEWORK_BOOTDELEGATION);
      if (bootDelegationProp != null)
      {
         String[] packageNames = bootDelegationProp.split(",");
         for (String packageName : packageNames)
         {
            bootDelegationPackages.add(packageName);
         }
      }
   }

   @Override
   public List<String> getBootDelegationPackages()
   {
      if (allPackages.isEmpty())
         initSystemPackages();
      
      return Collections.unmodifiableList(bootDelegationPackages);
   }

   @Override
   public boolean isBootDelegationPackage(String name)
   {
      if (name == null)
         throw new IllegalArgumentException("Null package name");

      if (allPackages.isEmpty())
         initSystemPackages();

      if (bootDelegationPackages.contains(name))
         return true;
      
      // Match foo with foo.*
      for (String aux : bootDelegationPackages)
      {
         if (aux.equals(name + ".*"))
            return true;
      }
      
      return false;
   }

   public List<String> getSystemPackages(boolean version)
   {
      if (allPackages.isEmpty())
         initSystemPackages();

      return Collections.unmodifiableList(version ? allPackages : allPackageNames);
   }

   public boolean isSystemPackage(String name)
   {
      if (name == null)
         throw new IllegalArgumentException("Null package name");

      if (allPackages.isEmpty())
         initSystemPackages();

      // [TODO] version specifier for system packages
      int semiIndex = name.indexOf(';');
      if (semiIndex > 0)
         name = name.substring(0, semiIndex);

      return allPackageNames.contains(name);
   }

   private String packagesAsString(List<String> sysPackages)
   {
      StringBuffer result = new StringBuffer();
      for (int i = 0; i < sysPackages.size(); i++)
      {
         if (i > 0)
            result.append(",");
         result.append(sysPackages.get(i));
      }
      return result.toString();
   }

   private List<String> packagesAsList(String sysPackages)
   {
      List<String> result = new ArrayList<String>();
      for (String name : sysPackages.split(","))
      {
         name = name.trim();
         if (name.length() > 0)
            result.add(name);
      }
      return result;
   }
}