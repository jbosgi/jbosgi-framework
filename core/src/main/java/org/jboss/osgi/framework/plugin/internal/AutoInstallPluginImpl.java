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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.FrameworkState;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.AutoInstallPlugin;
import org.jboss.osgi.spi.util.StringPropertyReplacer;
import org.jboss.osgi.spi.util.StringPropertyReplacer.PropertyProvider;
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

   @Override
   public void initPlugin()
   {
      FrameworkState framework = getBundleManager().getFrameworkState();
      String propValue = framework.getProperty(PROP_JBOSS_OSGI_AUTO_INSTALL);
      if (propValue != null)
      {
         for (String path : propValue.split(","))
         {
            URL url = toURL(path.trim());
            if (url != null)
            {
               addAutoInstall(url);
            }
         }
      }
      propValue = framework.getProperty(PROP_JBOSS_OSGI_AUTO_START);
      if (propValue != null)
      {
         for (String path : propValue.split(","))
         {
            URL url = toURL(path.trim());
            if (url != null)
            {
               addAutoStart(url);
            }
         }
      }
   }

   @Override
   public void startPlugin()
   {
      try
      {
         installBundles();
         startBundles();
      }
      catch (BundleException ex)
      {
         throw new IllegalStateException("Cannot start auto install bundles", ex);
      }
   }

   @Override
   public void destroyPlugin()
   {
      autoInstall = null;
      autoStart = null;
   }

   @Override
   public void installBundles() throws BundleException
   {
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

   @Override
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

   private URL toURL(String path)
   {
      URL pathURL = null;
      PropertyProvider provider = new PropertyProvider()
      {
         @Override
         public String getProperty(String key)
         {
            return getBundleManager().getFrameworkState().getProperty(key);
         }
      };
      String realPath = StringPropertyReplacer.replaceProperties(path, provider);
      try
      {
         pathURL = new URL(realPath);
      }
      catch (MalformedURLException ex)
      {
         // ignore
      }

      if (pathURL == null)
      {
         try
         {
            File file = new File(realPath);
            if (file.exists())
               pathURL = file.toURI().toURL();
         }
         catch (MalformedURLException ex)
         {
            throw new IllegalArgumentException("Invalid path: " + realPath, ex);
         }
      }

      if (pathURL == null)
         throw new IllegalArgumentException("Invalid path: " + realPath);

      return pathURL;
   }
}