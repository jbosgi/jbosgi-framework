/*
* JBoss, Home of Professional Open Source
* Copyright 2009, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
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
package org.jboss.osgi.container.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * This is the internal implementation of a fragment Bundle. 
 * 
 * Fragment specific functionality is handled here. 
 * 
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
public class FragmentBundle extends DeploymentBundle
{
   private static final Logger log = Logger.getLogger(FragmentBundle.class);

   FragmentBundle(BundleManager bundleManager, Deployment deployment) throws BundleException
   {
      super(bundleManager, deployment);
   }

   @Override
   AbstractRevision createRevision(Deployment deployment, int revision) throws BundleException
   {
      return new FragmentBundleRevision(this, deployment, revision);
   }
   
   @Override
   public URL getResource(String name)
   {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public Class loadClass(String name) throws ClassNotFoundException
   {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public Enumeration getResources(String name) throws IOException
   {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public Enumeration getEntryPaths(String path)
   {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public URL getEntry(String path)
   {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public Enumeration findEntries(String path, String filePattern, boolean recurse)
   {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public Version getVersion()
   {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public void addToResolver()
   {
      // TODO Auto-generated method stub
      
   }

   @Override
   public boolean ensureResolved()
   {
      // TODO Auto-generated method stub
      return false;
   }

   @Override
   public void removeFromResolver()
   {
      // TODO Auto-generated method stub
      
   }

   @Override
   AbstractBundleContext createContextInternal()
   {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public String getLocation()
   {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public OSGiMetaData getOSGiMetaData()
   {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public XModule getResolverModule()
   {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public List<XModule> getAllResolverModules()
   {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   void startInternal(int options) throws BundleException
   {
      // TODO Auto-generated method stub
      
   }

   @Override
   void stopInternal(int options) throws BundleException
   {
      // TODO Auto-generated method stub
      
   }

   @Override
   void updateInternal(InputStream input) throws BundleException
   {
      // TODO Auto-generated method stub
      
   }

   @Override
   void uninstallInternal() throws BundleException
   {
      // TODO Auto-generated method stub
      
   }

   @Override
   URL getLocalizationEntry(String entryPath)
   {
      // TODO Auto-generated method stub
      return null;
   }
}
