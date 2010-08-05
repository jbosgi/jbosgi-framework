package org.jboss.test.osgi.container.bundle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.Map;

import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.ModuleManager;
import org.junit.Test;


public class ModuleManagerTestCase
{
   @Test
   public void testGetUnrevisionedModule() throws Exception
   {
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

            AbstractBundle b = mock(AbstractBundle.class);
            ModuleSpec ms = new ModuleSpec(null, null, null, null, null, null, null);
            ModuleManager.ModuleHolder holder = new ModuleManager.ModuleHolder(b, ms);
            modules.put(mi, holder);
            return b;
         }
      };

      MyModuleManager mm = new MyModuleManager();
      Object ex1 = mm.createModule(0);
      Object ex2 = mm.createModule(1);
      Object ex3 = mm.createModule(3);
      
      assertEquals("Should return the module with the highest revision",
            ex3, mm.getBundle(new ModuleIdentifier("test", "test", "0.0.0")));

      assertEquals(ex1, mm.getBundle(new ModuleIdentifier("test", "test", "0.0.0-rev0")));
      assertEquals(ex2, mm.getBundle(new ModuleIdentifier("test", "test", "0.0.0-rev1")));
      assertNull(mm.getBundle(new ModuleIdentifier("test", "test", "0.0.0-rev2")));
   }
}
