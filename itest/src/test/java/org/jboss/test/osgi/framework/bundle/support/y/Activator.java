package org.jboss.test.osgi.framework.bundle.support.y;
/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

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