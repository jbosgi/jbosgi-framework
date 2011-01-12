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
public class BundleProtocolHandler extends AbstractURLStreamHandlerService
{
   public static final String PROTOCOL_NAME = "bundle";

   private final BundleManager bundleManager;

   BundleProtocolHandler(BundleManager bundleManager)
   {
      this.bundleManager = bundleManager;
   }

   public static URL getBundleURL(Bundle bundle, String path) throws IOException
   {
      if (bundle == null)
         throw new IllegalArgumentException("Null bundle");
      if (path == null)
         throw new IllegalArgumentException("Null path");

      return new URL(PROTOCOL_NAME, new Long(bundle.getBundleId()).toString(), path.startsWith("/") ? path : "/" + path);
   }

   @Override
   public URLConnection openConnection(URL url) throws IOException
   {
      URL vfsURL = toVirtualFileURL(url);
      if (vfsURL == null)
         throw new IOException("Cannot obtain virtual file URL for: " + url);
      
      return vfsURL.openConnection();
   }

   private URL toVirtualFileURL(URL url) throws IOException
   {
      if (PROTOCOL_NAME.equals(url.getProtocol()) == false)
         throw new IllegalArgumentException("Not a bundle url: " + url);

      long bundleId = Long.parseLong(url.getHost());
      AbstractBundle bundleState = bundleManager.getBundleById(bundleId);
      if (bundleState == null)
         throw new IOException("Cannot obtain bundle for: " + url);

      String path = url.getPath();
      return bundleState.getEntry(path);
   }
}
