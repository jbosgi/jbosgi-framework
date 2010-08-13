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
package org.jboss.test.osgi.container.bundle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.Map;

import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.AbstractRevision;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.HostRevision;
import org.jboss.osgi.container.bundle.ModuleManager;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.spi.AbstractModuleBuilder;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Version;

/**
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class ModuleManagerTestCase
{
   /**
    * Tests functionality in {@link ModuleManager#getModuleFromUnrevisionedIdentifier(ModuleIdentifier)}.
    */
   @Test
   public void testGetUnrevisionedModule() throws Exception
   {
      // Create a subclass to get access to some protected members of ModuleManager, e.g. the
      // ModuleHolder inner Class.
      class MyModuleManager extends ModuleManager
      {
         public MyModuleManager()
         {
            super(mock(BundleManager.class));
         }

         @SuppressWarnings({ "rawtypes", "unchecked" })
         AbstractBundle createModule(int revision) throws Exception
         {
            ModuleIdentifier mi = new ModuleIdentifier("test", "test", "0.0.0-rev" + revision);
            Field mf = ModuleManager.class.getDeclaredField("modules");
            mf.setAccessible(true);
            Map modules = (Map)mf.get(this);

            AbstractBundle bundleState = mock(AbstractBundle.class);
            AbstractRevision bundleRev = mock(AbstractRevision.class);
            Mockito.when(bundleRev.getBundleState()).thenReturn(bundleState);
            ModuleSpec moduleSpec = ModuleSpec.build(mi).create();
            ModuleManager.ModuleHolder holder = new ModuleManager.ModuleHolder(bundleRev, moduleSpec);
            modules.put(mi, holder);
            return bundleState;
         }
      };

      MyModuleManager mm = new MyModuleManager();
      Object ex1 = mm.createModule(0); // Create a module with revision 0
      Object ex2 = mm.createModule(1); // Create a module with revision 1
      Object ex3 = mm.createModule(3); // Create a module with revision 3

      // Obtain a module without specifying the revision, should return revision 3
      assertEquals("Should return the module with the highest revision", ex3, mm.getBundleState(new ModuleIdentifier("test", "test", "0.0.0")));
      assertEquals("Should have returned the object associated with revision 0", ex1, mm.getBundleState(new ModuleIdentifier("test", "test", "0.0.0-rev0")));
      assertEquals("Should have returned the object associated with revision 1", ex2, mm.getBundleState(new ModuleIdentifier("test", "test", "0.0.0-rev1")));
      assertNull("There is no module registered with revision 2", mm.getBundleState(new ModuleIdentifier("test", "test", "0.0.0-rev2")));
   }

   /** 
    * Tests {@link ModuleManager#getModuleIdentifier(XModule)}
    * @throws Exception
    */
   @Test
   public void testGetModuleIdentifier() throws Exception
   {
      // Create a BundleRevision that has revision number 42.
      AbstractRevision br = Mockito.mock(HostRevision.class);
      Mockito.when(br.getUpdateCount()).thenReturn(42);

      // Create a Module and attached the BundleRevision.
      XModule xm = new AbstractModuleBuilder().createModule(123, "myModule", Version.emptyVersion);
      xm.addAttachment(AbstractRevision.class, br);

      // Obtain the module identifier and check that it contains the revision number
      ModuleIdentifier id = ModuleManager.getModuleIdentifier(xm);
      assertEquals("module:jbosgi[123]:myModule:0.0.0-rev42", id.toURL().toString());
      ModuleIdentifier id2 = ModuleManager.getModuleIdentifier(xm);
      assertSame("Should have cached the Module Identifier", id, id2);

      AbstractRevision br2 = Mockito.mock(HostRevision.class);
      Mockito.when(br2.getUpdateCount()).thenReturn(43);

      // Create another module with attached BundleRevision (with rev 43). 
      XModule xm2 = new AbstractModuleBuilder().createModule(123, "myModule", Version.emptyVersion);
      xm2.addAttachment(AbstractRevision.class, br2);
      ModuleIdentifier id3 = ModuleManager.getModuleIdentifier(xm2);
      String a = id.toURL().toString();
      String b = id3.toURL().toString();
      assertTrue("Should be different: " + a + " and " + b, !a.equals(b));
   }
}
