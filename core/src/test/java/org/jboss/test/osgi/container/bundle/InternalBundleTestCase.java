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
package org.jboss.test.osgi.container.bundle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.Hashtable;

import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.InternalBundle;
import org.jboss.osgi.container.plugin.ResolverPlugin;
import org.jboss.osgi.container.plugin.StartLevelPlugin;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.vfs.VirtualFile;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

/**
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class InternalBundleTestCase extends OSGiFrameworkTest
{
   @Test
   public void testStartStop() throws Exception
   {
      BundleManager bm = mockBundleManager();
      Deployment dep = mockDeployment();
      InternalBundle hb = createInternalBundle(bm, dep);
      hb.changeState(Bundle.INSTALLED);

      assertEquals("Precondition failed", Bundle.INSTALLED, hb.getState());
      assertFalse(hb.isPersistentlyStarted());
      hb.start(0);
      assertTrue(hb.isPersistentlyStarted());
      assertEquals(Bundle.ACTIVE, hb.getState());

      hb.stop(0);
      assertFalse(hb.isPersistentlyStarted());
      assertEquals(Bundle.RESOLVED, hb.getState());
   }

   @Test
   public void testStartStopNoargs() throws Exception
   {
      BundleManager bm = mockBundleManager();
      Deployment dep = mockDeployment();
      InternalBundle hb = createInternalBundle(bm, dep);
      hb.changeState(Bundle.INSTALLED);

      assertEquals("Precondition failed", Bundle.INSTALLED, hb.getState());
      assertFalse(hb.isPersistentlyStarted());
      hb.start();
      assertTrue(hb.isPersistentlyStarted());
      assertEquals(Bundle.ACTIVE, hb.getState());

      hb.stop();
      assertFalse(hb.isPersistentlyStarted());
      assertEquals(Bundle.RESOLVED, hb.getState());
   }

   @Test
   public void testStartAltStartLevel() throws Exception
   {
      BundleManager bm = mockBundleManager();
      Deployment dep = mockDeployment();
      InternalBundle hb = createInternalBundle(bm, dep);
      hb.changeState(Bundle.INSTALLED);
      hb.setStartLevel(15);

      assertEquals("Precondition failed", Bundle.INSTALLED, hb.getState());
      assertFalse(hb.isPersistentlyStarted());
      hb.start();
      assertTrue(hb.isPersistentlyStarted());
      assertEquals(Bundle.INSTALLED, hb.getState());
   }

   @Test
   public void testStartAltStartLevel2() throws Exception
   {
      BundleManager bm = mockBundleManager();
      StartLevelPlugin sl = bm.getOptionalPlugin(StartLevelPlugin.class);
      when(sl.getStartLevel()).thenReturn(20);

      Deployment dep = mockDeployment();
      InternalBundle hb = createInternalBundle(bm, dep);
      hb.changeState(Bundle.INSTALLED);
      hb.setStartLevel(15);

      assertEquals("Precondition failed", Bundle.INSTALLED, hb.getState());
      assertFalse(hb.isPersistentlyStarted());
      hb.start();
      assertTrue(hb.isPersistentlyStarted());
      assertEquals(Bundle.ACTIVE, hb.getState());
   }

   @Test
   public void testNoStartLevelPlugin() throws Exception
   {
      BundleManager bm = mockBundleManager();
      when(bm.getOptionalPlugin(StartLevelPlugin.class)).thenReturn(null);

      Deployment dep = mockDeployment();
      InternalBundle hb = createInternalBundle(bm, dep);
      hb.changeState(Bundle.INSTALLED);
      hb.setStartLevel(15);

      assertEquals("Precondition failed", Bundle.INSTALLED, hb.getState());
      assertFalse(hb.isPersistentlyStarted());
      hb.start();
      assertTrue(hb.isPersistentlyStarted());
      assertEquals(Bundle.ACTIVE, hb.getState());
   }

   @Test
   public void testStartTransient() throws Exception
   {
      BundleManager bm = mockBundleManager();
      Deployment dep = mockDeployment();
      InternalBundle hb = createInternalBundle(bm, dep);
      hb.changeState(Bundle.INSTALLED);

      assertEquals("Precondition failed", Bundle.INSTALLED, hb.getState());
      assertFalse(hb.isPersistentlyStarted());
      hb.start(Bundle.START_TRANSIENT);
      assertFalse(hb.isPersistentlyStarted());
      assertEquals(Bundle.ACTIVE, hb.getState());
   }

   @Test
   public void testStartStopTransient() throws Exception
   {
      BundleManager bm = mockBundleManager();
      Deployment dep = mockDeployment();
      InternalBundle hb = createInternalBundle(bm, dep);
      hb.changeState(Bundle.INSTALLED);

      assertEquals("Precondition failed", Bundle.INSTALLED, hb.getState());
      hb.setPersistentlyStarted(true);
      hb.start(Bundle.START_TRANSIENT);
      assertEquals(Bundle.ACTIVE, hb.getState());

      hb.stop(Bundle.STOP_TRANSIENT);
      assertTrue(hb.isPersistentlyStarted());
      assertEquals(Bundle.RESOLVED, hb.getState());
   }

   private InternalBundle createInternalBundle(BundleManager bm, Deployment dep) throws Exception
   {
      Constructor<InternalBundle> ctor =
            InternalBundle.class.getDeclaredConstructor(BundleManager.class, Deployment.class);
      // Need to do this because this test is not in the same package as InternalBundle
      ctor.setAccessible(true);
      return ctor.newInstance(bm, dep);
   }

   private BundleManager mockBundleManager()
   {
      ResolverPlugin rp = mock(ResolverPlugin.class);
      StartLevelPlugin sl = mock(StartLevelPlugin.class);

      BundleManager bm = mock(BundleManager.class);
      when(bm.getPlugin(ResolverPlugin.class)).thenReturn(rp);
      when(bm.getOptionalPlugin(StartLevelPlugin.class)).thenReturn(sl);
      return bm;
   }

   private Deployment mockDeployment()
   {
      OSGiMetaData md = mock(OSGiMetaData.class);
      when(md.getHeaders()).thenReturn(new Hashtable<String, String>());
      when(md.getBundleSymbolicName()).thenReturn("test_bundle");
      when(md.getBundleVersion()).thenReturn(Version.emptyVersion);

      VirtualFile vf = mock(VirtualFile.class);

      Deployment dep = mock(Deployment.class);
      when(dep.getSymbolicName()).thenReturn("test_bundle");
      when(dep.getLocation()).thenReturn("somewhere");
      when(dep.getAttachment(OSGiMetaData.class)).thenReturn(md);
      when(dep.getRoot()).thenReturn(vf);
      return dep;
   }
}
