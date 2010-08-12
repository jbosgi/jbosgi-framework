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
import java.net.URL;
import java.util.Enumeration;

import org.jboss.osgi.deployment.deployer.Deployment;
import org.osgi.framework.BundleException;

/**
 * A {@link FragmentRevision} is responsible for the classloading and resource loading of a fragment.
 * 
 * Every time a fragment is updated a new {@link FragmentRevision} is created and referenced 
 * from the {@link FragmentBundle}. 
 * 
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
public class FragmentRevision extends AbstractRevision
{
   public FragmentRevision(FragmentBundle internalBundle, Deployment dep, int revision) throws BundleException
   {
      super(internalBundle, dep, revision);
   }

   @Override
   Class<?> loadClass(String name) throws ClassNotFoundException
   {
      throw new ClassNotFoundException("Cannot load class from a fragment: " + this);
   }

   @Override
   URL getResource(String name)
   {
      // Null if the resource could not be found or if this bundle is a fragment bundle
      return null;
   }

   @Override
   Enumeration<URL> getResources(String name) throws IOException
   {
      // Null if the resource could not be found or if this bundle is a fragment bundle
      return null;
   }
}
