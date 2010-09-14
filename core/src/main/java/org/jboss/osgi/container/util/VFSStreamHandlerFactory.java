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
package org.jboss.osgi.container.util;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.ServiceLoader;

/**
 * A URLStreamHandlerFactory that can be loaded from a {@link ServiceLoader}
 *
 * [JBVFS-164] Add a URLStreamHandlerFactory service
 *
 * [TODO] Remove when this is supported by jboss-vfs
 *
 * @author thomas.diesler@jboss.com
 * @since 14-Sep-2010
 */
public final class VFSStreamHandlerFactory implements URLStreamHandlerFactory
{
   @Override
   public URLStreamHandler createURLStreamHandler(String protocol)
   {
      String handlerName = "org.jboss.vfs.protocol." + protocol + ".Handler";
      try
      {
         Class<?> handlerClass = getClass().getClassLoader().loadClass(handlerName);
         return (URLStreamHandler)handlerClass.newInstance();
      }
      catch (Exception e)
      {
         return null;
      }
   }
}
