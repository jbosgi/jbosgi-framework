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
package org.jboss.osgi.framework.spi;

import org.jboss.osgi.framework.spi.LockManager.LockableItem;
import org.jboss.osgi.resolver.XBundle;


/**
 * Lock utils.
 *
 * @author thomas.diesler@jboss.com
 * @since 11-Apr-2013
 */
public final class LockUtils {

    // Hide ctor
    private LockUtils() {
    }

    public static LockableItem[] getLockableItems(XBundle[] bundles, LockableItem[] others) {
        int index = 0;
        int bsize = bundles != null ? bundles.length : 0;
        int osize = others != null ? others.length : 0;
        LockableItem[] items = new LockableItem[bsize + osize];
        if (bundles != null) {
            for (XBundle bundle : bundles) {
                items[index++] = (LockableItem) bundle;
            }
        }
        if (others != null) {
            for (LockableItem other : others) {
                items[index++] = other;
            }
        }
        return items;
    }
}
