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
package org.jboss.test.osgi.framework.bundle.support.y;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class Activator implements BundleActivator {

    public void start(final BundleContext context) throws Exception {
        new Thread(new Runnable() {

            public void run() {
                try {
                    // Uncomment the following line to make the test pass,
                    // the issue is that the StartLevelControl.testActivatorChangeBundleStartLevel()
                    // test in the TCK lowers the start level of a bundle in the bundle activator
                    // this causes the bundle to get stopped on a different thread, often before
                    // the system is finished activating the bundle.
                    // Thread.sleep(5000);
                    context.getBundle().stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // Wait with exiting the activator so that the thread gets a chance to call stop() before
        // we exit from here.
        Thread.sleep(2000);
    }

    public void stop(BundleContext context) {
    }
}