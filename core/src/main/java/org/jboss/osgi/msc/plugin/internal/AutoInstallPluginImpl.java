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
package org.jboss.osgi.msc.plugin.internal;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.logging.Logger;
import org.jboss.osgi.msc.bundle.BundleManager;
import org.jboss.osgi.msc.bundle.FrameworkState;
import org.jboss.osgi.msc.plugin.AbstractPlugin;
import org.jboss.osgi.msc.plugin.AutoInstallPlugin;
import org.jboss.osgi.msc.util.URLHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * A plugin that installs/starts bundles on framework startup.
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class AutoInstallPluginImpl extends AbstractPlugin implements AutoInstallPlugin
{
   // Provide logging
   final Logger log = Logger.getLogger(AutoInstallPluginImpl.class);

   private List<URL> autoInstall;
   private List<URL> autoStart;
   private Map<URL, Bundle> autoBundles;

   public AutoInstallPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
   }

   public void setAutoInstall(List<URL> autoInstall)
   {
      this.autoInstall = autoInstall;
   }

   public void setAutoStart(List<URL> autoStart)
   {
      this.autoStart = autoStart;
   }

   private void initializePlugin()
   {
      if (autoInstall == null && autoStart == null)
      {
         FrameworkState frameworkState = getBundleManager().getFrameworkState();
         for (Entry<String, String> entry : frameworkState.getProperties().entrySet())
         {
            String key = entry.getKey();
            if (key.startsWith("org.jboss.osgi.auto.install"))
            {
               URL url = URLHelper.toURL(entry.getValue());
               if (url != null)
               {
                  addAutoInstall(url);
               }
            }
            if (key.startsWith("org.jboss.osgi.auto.start"))
            {
               URL url = URLHelper.toURL(entry.getValue());
               if (url != null)
               {
                  addAutoStart(url);
               }
            }
         }
      }
   }

   public void installBundles() throws BundleException
   {
      // Initialize the plugin
      initializePlugin();

      // Add the autoStart bundles to autoInstall
      if (autoStart != null)
      {
         for (URL bundleURL : autoStart)
         {
            addAutoInstall(bundleURL);
         }
      }

      // Install autoInstall bundles
      if (autoInstall != null)
      {
         for (URL bundleURL : autoInstall)
         {

            Bundle bundle = getBundleManager().installBundle(bundleURL);
            registerBundle(bundleURL, bundle);
         }
      }
   }

   public void startBundles() throws BundleException
   {
      // Start autoStart bundles
      if (autoStart != null)
      {
         for (URL bundleURL : autoStart)
         {
            Bundle bundle = autoBundles.get(bundleURL);
            if (bundle != null)
            {
               bundle.start();
            }
         }
      }
   }

   private void addAutoInstall(URL bundleURL)
   {
      if (autoInstall == null)
         autoInstall = new ArrayList<URL>();
      autoInstall.add(bundleURL);
   }

   private void addAutoStart(URL bundleURL)
   {
      if (autoStart == null)
         autoStart = new ArrayList<URL>();
      autoStart.add(bundleURL);
   }

   private void registerBundle(URL bundleURL, Bundle bundle)
   {
      if (autoBundles == null)
         autoBundles = new HashMap<URL, Bundle>();
      autoBundles.put(bundleURL, bundle);
   }
}