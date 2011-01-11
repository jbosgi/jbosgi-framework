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

import java.lang.reflect.Field;
import java.net.URLConnection;
import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.BundleManager.IntegrationMode;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.ModuleManagerPlugin;
import org.jboss.osgi.framework.plugin.URLHandlerPlugin;

/**
 * This plugin provide OSGi URL Handler support. It is realized by plugging into the
 * {@link org.jboss.modules.ModularURLStreamHandlerFactory} class.
 *
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class URLHandlerPluginImpl extends AbstractPlugin implements URLHandlerPlugin
{
   private static final String MODULES_PROTOCOL_HANDLER_PROPERTY = "jboss.protocol.handler.modules";
   private final Logger log = Logger.getLogger(URLHandlerPluginImpl.class);
   private ModuleIdentifier frameworkModuleId;

   public URLHandlerPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
   }

   @Override
   public void initPlugin()
   {
      try
      {
         // TODO I would expect JBoss Modules to set this one too (MODULES-66)
         URLConnection.setContentHandlerFactory(new URLContentHandlerFactoryDelegate());
      }
      catch (Error e)
      {
         log.warn("Unable to set the ContentHandlerFactory on the URLConnection.", e);
      }
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @Override
   public void startPlugin()
   {
      frameworkModuleId = getBundleManager().getSystemBundle().getModuleIdentifier();
      ModuleManagerPlugin moduleManager = getPlugin(ModuleManagerPlugin.class);
      Module frameworkModule = moduleManager.getModule(frameworkModuleId);

      if (getBundleManager().getIntegrationMode() == IntegrationMode.STANDALONE)
      {
         try
         {
            // TODO the OSGiModuleLoader is aware of our system module but the system module loader isn't
            // this causes issues in standalone mode because the ModularURLStreamHandlerFactory looks for the
            // module in the System Module Loader.

            // Terrible hack to make the module system aware of the OSGi framework module.
            Field keyField = Module.class.getDeclaredField("myKey");
            keyField.setAccessible(true);
            Object fm = keyField.get(frameworkModule);

            Field mmapf = ModuleLoader.class.getDeclaredField("moduleMap");
            mmapf.setAccessible(true);
            ModuleLoader moduleLoader = getBundleManager().getSystemModuleLoader();
            Map mmap = (Map)mmapf.get(moduleLoader);
            mmap.put(frameworkModuleId, fm);
         }
         catch (Exception e)
         {
            // no point in doing anything intelligent here, instead we should get a proper
            // solution to the above...
            e.printStackTrace();
         }
      }

      URLHandlerFactory.initSystemBundleContext(getBundleManager().getSystemContext());
      URLContentHandlerFactoryDelegate.setDelegate(new URLContentHandlerFactory(getBundleManager().getSystemContext()));

      addModulesProtocolHandler(frameworkModuleId);
   }

   @Override
   public void stopPlugin()
   {
      removeModuleProtocolHandler(frameworkModuleId);
      URLHandlerFactory.cleanUp();
   }

   // Better would be to have a proper API, like
   //   ModularURLStreamHandlerFactory.addHandler(moduleID)
   private void addModulesProtocolHandler(ModuleIdentifier moduleId)
   {
      String val = System.getProperty(MODULES_PROTOCOL_HANDLER_PROPERTY);
      if (val == null)
         val = moduleId.getName();
      else
         val += "|" + moduleId.getName();
      System.setProperty(MODULES_PROTOCOL_HANDLER_PROPERTY, val);
   }

   // A lot of work to remove a module form the protocol handler.
   // Would really prefer an API like:
   //   ModularURLStreamHandlerFactory.removeHandler(moduleID);
   private void removeModuleProtocolHandler(ModuleIdentifier moduleId)
   {
      String val = System.getProperty(MODULES_PROTOCOL_HANDLER_PROPERTY);
      if (val == null)
         return;

      int idx = val.indexOf(MODULES_PROTOCOL_HANDLER_PROPERTY);
      if (idx < 0)
         return;

      String newVal = val.substring(0, idx);
      if (val.length() >= idx + MODULES_PROTOCOL_HANDLER_PROPERTY.length())
         newVal += val.substring(idx + MODULES_PROTOCOL_HANDLER_PROPERTY.length());

      int idx2 = val.indexOf("||");
      if (idx2 >= 0)
      {
         String s = newVal.substring(0, idx2);
         s += newVal.substring(idx2 + 1);
         newVal = s;
      }

      if (newVal.endsWith("|"))
         newVal = newVal.substring(0, newVal.length());

      if (newVal.startsWith("|") && newVal.length() >= 1)
         newVal = newVal.substring(1);

      System.setProperty(MODULES_PROTOCOL_HANDLER_PROPERTY, newVal);
   }
}
