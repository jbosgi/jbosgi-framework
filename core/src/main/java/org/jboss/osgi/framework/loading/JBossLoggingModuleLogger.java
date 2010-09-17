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
package org.jboss.osgi.framework.loading;

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
   public void greeting()
   {
      log.debug("ModuleLogger initialized");
   }

   @Override
   public void trace(String message)
   {
      if (log.isTraceEnabled())
         log.trace(message);
   }

   @Override
   public void trace(final String format, final Object arg1)
   {
      if (log.isTraceEnabled())
         log.trace(String.format(format, arg1));
   }

   @Override
   public void trace(final String format, final Object arg1, final Object arg2)
   {
      if (log.isTraceEnabled())
         log.trace(String.format(format, arg1, arg2));
   }

   @Override
   public void trace(final String format, final Object arg1, final Object arg2, final Object arg3)
   {
      if (log.isTraceEnabled())
         log.trace(String.format(format, arg1, arg2, arg3));
   }

   @Override
   public void trace(final String format, final Object... args)
   {
      if (log.isTraceEnabled())
         log.trace(String.format(format, (Object[])args));
   }

   @Override
   public void trace(final Throwable t, final String message)
   {
      if (log.isTraceEnabled())
         log.trace(message, t);
   }

   @Override
   public void trace(final Throwable t, final String format, final Object arg1)
   {
      if (log.isTraceEnabled())
         log.trace(String.format(format, arg1), t);
   }

   @Override
   public void trace(final Throwable t, final String format, final Object arg1, final Object arg2)
   {
      if (log.isTraceEnabled())
         log.trace(String.format(format, arg1, arg2), t);
   }

   @Override
   public void trace(final Throwable t, final String format, final Object arg1, final Object arg2, final Object arg3)
   {
      if (log.isTraceEnabled())
         log.trace(String.format(format, arg1, arg2, arg3), t);
   }

   @Override
   public void trace(final Throwable t, final String format, final Object... args)
   {
      if (log.isTraceEnabled())
         log.trace(String.format(format, (Object[])args), t);
   }
}
