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
package org.jboss.osgi.container.loading;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleLogger;

/**
 * A {@link ModuleLogger} that delegates to jboss-logging.
 * 
 * @author thomas.diesler@jboss.com
 * @since 13-Jul-2010
 */
public class JBossLoggingModuleLogger implements ModuleLogger
{
   private Logger log;

   public JBossLoggingModuleLogger(Logger log)
   {
      if (log == null)
         throw new IllegalArgumentException("Null logger");
      this.log = log;
   }

   @Override
   public boolean isTraceEnabled()
   {
      return log.isTraceEnabled();
   }

   @Override
   public void trace(String message)
   {
      log.trace(message);
   }

   @Override
   public void trace(String message, Throwable th)
   {
      log.trace(message, th);
   }

   @Override
   public void debug(String message)
   {
      log.debug(message);
   }

   @Override
   public void debug(String message, Throwable th)
   {
      log.debug(message, th);
   }

   @Override
   public void warn(String message)
   {
      log.warn(message);
   }

   @Override
   public void warn(String message, Throwable th)
   {
      log.warn(message, th);
   }

   @Override
   public void error(String message)
   {
      log.error(message);
   }

   @Override
   public void error(String message, Throwable th)
   {
      log.error(message, th);
   }
}
