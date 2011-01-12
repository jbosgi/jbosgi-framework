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

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.jboss.osgi.framework.bundle.AbstractBundle;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.osgi.framework.Bundle;
import org.osgi.service.url.AbstractURLStreamHandlerService;

/**
 * A handler for the 'bundle' protocol.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jan-2011
 */
public class BundleProtocolHandlerService extends AbstractURLStreamHandlerService
{
   public static final String PROTOCOL_NAME = "bundle";
   public static final String HOST_PREFIX = "jbosgi-";
   
   private final BundleManager bundleManager;
   
   BundleProtocolHandlerService(BundleManager bundleManager)
   {
      this.bundleManager = bundleManager;
   }
   
   public static URL getBundleURL(Bundle bundle, String path) throws IOException
   {
      if (bundle == null)
         throw new IllegalArgumentException("Null bundle");
      if (path == null)
         throw new IllegalArgumentException("Null path");
      
      return new URL(PROTOCOL_NAME, HOST_PREFIX + bundle.getBundleId(), path.startsWith("/") ? path : "/" + path);
   }

   @Override
   public URLConnection openConnection(URL url) throws IOException
   {
      String host = url.getHost();
      if (host.startsWith(HOST_PREFIX) == false)
         return url.openConnection();
      
      long bundleId = Long.parseLong(host.substring(HOST_PREFIX.length()));
      AbstractBundle bundleState = bundleManager.getBundleById(bundleId);
      if (bundleState == null)
         throw new IOException("Cannot obtain bundle for: " + url);
      
      String path = url.getPath();
      URL vfsURL = bundleState.getEntry(path);
      return vfsURL.openConnection();
   }

}
