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
package org.jboss.test.osgi.framework.performance;

import static org.junit.Assert.assertNotNull;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.jboss.osgi.framework.launch.FrameworkFactoryImpl;
import org.junit.Test;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * A test that profiles framework startup
 * 
 * @author thomas.diesler@jboss.com
 * @since 23-Sep-2010
 */
public class FrameworkStartupTestCase
{
   private static final int RUN_COUNT = 10;

   @Test
   public void testFrameworkStartup() throws Exception
   {
      FrameworkFactory factory = new FrameworkFactoryImpl();

      URL resURL = getClass().getClassLoader().getResource("jboss-osgi-framework.properties");
      assertNotNull("Properties not null", resURL);
      Properties props = new Properties();
      props.load(resURL.openStream());

      Map<String, Object> map = new HashMap<String, Object>();
      for (Entry<Object, Object> entry : props.entrySet())
         map.put((String)entry.getKey(), entry.getValue());

      long start = System.currentTimeMillis();
      for (int i = 1; i <= RUN_COUNT; i++)
      {
         if (i % 10 == 0)
         {
            long now = System.currentTimeMillis();
            System.out.println((now - start) + "ms");
            start = now;
         }
         
         frameworkStartStop(factory, map);
      }
   }

   private void frameworkStartStop(FrameworkFactory factory, Map<String, Object> props) throws Exception
   {
      Framework framework = factory.newFramework(props);
      framework.start();
      framework.stop();
      //framework.waitForStop(2000);
   }
}