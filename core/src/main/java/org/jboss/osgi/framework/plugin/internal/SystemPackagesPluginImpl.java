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
package org.jboss.osgi.framework.plugin.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.BundleManager.IntegrationMode;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.SystemPackagesPlugin;
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
   // The boot delegation packages
   private List<String> bootDelegationPackages = new ArrayList<String>();
   // The list of exported paths
   private Set<String> exportedPaths;

   public SystemPackagesPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);

      String systemPackages = (String)bundleManager.getProperty(Constants.FRAMEWORK_SYSTEMPACKAGES);
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

         allPackages.add("org.jboss.modules");

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

         // SchemaFactoryFinder attempting to use the platform default XML Schema validator
         allPackages.add("com.sun.org.apache.xerces.internal.jaxp.validation");

         // Only add non-jdk packages when running STANDALONE
         if (bundleManager.getIntegrationMode() == IntegrationMode.STANDALONE)
         {
            allPackages.addAll(getFrameworkPackages());
            allPackages.addAll(getInternalFrameworkPackages());
         }

         String asString = packagesAsString(allPackages);
         bundleManager.setProperty(Constants.FRAMEWORK_SYSTEMPACKAGES, asString);
      }

      // Add the extra system packages
      String extraPackages = (String)bundleManager.getProperty(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA);
      if (extraPackages != null)
         allPackages.addAll(packagesAsList(extraPackages));

      Collections.sort(allPackages);

      // Initialize the boot delegation package names
      String bootDelegationProp = (String)bundleManager.getProperty(Constants.FRAMEWORK_BOOTDELEGATION);
      if (bootDelegationProp != null)
      {
         String[] packageNames = bootDelegationProp.split(",");
         for (String packageName : packageNames)
         {
            bootDelegationPackages.add(packageName);
         }
      }
      else
      {
         bootDelegationPackages.add("sun.reflect");
      }
   }

   @Override
   public List<String> getFrameworkPackages()
   {
      List<String> packages = new ArrayList<String>();
      packages.add("org.jboss.msc.service");
      packages.add("org.jboss.osgi.deployment.deployer");
      packages.add("org.jboss.osgi.deployment.interceptor");
      packages.add("org.jboss.osgi.modules");

      packages.add("org.osgi.framework;version=1.5");
      packages.add("org.osgi.framework.hooks;version=1.0");
      packages.add("org.osgi.framework.hooks.service;version=1.0");
      packages.add("org.osgi.framework.launch;version=1.0");
      packages.add("org.osgi.service.condpermadmin;version=1.1");
      packages.add("org.osgi.service.packageadmin;version=1.2");
      packages.add("org.osgi.service.permissionadmin;version=1.2");
      packages.add("org.osgi.service.startlevel;version=1.1");
      packages.add("org.osgi.service.url;version=1.0");
      packages.add("org.osgi.util.tracker;version=1.4");
      return packages;
   }

   private List<String> getInternalFrameworkPackages()
   {
      // For the correct behaviour of the Module Classloader, all the internal packages
      // need to be listed
      List<String> packages = new ArrayList<String>();
      packages.add("org.jboss.osgi.framework");
      packages.add("org.jboss.osgi.framework.bundle");
      packages.add("org.jboss.osgi.framework.launch");
      packages.add("org.jboss.osgi.framework.loading");
      packages.add("org.jboss.osgi.framework.plugin");
      packages.add("org.jboss.osgi.framework.plugin.internal");
      packages.add("org.jboss.osgi.framework.util");

      // META-INF/services also needs to be added. JBoss modules exports this one by default in every module
      // this is depended on by the ModularURLStreamHandlerFactory which we hook into to provide URL handler support.
      packages.add("META-INF.services");
      return packages;
   }

   @Override
   public boolean isFrameworkPackage(String name)
   {
      assertInitialized();
      return isPackageNameInList(getFrameworkPackages(), name);
   }

   @Override
   public List<String> getBootDelegationPackages()
   {
      assertInitialized();
      return Collections.unmodifiableList(bootDelegationPackages);
   }

   @Override
   public boolean isBootDelegationPackage(String name)
   {
      assertInitialized();
      if (name == null)
         throw new IllegalArgumentException("Null package name");

      if (name.startsWith("java."))
         return true;

      if (bootDelegationPackages.contains(name))
         return true;

      // Match foo with foo.*
      for (String aux : bootDelegationPackages)
      {
         if (aux.endsWith(".*") && name.startsWith(aux.substring(0, aux.length() -2 )))
            return true;
      }

      return false;
   }

   @Override
   public List<String> getSystemPackages()
   {
      assertInitialized();
      return Collections.unmodifiableList(allPackages);
   }

   @Override
   public boolean isSystemPackage(String name)
   {
      assertInitialized();
      return isPackageNameInList(allPackages, name);
   }

   @Override
   public Set<String> getExportedPaths()
   {
      assertInitialized();
      if(exportedPaths == null)
      {
         Set<String> result = new LinkedHashSet<String>();

         // Add bootdelegation paths
         List<String> bootDelegationPackages = getBootDelegationPackages();
         for (String packageName : bootDelegationPackages)
         {
            if (packageName.endsWith(".*"))
               packageName = packageName.substring(0, packageName.length() - 2);

            result.add(packageName.replace('.', '/'));
         }

         // Add system packages exported by the framework
         List<String> systemPackages = getSystemPackages();
         for (String packageSpec : systemPackages)
         {
            int index = packageSpec.indexOf(';');
            if (index > 0)
               packageSpec = packageSpec.substring(0, index);

            result.add(packageSpec.replace('.', '/'));
         }
         exportedPaths = Collections.unmodifiableSet(result);
      }
      return exportedPaths;
   }

   private boolean isPackageNameInList(List<String> packages, String name)
   {
      if (name == null)
         throw new IllegalArgumentException("Null package name");

      int index = name.indexOf(';');
      if (index > 0)
         name = name.substring(0, index);

      if (packages.contains(name))
         return true;

      for (String aux : packages)
      {
         if (aux.startsWith(name + ";"))
            return true;
      }

      return false;
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

   private void assertInitialized()
   {
      if (allPackages.isEmpty())
         throw new IllegalStateException("SystemPackagesPlugin not initialized");
   }
}